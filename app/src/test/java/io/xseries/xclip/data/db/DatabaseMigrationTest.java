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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void migratesLegacySchemaAndBackfillsRecencyFields() throws Exception {
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
        }

        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO clip_entries(
                         content, content_norm, content_hash, is_favorite, created_at
                     ) VALUES (?, ?, ?, ?, ?)
                     """)) {
            ps.setString(1, "legacy value");
            ps.setString(2, "legacy value");
            ps.setString(3, "legacy-hash");
            ps.setInt(4, 0);
            ps.setLong(5, 1234L);
            ps.executeUpdate();
        }

        new Database(dbPath).init();

        try (Connection c = DriverManager.getConnection(jdbcUrl)) {
            assertEquals(Set.of(
                    "id",
                    "content",
                    "content_norm",
                    "content_hash",
                    "title",
                    "is_favorite",
                    "created_at",
                    "last_copied_at",
                    "use_count"
            ), tableColumns(c));

            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("""
                         SELECT created_at, last_copied_at, use_count, title
                         FROM clip_entries
                         WHERE content_hash = 'legacy-hash'
                         """)) {
                assertTrue(rs.next());
                assertEquals(1234L, rs.getLong("created_at"));
                assertEquals(1234L, rs.getLong("last_copied_at"));
                assertEquals(1, rs.getInt("use_count"));
                assertNull(rs.getString("title"));
            }

            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1));
            }

            assertTrue(hasUniqueHashIndex(c));
        }
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

    private boolean hasUniqueHashIndex(Connection c) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA index_list(clip_entries)")) {
            while (rs.next()) {
                if ("idx_clip_hash_unique".equals(rs.getString("name"))
                        && rs.getInt("unique") == 1) {
                    return true;
                }
            }
        }
        return false;
    }
}
