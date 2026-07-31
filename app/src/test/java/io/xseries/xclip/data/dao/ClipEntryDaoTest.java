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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void countAllIsIndependentOfPinnedStateAndListFilters() {
        Path dbPath = tempDir.resolve("count-all.db");
        Database db = new Database(dbPath);
        db.init();

        ClipEntryDao dao = new ClipEntryDao(db.jdbcUrl());
        try {
            dao.insert("alpha", "alpha", "hash-alpha", 1_000L);
            dao.insert("beta", "beta", "hash-beta", 2_000L);
            dao.insert("gamma", "gamma", "hash-gamma", 3_000L);

            long betaId = idFor(dao, "beta");
            dao.setFavorite(betaId, true);

            assertEquals(3, dao.countAll());
            assertEquals(1, dao.listLatest(10, true).size());
            assertEquals(2, dao.listLatest(10, false).size());
            assertEquals(3, dao.countAll());
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

    @Test
    void storesPinnedTitleAndSearchesByTitleWithoutChangingContent() {
        Path dbPath = tempDir.resolve("titles.db");
        Database db = new Database(dbPath);
        db.init();

        ClipEntryDao dao = new ClipEntryDao(db.jdbcUrl());
        try {
            dao.insert("git status --short", "git status --short", "hash-command", 1_000L);

            ClipEntry entry = dao.listLatest(10).get(0);

            // Titles are intentionally restricted to pinned entries.
            dao.setTitle(entry.id(), "Should not be stored");
            assertNull(dao.listLatest(10).get(0).title());

            dao.setFavorite(entry.id(), true);
            dao.setTitle(entry.id(), "Repository status");

            List<ClipEntry> matches = dao.search("Repository", 10);

            assertEquals(1, matches.size());
            assertEquals("Repository status", matches.get(0).title());
            assertEquals("git status --short", matches.get(0).content());

            // Reusing the same clipboard content must not erase user metadata.
            dao.insert("git status --short", "git status --short", "hash-command", 2_000L);
            assertEquals("Repository status", dao.listLatest(10).get(0).title());

            dao.setTitle(entry.id(), "   ");
            assertNull(dao.listLatest(10).get(0).title());
        } finally {
            dao.closeForCurrentThread();
            db.close();
        }
    }


    @Test
    void supportsPersistentManualOrderingForPinnedClips() {
        Path dbPath = tempDir.resolve("manual-order.db");
        Database db = new Database(dbPath);
        db.init();

        ClipEntryDao dao = new ClipEntryDao(db.jdbcUrl());
        try {
            dao.insert("first", "first", "hash-first", 1_000L);
            dao.insert("second", "second", "hash-second", 2_000L);
            dao.insert("third", "third", "hash-third", 3_000L);

            long firstId = idFor(dao, "first");
            long secondId = idFor(dao, "second");
            long thirdId = idFor(dao, "third");

            dao.setFavorite(firstId, true);
            dao.setFavorite(secondId, true);
            dao.setFavorite(thirdId, true);

            // Each newly pinned clip is placed at the top.
            assertEquals(List.of("third", "second", "first"), pinnedContents(dao));
            assertEquals(List.of(0, 1, 2), pinnedOrders(dao));

            // Pinning an already pinned clip is a no-op and keeps manual order.
            dao.setFavorite(secondId, true);
            assertEquals(List.of("third", "second", "first"), pinnedContents(dao));

            assertTrue(dao.movePinnedToTop(firstId));
            assertEquals(List.of("first", "third", "second"), pinnedContents(dao));
            assertFalse(dao.movePinnedUp(firstId));

            assertTrue(dao.movePinnedDown(thirdId));
            assertEquals(List.of("first", "second", "third"), pinnedContents(dao));

            assertTrue(dao.movePinnedToBottom(firstId));
            assertEquals(List.of("second", "third", "first"), pinnedContents(dao));

            // Reusing pinned content updates recency but must not change manual order.
            dao.insert("second", "second", "hash-second", 9_000L);
            assertEquals(List.of("second", "third", "first"), pinnedContents(dao));
            assertEquals(List.of("second", "third", "first"),
                    dao.search("", 10).stream()
                            .filter(ClipEntry::favorite)
                            .map(ClipEntry::content)
                            .toList());

            dao.setFavorite(secondId, false);
            assertEquals(List.of("third", "first"), pinnedContents(dao));
            assertEquals(List.of(0, 1), pinnedOrders(dao));

            // Re-pinning is intentionally treated as a new pin and returns to the top.
            dao.setFavorite(secondId, true);
            assertEquals(List.of("second", "third", "first"), pinnedContents(dao));
            assertEquals(List.of(0, 1, 2), pinnedOrders(dao));
        } finally {
            dao.closeForCurrentThread();
            db.close();
        }
    }


    @Test
    void canRestrictListingAndSearchByPinnedState() {
        Path dbPath = tempDir.resolve("scope-filter.db");
        Database db = new Database(dbPath);
        db.init();

        ClipEntryDao dao = new ClipEntryDao(db.jdbcUrl());
        try {
            dao.insert("shared pinned text", "shared pinned text", "hash-pinned-scope", 1_000L);
            dao.insert("shared recent text", "shared recent text", "hash-recent-scope", 2_000L);

            long pinnedId = idFor(dao, "shared pinned text");
            dao.setFavorite(pinnedId, true);
            dao.setTitle(pinnedId, "Pinned scope title");

            List<ClipEntry> pinnedOnly = dao.listLatest(10, true);
            List<ClipEntry> recentOnly = dao.listLatest(10, false);

            assertEquals(List.of("shared pinned text"),
                    pinnedOnly.stream().map(ClipEntry::content).toList());
            assertEquals(List.of("shared recent text"),
                    recentOnly.stream().map(ClipEntry::content).toList());

            assertEquals(List.of("shared pinned text"),
                    dao.search("shared", 10, true).stream().map(ClipEntry::content).toList());
            assertEquals(List.of("shared recent text"),
                    dao.search("shared", 10, false).stream().map(ClipEntry::content).toList());

            // Title matching is still available only for pinned entries.
            assertEquals(1, dao.search("scope title", 10, true).size());
            assertTrue(dao.search("scope title", 10, false).isEmpty());
        } finally {
            dao.closeForCurrentThread();
            db.close();
        }
    }


    @Test
    void unifiedPopupQueryFiltersByTagAndSearchesAssignedTagNames() {
        Path dbPath = tempDir.resolve("tag-query.db");
        Database db = new Database(dbPath);
        db.init();

        ClipEntryDao clips = new ClipEntryDao(db.jdbcUrl());
        TagDao tags = new TagDao(db.jdbcUrl());
        try {
            clips.insert("release notes", "release notes", "hash-release", 1_000L);
            clips.insert("private token", "private token", "hash-private", 2_000L);
            clips.insert("unrelated", "unrelated", "hash-unrelated", 3_000L);

            long releaseId = idFor(clips, "release notes");
            long privateId = idFor(clips, "private token");

            var work = tags.createOrGet("Work");
            var secret = tags.createOrGet("Private");
            tags.addTagToClip(releaseId, work.id());
            tags.addTagToClip(privateId, secret.id());

            assertEquals(
                    List.of("release notes"),
                    clips.queryLatest("", 20, null, work.id()).stream()
                            .map(ClipEntry::content)
                            .toList()
            );
            assertEquals(
                    List.of("private token"),
                    clips.queryLatest("priv", 20, null, null).stream()
                            .map(ClipEntry::content)
                            .toList()
            );
            assertTrue(clips.queryLatest("private", 20, null, work.id()).isEmpty());

            clips.setFavorite(releaseId, true);
            assertEquals(
                    List.of("release notes"),
                    clips.queryLatest("work", 20, true, null).stream()
                            .map(ClipEntry::content)
                            .toList()
            );
            assertTrue(clips.queryLatest("work", 20, false, null).isEmpty());
        } finally {
            tags.closeForCurrentThread();
            clips.closeForCurrentThread();
            db.close();
        }
    }

    private long idFor(ClipEntryDao dao, String content) {
        return dao.listLatest(100).stream()
                .filter(e -> content.equals(e.content()))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private List<String> pinnedContents(ClipEntryDao dao) {
        return dao.listLatest(100).stream()
                .filter(ClipEntry::favorite)
                .map(ClipEntry::content)
                .toList();
    }

    private List<Integer> pinnedOrders(ClipEntryDao dao) {
        return dao.listLatest(100).stream()
                .filter(ClipEntry::favorite)
                .map(ClipEntry::pinOrder)
                .toList();
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

