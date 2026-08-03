/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.db;

import io.xseries.xclip.config.Config;
import io.xseries.xclip.config.ConfigService;
import io.xseries.xclip.data.dao.ClipEntryDao;
import io.xseries.xclip.data.dao.TagDao;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.BackupDescriptor;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.BackupResult;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.CheckpointMode;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.CheckpointResult;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.DatabaseStatus;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.IntegrityReport;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.RestoreResult;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.VacuumResult;
import io.xseries.xclip.domain.duplicate.DuplicateContentKeys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMaintenanceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void missingDatabaseInspectionDoesNotCreateFile() {
        Path dataDir = tempDir.resolve("missing-data");
        Path dbPath = dataDir.resolve("xclip.db");
        DatabaseMaintenanceService service = new DatabaseMaintenanceService(
                dbPath,
                dataDir.resolve("config.json")
        );

        DatabaseStatus status = service.inspect();

        assertFalse(status.databasePresent());
        assertEquals(0L, status.totalBytes());
        assertFalse(Files.exists(dbPath));
    }

    @Test
    void reportsIntegrityCheckpointAndVacuumStatus() throws Exception {
        Path dbPath = tempDir.resolve("xclip.db");
        Path configPath = tempDir.resolve("config.json");
        Database database = new Database(dbPath);
        database.init();

        try (ClipEntryDao clips = new ClipEntryDao(database.jdbcUrl())) {
            for (int index = 0; index < 300; index++) {
                String content = "maintenance-value-" + index + "-" + "x".repeat(200);
                clips.insertNew(
                        content,
                        content,
                        DuplicateContentKeys.from(content),
                        1_000L + index
                );
            }
            for (long id = 1; id <= 220; id++) {
                clips.deleteById(id);
            }
        }
        new ConfigService(configPath).persist(Config.defaults());

        DatabaseMaintenanceService service =
                new DatabaseMaintenanceService(dbPath, configPath);

        DatabaseStatus before = service.inspect();
        assertTrue(before.databasePresent());
        assertEquals(Database.CURRENT_SCHEMA_VERSION, before.schemaVersion());
        assertEquals("wal", before.journalMode().toLowerCase());
        assertTrue(before.databaseBytes() > 0L);

        IntegrityReport integrity = service.integrityCheck();
        assertTrue(integrity.ok(), integrity.summary());

        CheckpointResult checkpoint = service.checkpoint(CheckpointMode.TRUNCATE);
        assertTrue(checkpoint.complete());

        VacuumResult vacuum = service.vacuum();
        assertTrue(vacuum.bytesBefore() > 0L);
        assertTrue(vacuum.bytesAfter() > 0L);
        assertTrue(vacuum.bytesAfter() <= vacuum.bytesBefore());

        DatabaseStatus after = service.inspect();
        assertEquals(Database.CURRENT_SCHEMA_VERSION, after.schemaVersion());
        assertTrue(service.integrityCheck().ok());
    }

    @Test
    void backupRestoreRoundTripPreservesHistoryTagsAndConfiguration()
            throws Exception {
        Path dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);
        Path dbPath = dataDir.resolve("xclip.db");
        Path configPath = dataDir.resolve("config.json");
        Path backupPath = tempDir.resolve("portable-backup");

        Database database = new Database(dbPath);
        database.init();
        ConfigService configService = new ConfigService(configPath);
        configService.persist(Config.defaults().withMaxHistory(1_250));

        try (ClipEntryDao clips = new ClipEntryDao(database.jdbcUrl());
             TagDao tags = new TagDao(database.jdbcUrl())) {
            String content = "original backup value";
            clips.insertNew(
                    content,
                    content,
                    DuplicateContentKeys.from(content),
                    5_000L
            );
            long tagId = tags.createOrGet("Backup").id();
            tags.addTagToClip(1L, tagId);
        }

        DatabaseMaintenanceService service =
                new DatabaseMaintenanceService(dbPath, configPath);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createBackup(
                        dataDir.resolve("unsafe-backup.xclip-backup"),
                        "1.3.0"
                )
        );
        BackupResult backup = service.createBackup(backupPath, "1.3.0");

        assertTrue(Files.isRegularFile(backup.path()));
        assertTrue(backup.path().getFileName().toString().endsWith(
                DatabaseMaintenanceService.BACKUP_EXTENSION
        ));
        assertEquals(Database.CURRENT_SCHEMA_VERSION, backup.databaseSchemaVersion());
        assertEquals(Config.CURRENT_VERSION, backup.configSchemaVersion());

        BackupDescriptor descriptor = service.inspectBackup(backup.path());
        assertEquals(Database.CURRENT_SCHEMA_VERSION, descriptor.databaseSchemaVersion());
        assertEquals(Config.CURRENT_VERSION, descriptor.configSchemaVersion());
        assertEquals("1.3.0", descriptor.productVersion());

        try (ZipFile zip = new ZipFile(backup.path().toFile())) {
            Set<String> entries = zip.stream()
                    .map(ZipEntry::getName)
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(
                    Set.of("manifest.properties", "xclip.db", "config.json"),
                    entries
            );
        }

        try (ClipEntryDao clips = new ClipEntryDao(database.jdbcUrl())) {
            clips.deleteAllNonFavorites();
            String changed = "changed after backup";
            clips.insertNew(
                    changed,
                    changed,
                    DuplicateContentKeys.from(changed),
                    9_000L
            );
        }
        configService.persist(Config.defaults().withMaxHistory(9_999));

        RestoreResult restored = service.restoreBackup(backup.path());
        assertEquals(Database.CURRENT_SCHEMA_VERSION, restored.databaseSchemaVersion());
        assertEquals(Config.CURRENT_VERSION, restored.configSchemaVersion());

        try (Connection connection = DriverManager.getConnection(database.jdbcUrl());
             Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("""
                    SELECT content FROM clip_entries ORDER BY id
                    """)) {
                assertTrue(result.next());
                assertEquals("original backup value", result.getString(1));
                assertFalse(result.next());
            }
            try (ResultSet result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM clip_tags
                    """)) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }

        Config restoredConfig = configService.loadOrCreate();
        assertEquals(1_250, restoredConfig.maxHistory());
        assertTrue(service.integrityCheck().ok());
    }

    @Test
    void manifestSchemaMismatchIsRejected() throws Exception {
        Path dataDir = tempDir.resolve("manifest-data");
        Files.createDirectories(dataDir);
        Path dbPath = dataDir.resolve("xclip.db");
        Path configPath = dataDir.resolve("config.json");
        Path validBackup = tempDir.resolve("valid.xclip-backup");
        Path mismatchBackup = tempDir.resolve("mismatch.xclip-backup");

        Database database = new Database(dbPath);
        database.init();
        new ConfigService(configPath).persist(Config.defaults());

        DatabaseMaintenanceService service =
                new DatabaseMaintenanceService(dbPath, configPath);
        service.createBackup(validBackup, "1.3.0");

        try (ZipFile source = new ZipFile(validBackup.toFile());
             OutputStream output = Files.newOutputStream(mismatchBackup);
             ZipOutputStream target = new ZipOutputStream(
                     output,
                     StandardCharsets.UTF_8
             )) {
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                target.putNextEntry(new ZipEntry(entry.getName()));
                if ("manifest.properties".equals(entry.getName())) {
                    Properties manifest = new Properties();
                    try (var input = source.getInputStream(entry)) {
                        manifest.load(input);
                    }
                    manifest.setProperty("databaseSchemaVersion", "5");
                    manifest.store(target, "mismatch");
                } else {
                    try (var input = source.getInputStream(entry)) {
                        input.transferTo(target);
                    }
                }
                target.closeEntry();
            }
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> service.inspectBackup(mismatchBackup)
        );
    }

    @Test
    void failedRestoreInstallRollsBackLiveDatabaseAndConfiguration()
            throws Exception {
        Path dataDir = tempDir.resolve("rollback-data");
        Files.createDirectories(dataDir);
        Path dbPath = dataDir.resolve("xclip.db");
        Path configPath = dataDir.resolve("config.json");
        Path backupPath = tempDir.resolve("rollback-source.xclip-backup");

        Database database = new Database(dbPath);
        database.init();
        ConfigService configService = new ConfigService(configPath);
        configService.persist(Config.defaults().withMaxHistory(1_111));

        try (ClipEntryDao clips = new ClipEntryDao(database.jdbcUrl())) {
            String original = "backup-side value";
            clips.insertNew(
                    original,
                    original,
                    DuplicateContentKeys.from(original),
                    1_000L
            );
        }

        DatabaseMaintenanceService normal =
                new DatabaseMaintenanceService(dbPath, configPath);
        normal.createBackup(backupPath, "1.3.0");

        try (ClipEntryDao clips = new ClipEntryDao(database.jdbcUrl())) {
            clips.deleteAllNonFavorites();
            String live = "live value preserved by rollback";
            clips.insertNew(
                    live,
                    live,
                    DuplicateContentKeys.from(live),
                    2_000L
            );
        }
        configService.persist(Config.defaults().withMaxHistory(2_222));

        DatabaseMaintenanceService failing =
                new DatabaseMaintenanceService(
                        dbPath,
                        configPath,
                        () -> {
                            throw new java.io.IOException(
                                    "injected restore installation failure"
                            );
                        }
                );

        assertThrows(
                RuntimeException.class,
                () -> failing.restoreBackup(backupPath)
        );

        try (Connection connection = DriverManager.getConnection(database.jdbcUrl());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT content FROM clip_entries ORDER BY id"
             )) {
            assertTrue(result.next());
            assertEquals(
                    "live value preserved by rollback",
                    result.getString(1)
            );
            assertFalse(result.next());
        }

        Config liveConfig = configService.loadOrCreate();
        assertEquals(2_222, liveConfig.maxHistory());
        assertTrue(normal.integrityCheck().ok());
    }

    @Test
    void invalidArchiveIsRejectedWithoutReplacingLiveData() throws Exception {
        Path dbPath = tempDir.resolve("xclip.db");
        Path configPath = tempDir.resolve("config.json");
        Path invalidBackup = tempDir.resolve("invalid.xclip-backup");

        Database database = new Database(dbPath);
        database.init();
        new ConfigService(configPath).persist(Config.defaults());

        try (ClipEntryDao clips = new ClipEntryDao(database.jdbcUrl())) {
            String content = "live data";
            clips.insertNew(
                    content,
                    content,
                    DuplicateContentKeys.from(content),
                    1_000L
            );
        }

        Properties manifest = new Properties();
        manifest.setProperty("formatVersion", "1");
        manifest.setProperty("createdAtEpochMillis", "1");
        manifest.setProperty("productVersion", "1.3.0");
        manifest.setProperty("databaseSchemaVersion", "6");
        manifest.setProperty("configSchemaVersion", "5");
        manifest.setProperty("databaseEntry", "xclip.db");
        manifest.setProperty("configEntry", "config.json");

        try (OutputStream output = Files.newOutputStream(invalidBackup);
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("manifest.properties"));
            manifest.store(zip, "invalid");
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("xclip.db"));
            zip.write("not a sqlite database".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("config.json"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        DatabaseMaintenanceService service =
                new DatabaseMaintenanceService(dbPath, configPath);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.restoreBackup(invalidBackup)
        );

        try (Connection connection = DriverManager.getConnection(database.jdbcUrl());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT content FROM clip_entries"
             )) {
            assertTrue(result.next());
            assertEquals("live data", result.getString(1));
        }
    }
}
