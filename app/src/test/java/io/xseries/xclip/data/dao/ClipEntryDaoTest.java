/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.dao;

import io.xseries.xclip.data.db.Database;
import io.xseries.xclip.data.model.ClipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClipEntryDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void reusingExistingContentMovesItToTopWithoutCreatingDuplicate() throws Exception {
        Path dbPath = tempDir.resolve("xclip.db");
        Database db = new Database(dbPath);
        db.init();

        ClipEntryDao dao = new ClipEntryDao(db.jdbcUrl());
        try {
            dao.insert("alpha", "alpha", "hash-alpha", 1_000L);
            dao.insert("beta", "beta", "hash-beta", 2_000L);
            dao.insert("alpha", "alpha", "hash-alpha", 3_000L);

            List<ClipEntry> rows = dao.listLatest(10);

            assertEquals(2, rows.size());
            assertEquals("alpha", rows.get(0).content());
            assertEquals(3_000L, rows.get(0).createdAt());
            assertEquals("beta", rows.get(1).content());
            assertEquals(2, usageCount(db.jdbcUrl(), "hash-alpha"));
        } finally {
            dao.closeForCurrentThread();
            db.close();
        }
    }

    @Test
    void pinnedEntriesRemainBeforeRecentEntries() {
        Path dbPath = tempDir.resolve("pinned.db");
        Database db = new Database(dbPath);
        db.init();

        ClipEntryDao dao = new ClipEntryDao(db.jdbcUrl());
        try {
            dao.insert("pinned", "pinned", "hash-pinned", 1_000L);
            dao.insert("recent", "recent", "hash-recent", 5_000L);

            long pinnedId = dao.listLatest(10).stream()
                    .filter(e -> "pinned".equals(e.content()))
                    .findFirst()
                    .orElseThrow()
                    .id();
            dao.setFavorite(pinnedId, true);

            List<ClipEntry> rows = dao.listLatest(10);

            assertEquals("pinned", rows.get(0).content());
            assertEquals("recent", rows.get(1).content());
        } finally {
            dao.closeForCurrentThread();
            db.close();
        }
    }

    private int usageCount(String jdbcUrl, String hash) throws Exception {
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT use_count FROM clip_entries WHERE content_hash = '" + hash + "'"
             )) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
