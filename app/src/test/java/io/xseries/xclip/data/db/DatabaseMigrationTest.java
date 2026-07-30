/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void migratesLegacySchemaAndBackfillsRecencyAndPinnedOrder() throws Exception {
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
                    "title",
                    "is_favorite",
                    "pin_order",
                    "created_at",
                    "last_copied_at",
                    "use_count"
            ), tableColumns(c));

            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("""
                         SELECT created_at, last_copied_at, use_count, title
                         FROM clip_entries
                         WHERE content_hash = 'hash-old'
                         """)) {
                assertTrue(rs.next());
                assertEquals(1_000L, rs.getLong("created_at"));
                assertEquals(1_000L, rs.getLong("last_copied_at"));
                assertEquals(1, rs.getInt("use_count"));
                assertNull(rs.getString("title"));
            }

            Map<String, Integer> pinOrders = readPinOrders(c);
            assertEquals(0, pinOrders.get("newer pinned").intValue());
            assertEquals(1, pinOrders.get("older pinned").intValue());
            assertNull(pinOrders.get("recent"));

            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                assertTrue(rs.next());
                assertEquals(4, rs.getInt(1));
            }

            assertTrue(hasIndex(c, "idx_clip_hash_unique", true));
            assertTrue(hasIndex(c, "idx_clip_pinned_order", false));
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

    private Set<String> tableColumns(Connection c) throws Exception {
        Set<String> columns = new HashSet<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(clip_entries)")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    private boolean hasIndex(Connection c, String name, boolean unique) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA index_list(clip_entries)")) {
            while (rs.next()) {
                if (name.equals(rs.getString("name"))
                        && (rs.getInt("unique") == 1) == unique) {
                    return true;
                }
            }
        }
        return false;
    }
}
