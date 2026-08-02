/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.db;

import io.xseries.xclip.domain.duplicate.DuplicateContentKeys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void migratesLegacySchemaAndCreatesTagFoundation() throws Exception {
        Path dbPath = tempDir.resolve("legacy.db");
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        try (Connection c = DriverManager.getConnection(jdbcUrl);
             Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE clip_entries (
                      id           INTEGER PRIMARY KEY AUTOINCREMENT,
                      content      TEXT    NOT NULL,
                      content_norm TEXT    NOT NULL,
                      content_hash TEXT    NOT NULL,
                      is_favorite  INTEGER NOT NULL DEFAULT 0,
                      created_at   INTEGER NOT NULL
                    )
                    """);
            st.execute("CREATE INDEX idx_clip_hash ON clip_entries(content_hash)");
            st.execute("PRAGMA user_version = 3");
        }

        insertLegacy(jdbcUrl, "older pinned", "hash-old", true, 1_000L);
        insertLegacy(jdbcUrl, "newer pinned", "hash-new", true, 3_000L);
        insertLegacy(jdbcUrl, "recent", "hash-recent", false, 5_000L);

        new Database(dbPath).init();

        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            assertEquals(Set.of(
                    "id",
                    "content",
                    "content_norm",
                    "content_hash",
                    "content_exact_hash",
                    "content_exact_ci_hash",
                    "content_norm_ci_hash",
                    "title",
                    "is_favorite",
                    "pin_order",
                    "created_at",
                    "last_copied_at",
                    "use_count"
            ), tableColumns(c, "clip_entries"));

            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("""
                         SELECT created_at, last_copied_at, use_count, title
                         FROM clip_entries
                         WHERE content = 'older pinned'
                         """)) {
                assertTrue(rs.next());
                assertEquals(1_000L, rs.getLong("created_at"));
                assertEquals(1_000L, rs.getLong("last_copied_at"));
                assertEquals(1, rs.getInt("use_count"));
                assertNull(rs.getString("title"));
            }

            DuplicateContentKeys keys = DuplicateContentKeys.from("older pinned");
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT content_hash, content_exact_hash,
                           content_exact_ci_hash, content_norm_ci_hash
                    FROM clip_entries
                    WHERE content = ?
                    """)) {
                ps.setString(1, "older pinned");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(keys.normalizedHash(), rs.getString("content_hash"));
                    assertEquals(keys.exactHash(), rs.getString("content_exact_hash"));
                    assertEquals(keys.exactCaseInsensitiveHash(),
                            rs.getString("content_exact_ci_hash"));
                    assertEquals(keys.normalizedCaseInsensitiveHash(),
                            rs.getString("content_norm_ci_hash"));
                }
            }

            Map<String, Integer> pinOrders = readPinOrders(c);
            assertEquals(0, pinOrders.get("newer pinned").intValue());
            assertEquals(1, pinOrders.get("older pinned").intValue());
            assertNull(pinOrders.get("recent"));

            assertEquals(Set.of("id", "name", "name_norm", "created_at"),
                    tableColumns(c, "tags"));
            assertEquals(Set.of("clip_id", "tag_id", "assigned_at"),
                    tableColumns(c, "clip_tags"));

            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                assertTrue(rs.next());
                assertEquals(6, rs.getInt(1));
            }

            assertTrue(hasIndex(c, "clip_entries", "idx_clip_hash", false));
            assertTrue(hasIndex(c, "clip_entries", "idx_clip_exact_hash", false));
            assertTrue(hasIndex(c, "clip_entries", "idx_clip_exact_ci_hash", false));
            assertTrue(hasIndex(c, "clip_entries", "idx_clip_norm_ci_hash", false));
            assertTrue(hasIndex(c, "clip_entries", "idx_clip_pinned_order", false));
            assertTrue(hasIndex(c, "tags", "idx_tags_name", false));
            assertTrue(hasIndex(c, "clip_tags", "idx_clip_tags_tag_id", false));

            assertTrue(hasCascadeForeignKey(
                    c, "clip_tags", "clip_id", "clip_entries", "id"
            ));
            assertTrue(hasCascadeForeignKey(
                    c, "clip_tags", "tag_id", "tags", "id"
            ));
        }
    }



    @Test
    void initializationPersistsWalModeAndFreshDatabaseCanBeRecreatedAfterDeletion()
            throws Exception {
        Path dbPath = tempDir.resolve("recreate.db");
        Database database = new Database(dbPath);
        database.init();

        try (Connection connection = DriverManager.getConnection(database.jdbcUrl());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA journal_mode")) {
            assertTrue(result.next());
            assertEquals("wal", result.getString(1).toLowerCase());
        }

        database.close();

        Path wal = Path.of(dbPath.toString() + "-wal");
        Path shm = Path.of(dbPath.toString() + "-shm");
        Path journal = Path.of(dbPath.toString() + "-journal");
        Files.writeString(wal, "stale wal");
        Files.writeString(shm, "stale shm");
        Files.writeString(journal, "stale journal");

        database.deleteDatabaseFile();

        assertFalse(Files.exists(dbPath));
        assertFalse(Files.exists(wal));
        assertFalse(Files.exists(shm));
        assertFalse(Files.exists(journal));

        Database recreated = new Database(dbPath);
        recreated.init();
        assertTrue(Files.exists(dbPath));
        recreated.close();
    }

    @Test
    void v6RestartPreservesIntentionalEqualRows() throws Exception {
        Path dbPath = tempDir.resolve("v6-duplicates.db");
        Database first = new Database(dbPath);
        first.init();

        String jdbcUrl = first.jdbcUrl();
        DuplicateContentKeys keys = DuplicateContentKeys.from("same value");
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO clip_entries(
                         content, content_norm, content_hash,
                         content_exact_hash, content_exact_ci_hash, content_norm_ci_hash,
                         created_at, last_copied_at, use_count
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                     """)) {
            for (long timestamp : new long[]{1_000L, 3_000L}) {
                ps.setString(1, "same value");
                ps.setString(2, "same value");
                ps.setString(3, keys.normalizedHash());
                ps.setString(4, keys.exactHash());
                ps.setString(5, keys.exactCaseInsensitiveHash());
                ps.setString(6, keys.normalizedCaseInsensitiveHash());
                ps.setLong(7, timestamp);
                ps.setLong(8, timestamp);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        first.close();
        new Database(dbPath).init();

        try (Connection c = DriverManager.getConnection(jdbcUrl);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM clip_entries")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }
    }

    private void insertLegacy(
            String jdbcUrl,
            String content,
            String hash,
            boolean favorite,
            long createdAt
    ) throws Exception {
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO clip_entries(
                         content, content_norm, content_hash, is_favorite, created_at
                     ) VALUES (?, ?, ?, ?, ?)
                     """)) {
            ps.setString(1, content);
            ps.setString(2, content);
            ps.setString(3, hash);
            ps.setInt(4, favorite ? 1 : 0);
            ps.setLong(5, createdAt);
            ps.executeUpdate();
        }
    }

    private Map<String, Integer> readPinOrders(Connection c) throws Exception {
        Map<String, Integer> values = new LinkedHashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT content, pin_order FROM clip_entries")) {
            while (rs.next()) {
                int raw = rs.getInt("pin_order");
                boolean pinOrderWasNull = rs.wasNull();
                String content = rs.getString("content");
                values.put(content, pinOrderWasNull ? null : raw);
            }
        }
        return values;
    }

    private Set<String> tableColumns(Connection c, String tableName) throws Exception {
        Set<String> columns = new HashSet<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    private boolean hasIndex(
            Connection c,
            String tableName,
            String name,
            boolean unique
    ) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA index_list(" + tableName + ")")) {
            while (rs.next()) {
                if (name.equals(rs.getString("name"))
                        && (rs.getInt("unique") == 1) == unique) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasCascadeForeignKey(
            Connection c,
            String tableName,
            String fromColumn,
            String targetTable,
            String targetColumn
    ) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA foreign_key_list(" + tableName + ")")) {
            while (rs.next()) {
                if (fromColumn.equals(rs.getString("from"))
                        && targetTable.equals(rs.getString("table"))
                        && targetColumn.equals(rs.getString("to"))
                        && "CASCADE".equalsIgnoreCase(rs.getString("on_delete"))) {
                    return true;
                }
            }
        }
        return false;
    }
}