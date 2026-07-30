/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.db;

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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class Database {

    private static final int CURRENT_SCHEMA_VERSION = 5;

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

        try (Connection c = DriverManager.getConnection(jdbcUrl);
             Statement st = c.createStatement()) {

            // --- PRAGMA: must be first ---
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=NORMAL;");
            st.execute("PRAGMA foreign_keys=ON;");
            st.execute("PRAGMA temp_store=MEMORY;");
            st.execute("PRAGMA busy_timeout=3000;");

            applyBaseSchema(st);
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

            try (Statement st = c.createStatement()) {
                // Existing v1 rows did not have last_copied_at.
                st.executeUpdate("""
                        UPDATE clip_entries
                        SET last_copied_at = created_at
                        WHERE last_copied_at IS NULL OR last_copied_at <= 0
                        """);

                // The old schema used a non-unique hash index. Keep the best row
                // before enforcing uniqueness: pinned first, then most recently used.
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

                st.execute("DROP INDEX IF EXISTS idx_clip_hash;");
                st.execute("DROP INDEX IF EXISTS idx_clip_fav_created;");
                st.execute("DROP INDEX IF EXISTS idx_clip_fav_last_copied;");
                st.execute("""
                        CREATE UNIQUE INDEX IF NOT EXISTS idx_clip_hash_unique
                        ON clip_entries(content_hash)
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
     * Safe to call multiple times.
     * Currently no persistent connections are held,
     * but this is kept for symmetry and future pooling.
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // No-op for now (connections are per-operation)
        // Reserved for future connection pool or migration locks
    }

    /**
     * Deletes the database file from disk.
     *
     * Notes:
     * - Calls close() first (idempotent).
     * - Since the app uses per-operation connections, there is no single Connection to close here.
     * - Deletion can still fail on Windows if some other process/thread holds the DB file open.
     *   In that case we throw, so caller can show a proper UI message.
     */
    public void deleteDatabaseFile() {
        close();

        try {
            Files.deleteIfExists(dbPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete database file: " + dbPath, e);
        }
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
