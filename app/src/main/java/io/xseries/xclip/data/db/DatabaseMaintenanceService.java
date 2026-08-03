/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.db;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.xseries.xclip.config.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Exclusive and diagnostic maintenance operations for XClip's SQLite database.
 *
 * This class owns no long-lived JDBC connection. Callers pause write-producing
 * services and release DAO connections before checkpoint, vacuum, backup, or
 * restore operations. Read-only status and integrity checks can run directly.
 */
public final class DatabaseMaintenanceService {

    public static final int BACKUP_FORMAT_VERSION = 1;
    public static final String BACKUP_EXTENSION = ".xclip-backup";

    private static final String MANIFEST_ENTRY = "manifest.properties";
    private static final String DATABASE_ENTRY = "xclip.db";
    private static final String CONFIG_ENTRY = "config.json";
    private static final Set<String> REQUIRED_BACKUP_ENTRIES =
            Set.of(MANIFEST_ENTRY, DATABASE_ENTRY, CONFIG_ENTRY);

    private static final long MAX_MANIFEST_BYTES = 64L * 1024L;
    private static final long MAX_CONFIG_BYTES = 10L * 1024L * 1024L;
    private static final long MAX_DATABASE_BYTES = 64L * 1024L * 1024L * 1024L;

    private final Path databasePath;
    private final Path configPath;
    private final Path dataDirectory;
    private final Gson gson;
    private final RestoreInstallHook restoreInstallHook;

    public DatabaseMaintenanceService(
            Path databasePath,
            Path configPath
    ) {
        this(databasePath, configPath, () -> {});
    }

