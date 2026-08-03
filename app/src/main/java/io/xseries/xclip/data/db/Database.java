/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.db;

import io.xseries.xclip.domain.duplicate.DuplicateContentKeys;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class Database {

    private static final int CURRENT_SCHEMA_VERSION = 6;

    private final Path dbPath;
    private final String jdbcUrl;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public Database(Path dbPath) {
        this.dbPath = dbPath;
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }

    public Path dbPath() {
        return dbPath;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Initializes database directory, enables WAL,
     * applies the latest base schema, and migrates existing databases.
     */
    public void init() {
        if (!initialized.compareAndSet(false, true)) {
            return; // already initialized
        }

        if (closed.get()) {
            throw new IllegalStateException("Database is closed and cannot be initialized again.");
        }

        try {
            Files.createDirectories(dbPath.getParent());
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create database directory: " + dbPath.getParent(), e
            );
        }

        // Ensure SQLite driver is loaded
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // modern JDBC auto-loads; ignore
        }

        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            SqliteConnectionConfig.configureDatabase(c);

            try (Statement st = c.createStatement()) {
                applyBaseSchema(st);
            }
            migrateToLatest(c);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SQLite database", e);
        }
    }

    private void applyBaseSchema(Statement st) throws SQLException {
        String ddl = loadResourceText("/db/schema.sql");
        for (String sql : ddl.split(";")) {
            String s = sql.trim();
            if (!s.isEmpty()) {
                st.execute(s + ";");
            }
        }
    }

    private void migrateToLatest(Connection c) throws SQLException {
        int existingVersion = readUserVersion(c);
        if (existingVersion > CURRENT_SCHEMA_VERSION) {
            throw new SQLException(
                    "Database schema version " + existingVersion
                            + " is newer than supported version " + CURRENT_SCHEMA_VERSION
            );
        }

        boolean previousAutoCommit = c.getAutoCommit();
        c.setAutoCommit(false);

        try {
            ensureColumn(c, "last_copied_at", "INTEGER NOT NULL DEFAULT 0");
            ensureColumn(c, "use_count", "INTEGER NOT NULL DEFAULT 1");
            ensureColumn(c, "title", "TEXT");
            ensureColumn(c, "pin_order", "INTEGER");
            ensureColumn(c, "content_exact_hash", "TEXT");
            ensureColumn(c, "content_exact_ci_hash", "TEXT");
            ensureColumn(c, "content_norm_ci_hash", "TEXT");

            try (Statement st = c.createStatement()) {
                // Existing v1 rows did not have last_copied_at.
                st.executeUpdate("""
                        UPDATE clip_entries
                        SET last_copied_at = created_at
                        WHERE last_copied_at IS NULL OR last_copied_at <= 0
                        """);

                if (existingVersion < 6) {
                    // Versions through v5 enforced one row per normalized hash.
                    // Keep the best legacy row before removing that uniqueness rule.
                    st.executeUpdate("""
                            DELETE FROM clip_entries
                            WHERE id NOT IN (
                                SELECT keeper.id
                                FROM clip_entries AS keeper
                                WHERE keeper.id = (
                                    SELECT candidate.id
                                    FROM clip_entries AS candidate
                                    WHERE candidate.content_hash = keeper.content_hash
                                    ORDER BY candidate.is_favorite DESC,
                                             candidate.last_copied_at DESC,
                                             candidate.created_at DESC,
                                             candidate.id DESC
                                    LIMIT 1
                                )
                            )
                            """);
                }

                if (existingVersion < 4) {
                    // Preserve the exact pinned order users saw before manual ordering existed:
                    // newest pinned clip first, then deterministic id fallback.
                    st.executeUpdate("""
                            UPDATE clip_entries AS target
                            SET pin_order = (
                                SELECT COUNT(*)
                                FROM clip_entries AS other
                                WHERE other.is_favorite = 1
                                  AND (
                                      other.last_copied_at > target.last_copied_at
                                      OR (
                                          other.last_copied_at = target.last_copied_at
                                          AND other.id > target.id
                                      )
                                  )
                            )
                            WHERE target.is_favorite = 1
                            """);
                    st.executeUpdate("""
                            UPDATE clip_entries
                            SET pin_order = NULL
                            WHERE is_favorite = 0
                            """);
                }

                // Remove v5 uniqueness before recalculating hashes. Finite
                // duplicate windows intentionally permit equal keys in v6.
                st.execute("DROP INDEX IF EXISTS idx_clip_hash_unique;");

                if (existingVersion < 6 || hasMissingDuplicateHashes(c)) {
                    backfillDuplicateHashes(c);
                }

                st.execute("DROP INDEX IF EXISTS idx_clip_hash;");
                st.execute("DROP INDEX IF EXISTS idx_clip_fav_created;");
                st.execute("DROP INDEX IF EXISTS idx_clip_fav_last_copied;");
                st.execute("""
                        CREATE INDEX IF NOT EXISTS idx_clip_hash
                        ON clip_entries(content_hash, last_copied_at DESC, id DESC)
                        """);
                st.execute("""
                        CREATE INDEX IF NOT EXISTS idx_clip_exact_hash
                        ON clip_entries(content_exact_hash, last_copied_at DESC, id DESC)
                        """);
                st.execute("""
                        CREATE INDEX IF NOT EXISTS idx_clip_exact_ci_hash
                        ON clip_entries(content_exact_ci_hash, last_copied_at DESC, id DESC)
                        """);
                st.execute("""
                        CREATE INDEX IF NOT EXISTS idx_clip_norm_ci_hash
                        ON clip_entries(content_norm_ci_hash, last_copied_at DESC, id DESC)
                        """);
                st.execute("""
                        CREATE INDEX IF NOT EXISTS idx_clip_pinned_order
                        ON clip_entries(is_favorite, pin_order, last_copied_at DESC)
                        """);

                // v5 tag foundation. These statements are intentionally
                // idempotent because applyBaseSchema also creates them for new DBs.
                st.execute("""
                        CREATE TABLE IF NOT EXISTS tags (
                          id         INTEGER PRIMARY KEY AUTOINCREMENT,
                          name       TEXT    NOT NULL,
                          name_norm  TEXT    NOT NULL,
                          created_at INTEGER NOT NULL,
                          CONSTRAINT ck_tags_name_length
                            CHECK (length(name) BETWEEN 1 AND 64),
                          CONSTRAINT uq_tags_name_norm UNIQUE (name_norm)
                        )
                        """);
                st.execute("""
                        CREATE TABLE IF NOT EXISTS clip_tags (
                          clip_id     INTEGER NOT NULL,
                          tag_id      INTEGER NOT NULL,
                          assigned_at INTEGER NOT NULL,
                          PRIMARY KEY (clip_id, tag_id),
                          FOREIGN KEY (clip_id)
                            REFERENCES clip_entries(id) ON DELETE CASCADE,
                          FOREIGN KEY (tag_id)
                            REFERENCES tags(id) ON DELETE CASCADE
                        )
                        """);
                st.execute("""
                        CREATE INDEX IF NOT EXISTS idx_tags_name
                        ON tags(name COLLATE NOCASE, id)
                        """);
                st.execute("""
                        CREATE INDEX IF NOT EXISTS idx_clip_tags_tag_id
                        ON clip_tags(tag_id, clip_id)
                        """);

                st.execute("PRAGMA user_version = " + CURRENT_SCHEMA_VERSION + ";");
            }

            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(previousAutoCommit);
        }
    }

    private boolean hasMissingDuplicateHashes(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT 1
                     FROM clip_entries
                     WHERE content_exact_hash IS NULL
                        OR content_exact_ci_hash IS NULL
                        OR content_norm_ci_hash IS NULL
                     LIMIT 1
                     """)) {
            return rs.next();
        }
    }

    private void backfillDuplicateHashes(Connection c) throws SQLException {
        long lastId = 0L;
        final int batchSize = 16;

        while (true) {
            List<ExistingContent> rows = new ArrayList<>(batchSize);
            try (java.sql.PreparedStatement select = c.prepareStatement("""
                    SELECT id, content
                    FROM clip_entries
                    WHERE id > ?
                    ORDER BY id ASC
                    LIMIT ?
                    """)) {
                select.setLong(1, lastId);
                select.setInt(2, batchSize);
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new ExistingContent(
                                rs.getLong("id"),
                                rs.getString("content")
                        ));
                    }
                }
            }

            if (rows.isEmpty()) return;

            try (java.sql.PreparedStatement update = c.prepareStatement("""
                    UPDATE clip_entries
                    SET content_hash = ?,
                        content_exact_hash = ?,
                        content_exact_ci_hash = ?,
                        content_norm_ci_hash = ?
                    WHERE id = ?
                    """)) {
                for (ExistingContent row : rows) {
                    DuplicateContentKeys keys = DuplicateContentKeys.from(row.content());
                    update.setString(1, keys.normalizedHash());
                    update.setString(2, keys.exactHash());
                    update.setString(3, keys.exactCaseInsensitiveHash());
                    update.setString(4, keys.normalizedCaseInsensitiveHash());
                    update.setLong(5, row.id());
                    update.addBatch();
                    lastId = row.id();
                }
                update.executeBatch();
            }
        }
    }

    private record ExistingContent(long id, String content) {}

    private void ensureColumn(Connection c, String columnName, String definition) throws SQLException {
        if (tableColumns(c, "clip_entries").contains(columnName)) return;

        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE clip_entries ADD COLUMN " + columnName + " " + definition + ";");
        }
    }

    private Set<String> tableColumns(Connection c, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + tableName + ");")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    private int readUserVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version;")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Marks this database facade as closed. Safe to call multiple times.
     *
     * Initialization connections are operation-scoped. DAO-managed connections
     * are closed separately by their owning services.
     */
    public void close() {
        closed.compareAndSet(false, true);
    }

    /**
     * Deletes the complete SQLite file set from disk.
     *
     * DAO-owned connections must be closed by their owners before this method is
     * called. WAL, shared-memory, and rollback-journal sidecars are removed
     * explicitly so "Clear all data" cannot leave user-owned database artifacts.
     */
    public void deleteDatabaseFile() {
        close();

        RuntimeException failure = null;
        for (Path file : databaseFilesForDeletion()) {
            try {
                Files.deleteIfExists(file);
            } catch (Exception error) {
                RuntimeException next = new RuntimeException(
                        "Failed to delete database file: " + file,
                        error
                );
                if (failure == null) failure = next;
                else failure.addSuppressed(next);
            }
        }

        if (failure != null) throw failure;
    }

    private List<Path> databaseFilesForDeletion() {
        String fileName = dbPath.getFileName().toString();
        Path parent = dbPath.getParent();
        if (parent == null) parent = Path.of(".");

        return List.of(
                parent.resolve(fileName + "-wal"),
                parent.resolve(fileName + "-shm"),
                parent.resolve(fileName + "-journal"),
                dbPath
        );
    }

    private static String loadResourceText(String path) {
        try (var is = Database.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8)
            )) {
                return br.lines().collect(Collectors.joining("\n"));
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load resource: " + path, e);
        }
    }
}