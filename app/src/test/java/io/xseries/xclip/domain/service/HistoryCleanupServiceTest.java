/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.service;

import io.xseries.xclip.data.dao.ClipEntryDao;
import io.xseries.xclip.data.db.Database;
import io.xseries.xclip.domain.duplicate.DuplicateContentKeys;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.retention.HistoryRetentionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryCleanupServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void ageCleanupUsesTypeOverridesAndAlwaysPreservesPinned() {
        Fixture fixture = fixture();
        long day = HistoryRetentionPolicy.MILLIS_PER_DAY;
        long now = 100L * day;

        long oldText = fixture.insert("ordinary old note", now - 40L * day);
        long oldUrl = fixture.insert("https://example.com/old", now - 8L * day);
        fixture.insert("https://example.com/recent", now - 2L * day);
        long pinned = fixture.insert("pinned old note", now - 90L * day);
        fixture.dao.setFavorite(pinned, true);

        HistoryCleanupService service = new HistoryCleanupService(fixture.dao);
        service.applyPolicy(new HistoryRetentionPolicy(
                true,
                30,
                Map.of(ClipContentType.URL, 7),
                false
        ));

        HistoryCleanupService.CleanupStatus status = service.runCleanupAt(
                now,
                HistoryCleanupService.CleanupTrigger.MANUAL
        );

        assertEquals(HistoryCleanupService.CleanupOutcome.SUCCESS, status.outcome());
        assertEquals(2, status.deletedCount());
        assertEquals(2, fixture.dao.countAll());
        List<String> remaining = fixture.dao.listLatest(20).stream()
                .map(entry -> entry.content())
                .toList();
        assertTrue(remaining.contains("https://example.com/recent"));
        assertTrue(remaining.contains("pinned old note"));

        service.close();
        fixture.close();
    }

    @Test
    void disabledAgeRulesPublishSkippedWithoutDeletingAnything() {
        Fixture fixture = fixture();
        fixture.insert("keep me", 1L);

        HistoryCleanupService service = new HistoryCleanupService(fixture.dao);
        service.applyPolicy(HistoryRetentionPolicy.defaults());

        HistoryCleanupService.CleanupStatus status = service.runCleanupAt(
                100L * HistoryRetentionPolicy.MILLIS_PER_DAY,
                HistoryCleanupService.CleanupTrigger.STARTUP
        );

        assertEquals(HistoryCleanupService.CleanupOutcome.SKIPPED, status.outcome());
        assertEquals(0, status.deletedCount());
        assertEquals(1, fixture.dao.countAll());

        service.close();
        fixture.close();
    }

    @Test
    void clearOnExitDeletesOnlyRecentHistory() {
        Fixture fixture = fixture();
        fixture.insert("recent one", 10L);
        fixture.insert("recent two", 20L);
        long pinned = fixture.insert("pinned", 1L);
        fixture.dao.setFavorite(pinned, true);

        HistoryCleanupService service = new HistoryCleanupService(fixture.dao);
        service.applyPolicy(new HistoryRetentionPolicy(
                false,
                30,
                Map.of(),
                true
        ));

        HistoryCleanupService.CleanupStatus status = service.shutdownAndClearOnExit();

        assertEquals(HistoryCleanupService.CleanupOutcome.SUCCESS, status.outcome());
        assertEquals(2, status.deletedCount());
        assertEquals(1, fixture.dao.countAll());
        assertEquals("pinned", fixture.dao.listLatest(10).get(0).content());

        fixture.close();
    }

    private Fixture fixture() {
        Database database = new Database(tempDir.resolve("xclip.db"));
        database.init();
        return new Fixture(database, new ClipEntryDao(database.jdbcUrl()));
    }

    private static final class Fixture {
        private final Database database;
        private final ClipEntryDao dao;

        private Fixture(Database database, ClipEntryDao dao) {
            this.database = database;
            this.dao = dao;
        }

        private long insert(String content, long createdAt) {
            DuplicateContentKeys keys = DuplicateContentKeys.from(content);
            dao.insertNew(
                    content,
                    content.trim(),
                    keys,
                    createdAt
            );
            return dao.listLatest(100).stream()
                    .filter(entry -> entry.content().equals(content))
                    .findFirst()
                    .orElseThrow()
                    .id();
        }

        private void close() {
            dao.closeForCurrentThread();
            database.close();
        }
    }
}