    DatabaseMaintenanceService(
            Path databasePath,
            Path configPath,
            RestoreInstallHook restoreInstallHook
    ) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
        this.configPath = Objects.requireNonNull(configPath, "configPath")
                .toAbsolutePath()
                .normalize();
        Path parent = this.databasePath.getParent();
        this.dataDirectory = parent == null
                ? Path.of(".").toAbsolutePath().normalize()
                : parent;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.restoreInstallHook = Objects.requireNonNull(
                restoreInstallHook,
                "restoreInstallHook"
        );
    }

    public DatabaseStatus inspect() {
        if (!Files.isRegularFile(databasePath)) {
            return new DatabaseStatus(
                    false,
                    0L,
                    0L,
                    0L,
                    0,
                    "missing",
                    0L,
                    0L,
                    0L
            );
        }

        try (Connection connection = open(databasePath);
             Statement statement = connection.createStatement()) {
            int schemaVersion = intPragma(statement, "user_version");
            String journalMode = stringPragma(statement, "journal_mode");
            long pageCount = longPragma(statement, "page_count");
            long freePages = longPragma(statement, "freelist_count");
            long pageSize = longPragma(statement, "page_size");

            return new DatabaseStatus(
                    true,
                    safeSize(databasePath),
                    safeSize(sidecar("-wal")),
                    safeSize(sidecar("-shm")),
                    schemaVersion,
                    journalMode,
                    pageCount,
                    freePages,
                    multiplySaturated(freePages, pageSize)
            );
        } catch (Exception error) {
            throw new RuntimeException("Failed to inspect XClip database", error);
        }
    }

    public IntegrityReport integrityCheck() {
        return integrityCheck(databasePath);
    }

    public CheckpointResult checkpoint(CheckpointMode mode) {
        CheckpointMode effectiveMode = Objects.requireNonNull(mode, "mode");
        requireDatabase();

        String sql = "PRAGMA wal_checkpoint("
                + effectiveMode.name().toUpperCase(Locale.ROOT)
                + ");";
        try (Connection connection = open(databasePath);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) {
                throw new IllegalStateException("SQLite returned no checkpoint result");
            }
            return new CheckpointResult(
                    effectiveMode,
                    result.getInt(1),
                    result.getInt(2),
                    result.getInt(3)
            );
        } catch (Exception error) {
            throw new RuntimeException("Failed to checkpoint SQLite WAL", error);
        }
    }

    public VacuumResult vacuum() {
        requireDatabase();

        long before = totalDatabaseBytes();
        CheckpointResult checkpoint = checkpoint(CheckpointMode.TRUNCATE);
        if (!checkpoint.complete()) {
            throw new IllegalStateException(
                    "SQLite WAL checkpoint is busy; close other XClip processes and retry"
            );
        }

        try (Connection connection = open(databasePath);
             Statement statement = connection.createStatement()) {
            statement.execute("VACUUM;");
            statement.execute("PRAGMA optimize;");
        } catch (Exception error) {
            throw new RuntimeException("Failed to optimize XClip database", error);
        }

        return new VacuumResult(before, totalDatabaseBytes());
    }

    public BackupResult createBackup(
            Path requestedDestination,
            String productVersion
    ) {
        requireDatabase();
        Path destination = normalizedBackupDestination(requestedDestination);
        Path parent = destination.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Backup destination must have a parent directory");
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException error) {
            throw new RuntimeException("Failed to create backup destination directory", error);
        }

        Path temporaryDirectory = null;
        Path temporaryArchive = null;
        try {
            temporaryDirectory = Files.createTempDirectory("xclip-backup-");
            Path snapshotDatabase = temporaryDirectory.resolve(DATABASE_ENTRY);
            Path snapshotConfig = temporaryDirectory.resolve(CONFIG_ENTRY);

            CheckpointResult checkpoint = checkpoint(CheckpointMode.TRUNCATE);
            if (!checkpoint.complete()) {
                throw new IllegalStateException(
                        "SQLite WAL checkpoint is busy; close other XClip processes and retry"
                );
            }

            vacuumInto(databasePath, snapshotDatabase);
            IntegrityReport integrity = integrityCheck(snapshotDatabase);
            if (!integrity.ok()) {
                throw new IllegalStateException(
                        "Backup snapshot failed integrity_check: " + integrity.summary()
                );
            }

            Config config = readConfigOrDefaults(configPath);
            writeConfig(snapshotConfig, config);

            int databaseSchema = inspectSchemaVersion(snapshotDatabase);
            Properties manifest = new Properties();
            manifest.setProperty("formatVersion", String.valueOf(BACKUP_FORMAT_VERSION));
            manifest.setProperty("createdAtEpochMillis", String.valueOf(Instant.now().toEpochMilli()));
            manifest.setProperty(
                    "productVersion",
                    Objects.requireNonNullElse(productVersion, "unknown")
            );
            manifest.setProperty("databaseSchemaVersion", String.valueOf(databaseSchema));
            manifest.setProperty("configSchemaVersion", String.valueOf(config.version()));
            manifest.setProperty("databaseEntry", DATABASE_ENTRY);
            manifest.setProperty("configEntry", CONFIG_ENTRY);

            temporaryArchive = Files.createTempFile(parent, ".xclip-backup-", ".tmp");
            writeArchive(
                    temporaryArchive,
                    manifest,
                    snapshotDatabase,
                    snapshotConfig
            );
            moveReplacing(temporaryArchive, destination);
            temporaryArchive = null;

            return new BackupResult(
                    destination,
                    safeSize(destination),
                    databaseSchema,
                    config.version()
            );
        } catch (Exception error) {
            if (error instanceof RuntimeException runtime) throw runtime;
            throw new RuntimeException("Failed to create XClip backup", error);
        } finally {
            deleteQuietly(temporaryArchive);
            deleteTreeQuietly(temporaryDirectory);
        }
    }

    public BackupDescriptor inspectBackup(Path source) {
        try (PreparedBackup prepared = prepareBackup(source)) {
            return prepared.descriptor();
        }
    }

    public RestoreResult restoreBackup(Path source) {
        try (PreparedBackup prepared = prepareBackup(source)) {
            CheckpointResult checkpoint = checkpoint(CheckpointMode.TRUNCATE);
            if (!checkpoint.complete()) {
                throw new IllegalStateException(
                        "SQLite WAL checkpoint is busy; close other XClip processes and retry"
                );
            }
            installPreparedBackup(prepared);
            return new RestoreResult(
                    prepared.source(),
                    prepared.descriptor().databaseSchemaVersion(),
                    prepared.descriptor().configSchemaVersion()
            );
        }
    }

    private PreparedBackup prepareBackup(Path source) {
        Path backup = Objects.requireNonNull(source, "source")
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(backup)) {
            throw new IllegalArgumentException("Backup file does not exist: " + backup);
        }

        Path temporaryDirectory = null;
        try {
            temporaryDirectory = Files.createTempDirectory("xclip-restore-");
            Path manifestPath = temporaryDirectory.resolve(MANIFEST_ENTRY);
            Path extractedDatabase = temporaryDirectory.resolve(DATABASE_ENTRY);
            Path extractedConfig = temporaryDirectory.resolve(CONFIG_ENTRY);

            extractValidatedArchive(
                    backup,
                    manifestPath,
                    extractedDatabase,
                    extractedConfig
            );

            Properties manifest = loadManifest(manifestPath);
            int formatVersion = requiredInt(manifest, "formatVersion");
            if (formatVersion != BACKUP_FORMAT_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported XClip backup format: " + formatVersion
                );
            }
            if (!DATABASE_ENTRY.equals(manifest.getProperty("databaseEntry"))
                    || !CONFIG_ENTRY.equals(manifest.getProperty("configEntry"))) {
                throw new IllegalArgumentException("Backup manifest entry names are invalid");
            }

            int actualDatabaseSchema = inspectSchemaVersion(extractedDatabase);
            int manifestDatabaseSchema = requiredInt(
                    manifest,
                    "databaseSchemaVersion"
            );
            if (actualDatabaseSchema != manifestDatabaseSchema) {
                throw new IllegalArgumentException(
                        "Backup database schema does not match its manifest"
                );
            }
            if (actualDatabaseSchema <= 0
                    || actualDatabaseSchema > Database.CURRENT_SCHEMA_VERSION) {
                throw new IllegalArgumentException(
                        "Backup database schema " + actualDatabaseSchema
                                + " is not supported by this XClip build"
                );
            }

            IntegrityReport integrity = integrityCheck(extractedDatabase);
            if (!integrity.ok()) {
                throw new IllegalArgumentException(
                        "Backup database integrity_check failed: " + integrity.summary()
                );
            }

            Config rawConfig = readConfig(extractedConfig);
            int manifestConfigSchema = requiredInt(manifest, "configSchemaVersion");
            if (rawConfig.version() != manifestConfigSchema) {
                throw new IllegalArgumentException(
                        "Backup configuration schema does not match its manifest"
                );
            }
            if (rawConfig.version() <= 0
                    || rawConfig.version() > Config.CURRENT_VERSION) {
                throw new IllegalArgumentException(
                        "Backup configuration schema " + rawConfig.version()
                                + " is not supported by this XClip build"
                );
            }
            Config config = rawConfig.normalized();
            writeConfig(extractedConfig, config);

            long createdAt = requiredLong(manifest, "createdAtEpochMillis");
            if (createdAt < 0L) {
                throw new IllegalArgumentException(
                        "Backup creation timestamp cannot be negative"
                );
            }
            BackupDescriptor descriptor = new BackupDescriptor(
                    backup,
                    formatVersion,
                    createdAt,
                    Objects.requireNonNullElse(
                            manifest.getProperty("productVersion"),
                            "unknown"
                    ),
                    actualDatabaseSchema,
                    manifestConfigSchema,
                    safeSize(backup)
            );

            PreparedBackup prepared = new PreparedBackup(
                    backup,
                    temporaryDirectory,
                    extractedDatabase,
                    extractedConfig,
                    descriptor
            );
            temporaryDirectory = null;
            return prepared;
        } catch (Exception error) {
            deleteTreeQuietly(temporaryDirectory);
            if (error instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException(
                    "Invalid XClip backup: " + safeMessage(error),
                    error
            );
        }
    }

    private void installPreparedBackup(PreparedBackup prepared) {
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException error) {
            throw new RuntimeException("Failed to create XClip data directory", error);
        }

        String token = UUID.randomUUID().toString();
        Path stagedDatabase = dataDirectory.resolve(".xclip-restore-" + token + ".db");
        Path stagedConfig = dataDirectory.resolve(".xclip-restore-" + token + ".json");
        Path rollbackDatabase = dataDirectory.resolve(".xclip-rollback-" + token + ".db");
        Path rollbackConfig = dataDirectory.resolve(".xclip-rollback-" + token + ".json");

        boolean databaseMoved = false;
        boolean configMoved = false;
        boolean databaseInstalled = false;
        boolean configInstalled = false;
        try {
            Files.copy(
                    prepared.database(),
                    stagedDatabase,
                    StandardCopyOption.REPLACE_EXISTING
            );
            Files.copy(
                    prepared.config(),
                    stagedConfig,
                    StandardCopyOption.REPLACE_EXISTING
            );

            deleteDatabaseSidecarsStrict();

            if (Files.exists(databasePath)) {
                moveReplacing(databasePath, rollbackDatabase);
                databaseMoved = true;
            }
            if (Files.exists(configPath)) {
                moveReplacing(configPath, rollbackConfig);
                configMoved = true;
            }

            restoreInstallHook.afterOriginalsMoved();

            moveReplacing(stagedDatabase, databasePath);
            databaseInstalled = true;
            moveReplacing(stagedConfig, configPath);
            configInstalled = true;

            IntegrityReport installedIntegrity = integrityCheck(databasePath);
            if (!installedIntegrity.ok()) {
                throw new IllegalStateException(
                        "Installed backup failed integrity_check: "
                                + installedIntegrity.summary()
                );
            }
            int installedSchema = inspectSchemaVersion(databasePath);
            if (installedSchema != prepared.descriptor().databaseSchemaVersion()) {
                throw new IllegalStateException(
                        "Installed backup schema changed during restore"
                );
            }

            deleteQuietly(rollbackDatabase);
            deleteQuietly(rollbackConfig);
        } catch (Exception failure) {
            if (databaseInstalled) deleteQuietly(databasePath);
            if (configInstalled) deleteQuietly(configPath);

            try {
                if (databaseMoved && Files.exists(rollbackDatabase)) {
                    moveReplacing(rollbackDatabase, databasePath);
                }
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            try {
                if (configMoved && Files.exists(rollbackConfig)) {
                    moveReplacing(rollbackConfig, configPath);
                }
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }

            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new RuntimeException("Failed to restore XClip backup", failure);
        } finally {
            deleteQuietly(stagedDatabase);
            deleteQuietly(stagedConfig);
            // Rollback files are deleted on success. If rollback itself fails,
            // they are intentionally preserved for manual recovery.
        }
    }

    private void writeArchive(
            Path archive,
            Properties manifest,
            Path snapshotDatabase,
            Path snapshotConfig
    ) throws IOException {
        try (OutputStream fileOutput = Files.newOutputStream(archive);
             ZipOutputStream zip = new ZipOutputStream(fileOutput, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
            manifest.store(zip, "XClip backup manifest");
            zip.closeEntry();

            addFile(zip, DATABASE_ENTRY, snapshotDatabase);
            addFile(zip, CONFIG_ENTRY, snapshotConfig);
        }
    }

    private void addFile(
            ZipOutputStream zip,
            String entryName,
            Path file
    ) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private void extractValidatedArchive(
            Path archive,
            Path manifest,
            Path database,
            Path config
    ) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            Set<String> names = new HashSet<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()
                        || name.contains("/")
                        || name.contains("\\")
                        || !names.add(name)) {
                    throw new IllegalArgumentException(
                            "Backup contains an unsafe or duplicate entry: " + name
                    );
                }
            }
            if (!names.equals(REQUIRED_BACKUP_ENTRIES)) {
                throw new IllegalArgumentException(
                        "Backup entries are incomplete or unexpected: " + names
                );
            }

            copyBounded(zip, MANIFEST_ENTRY, manifest, MAX_MANIFEST_BYTES);
            copyBounded(zip, DATABASE_ENTRY, database, MAX_DATABASE_BYTES);
            copyBounded(zip, CONFIG_ENTRY, config, MAX_CONFIG_BYTES);
        }
    }

    private void copyBounded(
            ZipFile zip,
            String entryName,
            Path destination,
            long maxBytes
    ) throws IOException {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) {
            throw new IllegalArgumentException("Missing backup entry: " + entryName);
        }
        long declaredSize = entry.getSize();
        if (declaredSize > maxBytes) {
            throw new IllegalArgumentException(
                    "Backup entry is too large: " + entryName
            );
        }

        try (InputStream input = zip.getInputStream(entry);
             OutputStream output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            long copied = 0L;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                copied += read;
                if (copied > maxBytes) {
                    throw new IllegalArgumentException(
                            "Backup entry exceeds the allowed size: " + entryName
                    );
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private Properties loadManifest(Path path) throws IOException {
        Properties manifest = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            manifest.load(input);
        }
        return manifest;
    }

    private Config readConfigOrDefaults(Path path) {
        if (!Files.isRegularFile(path)) return Config.defaults();
        return readConfig(path).normalized();
    }

    private Config readConfig(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Config config = gson.fromJson(reader, Config.class);
            if (config == null) {
                throw new IllegalArgumentException("Configuration parsed to null");
            }
            return config;
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    "Backup configuration is invalid",
                    error
            );
        }
    }

    private void writeConfig(Path path, Config config) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(Objects.requireNonNull(config, "config"), writer);
        }
    }

    private IntegrityReport integrityCheck(Path path) {
        if (!Files.isRegularFile(path)) {
            return new IntegrityReport(false, List.of("database file is missing"));
        }

        List<String> messages = new ArrayList<>();
        try (Connection connection = open(path);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA integrity_check;")) {
            while (result.next()) {
                String message = result.getString(1);
                if (message != null && !message.isBlank()) {
                    messages.add(message.trim());
                }
                if (messages.size() >= 100) break;
            }
        } catch (Exception error) {
            return new IntegrityReport(
                    false,
                    List.of("integrity_check failed: " + safeMessage(error))
            );
        }

        boolean ok = messages.size() == 1
                && "ok".equalsIgnoreCase(messages.get(0));
        if (messages.isEmpty()) messages.add("integrity_check returned no result");
        return new IntegrityReport(ok, List.copyOf(messages));
    }

    private int inspectSchemaVersion(Path path) {
        try (Connection connection = open(path);
             Statement statement = connection.createStatement()) {
            return intPragma(statement, "user_version");
        } catch (Exception error) {
            throw new RuntimeException("Failed to read SQLite schema version", error);
        }
    }

    private void vacuumInto(Path source, Path destination) {
        deleteQuietly(destination);
        String escaped = destination.toAbsolutePath()
                .normalize()
                .toString()
                .replace("'", "''");
        try (Connection connection = open(source);
             Statement statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + escaped + "';");
        } catch (Exception error) {
            throw new RuntimeException("Failed to create consistent SQLite snapshot", error);
        }
    }

    private Connection open(Path path) throws Exception {
        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + path.toAbsolutePath()
        );
        try {
            SqliteConnectionConfig.configureWorkingConnection(connection);
            return connection;
        } catch (Exception error) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
            throw error;
        }
    }

    private int intPragma(Statement statement, String name) throws Exception {
        return Math.toIntExact(longPragma(statement, name));
    }

    private long longPragma(Statement statement, String name) throws Exception {
        try (ResultSet result = statement.executeQuery("PRAGMA " + name + ";")) {
            if (!result.next()) {
                throw new IllegalStateException("PRAGMA " + name + " returned no result");
            }
            return result.getLong(1);
        }
    }

    private String stringPragma(Statement statement, String name) throws Exception {
        try (ResultSet result = statement.executeQuery("PRAGMA " + name + ";")) {
            if (!result.next()) {
                throw new IllegalStateException("PRAGMA " + name + " returned no result");
            }
            return Objects.requireNonNullElse(result.getString(1), "unknown");
        }
    }

    private int requiredInt(Properties properties, String key) {
        long value = requiredLong(properties, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Backup manifest value is out of range: " + key);
        }
        return (int) value;
    }

    private long requiredLong(Properties properties, String key) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing backup manifest value: " + key);
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    "Invalid backup manifest value: " + key,
                    error
            );
        }
    }

    private Path normalizedBackupDestination(Path requested) {
        Path destination = Objects.requireNonNull(requested, "requestedDestination")
                .toAbsolutePath()
                .normalize();
        Path fileName = destination.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException(
                    "Backup destination must be a file path"
            );
        }
        if (!fileName.toString()
                .toLowerCase(Locale.ROOT)
                .endsWith(BACKUP_EXTENSION)) {
            destination = destination.resolveSibling(
                    fileName + BACKUP_EXTENSION
            );
        }
        if (destination.equals(databasePath)
                || destination.equals(configPath)
                || destination.startsWith(dataDirectory)) {
            throw new IllegalArgumentException(
                    "Backup destination must be outside the live XClip data directory"
            );
        }
        return destination;
    }

    private void requireDatabase() {
        if (!Files.isRegularFile(databasePath)) {
            throw new IllegalStateException(
                    "XClip database does not exist: " + databasePath
            );
        }
    }

    private long totalDatabaseBytes() {
        return safeSize(databasePath)
                + safeSize(sidecar("-wal"))
                + safeSize(sidecar("-shm"));
    }

    private Path sidecar(String suffix) {
        return Path.of(databasePath.toString() + suffix);
    }

    private void deleteDatabaseSidecarsStrict() {
        RuntimeException failure = null;
        for (Path sidecar : List.of(
                sidecar("-wal"),
                sidecar("-shm"),
                sidecar("-journal")
        )) {
            try {
                Files.deleteIfExists(sidecar);
            } catch (Exception error) {
                RuntimeException next = new RuntimeException(
                        "Failed to remove SQLite sidecar before restore: " + sidecar,
                        error
                );
                if (failure == null) failure = next;
                else failure.addSuppressed(next);
            }
        }
        if (failure != null) throw failure;
    }

    private long safeSize(Path path) {
        if (path == null) return 0L;
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private long multiplySaturated(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank()
                ? error == null ? "unknown error" : error.getClass().getSimpleName()
                : message;
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    private void deleteTreeQuietly(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(this::deleteQuietly);
        } catch (Exception ignored) {
        }
    }

    @FunctionalInterface
    interface RestoreInstallHook {
        void afterOriginalsMoved() throws Exception;
    }

    public enum CheckpointMode {
        PASSIVE,
        FULL,
        RESTART,
        TRUNCATE
    }

    public record DatabaseStatus(
            boolean databasePresent,
            long databaseBytes,
            long walBytes,
            long sharedMemoryBytes,
            int schemaVersion,
            String journalMode,
            long pageCount,
            long freePageCount,
            long estimatedReclaimableBytes
    ) {
        public long totalBytes() {
            return databaseBytes + walBytes + sharedMemoryBytes;
        }
    }

    public record IntegrityReport(
            boolean ok,
            List<String> messages
    ) {
        public IntegrityReport {
            messages = List.copyOf(Objects.requireNonNullElse(messages, List.of()));
        }

        public String summary() {
            return messages.isEmpty() ? "no result" : String.join("; ", messages);
        }
    }

    public record CheckpointResult(
            CheckpointMode mode,
            int busyConnections,
            int logFrames,
            int checkpointedFrames
    ) {
        public boolean complete() {
            return busyConnections == 0;
        }
    }

    public record VacuumResult(
            long bytesBefore,
            long bytesAfter
    ) {
        public long reclaimedBytes() {
            return Math.max(0L, bytesBefore - bytesAfter);
        }
    }

    public record BackupResult(
            Path path,
            long archiveBytes,
            int databaseSchemaVersion,
            int configSchemaVersion
    ) {}

    public record BackupDescriptor(
            Path path,
            int formatVersion,
            long createdAtEpochMillis,
            String productVersion,
            int databaseSchemaVersion,
            int configSchemaVersion,
            long archiveBytes
    ) {}

    public record RestoreResult(
            Path source,
            int databaseSchemaVersion,
            int configSchemaVersion
    ) {}

    private static final class PreparedBackup implements AutoCloseable {
        private final Path source;
        private final Path temporaryDirectory;
        private final Path database;
        private final Path config;
        private final BackupDescriptor descriptor;

        private PreparedBackup(
                Path source,
                Path temporaryDirectory,
                Path database,
                Path config,
                BackupDescriptor descriptor
        ) {
            this.source = source;
            this.temporaryDirectory = temporaryDirectory;
            this.database = database;
            this.config = config;
            this.descriptor = descriptor;
        }

        private Path source() {
            return source;
        }

        private Path database() {
            return database;
        }

        private Path config() {
            return config;
        }

        private BackupDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public void close() {
            if (temporaryDirectory == null || !Files.exists(temporaryDirectory)) return;
            try (var stream = Files.walk(temporaryDirectory)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                            }
                        });
            } catch (Exception ignored) {
            }
        }
    }
}
