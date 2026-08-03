

/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
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
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryCleanupServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void ageCleanupUsesTypeOverridesAndAlwaysPreservesPinned() {
        try (Fixture fixture = fixture();
             HistoryCleanupService service = new HistoryCleanupService(fixture.dao)) {
            long day = HistoryRetentionPolicy.MILLIS_PER_DAY;
            long now = 100L * day;

            fixture.insert("ordinary old note", now - 40L * day);
            fixture.insert("https://example.com/old", now - 8L * day);
            fixture.insert("https://example.com/recent", now - 2L * day);
            long pinned = fixture.insert("pinned old note", now - 90L * day);
            fixture.dao.setFavorite(pinned, true);

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
        }
    }

    @Test
    void disabledAgeRulesPublishSkippedWithoutDeletingAnything() {
        try (Fixture fixture = fixture();
             HistoryCleanupService service = new HistoryCleanupService(fixture.dao)) {
            fixture.insert("keep me", 1L);
            service.applyPolicy(HistoryRetentionPolicy.defaults());

            HistoryCleanupService.CleanupStatus status = service.runCleanupAt(
                    100L * HistoryRetentionPolicy.MILLIS_PER_DAY,
                    HistoryCleanupService.CleanupTrigger.STARTUP
            );

            assertEquals(HistoryCleanupService.CleanupOutcome.SKIPPED, status.outcome());
            assertEquals(0, status.deletedCount());
            assertEquals(1, fixture.dao.countAll());
        }
    }

    @Test
    void cleanupPagesLargeCandidateSetsAndDeletesAcrossSqlBatches() {
        try (Fixture fixture = fixture();
             HistoryCleanupService service = new HistoryCleanupService(fixture.dao)) {
            long day = HistoryRetentionPolicy.MILLIS_PER_DAY;
            long now = 100L * day;
            long old = now - 8L * day;

            int oldUrlCount = 530;
            int oldTextCount = 25;
            for (int index = 0; index < oldUrlCount; index++) {
                fixture.insertDirect("https://example.com/archive/" + index, old);
            }
            for (int index = 0; index < oldTextCount; index++) {
                fixture.insertDirect("ordinary archive note " + index, old);
            }

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
            assertEquals(oldUrlCount, status.deletedCount());
            assertEquals(oldTextCount, fixture.dao.countAll());
            assertTrue(
                    fixture.dao.listLatest(100).stream()
                            .allMatch(entry -> entry.content().startsWith("ordinary archive note "))
            );
        }
    }

    @Test
    void clearOnExitDeletesOnlyRecentHistory() {
        try (Fixture fixture = fixture();
             HistoryCleanupService service = new HistoryCleanupService(fixture.dao)) {
            fixture.insert("recent one", 10L);
            fixture.insert("recent two", 20L);
            long pinned = fixture.insert("pinned", 1L);
            fixture.dao.setFavorite(pinned, true);

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
        }
    }

    @Test
    void manualClearRecentDeletesOnlyUnpinnedAndPublishesDedicatedTrigger() {
        try (Fixture fixture = fixture();
             HistoryCleanupService service = new HistoryCleanupService(fixture.dao)) {
            fixture.insert("recent one", 10L);
            fixture.insert("recent two", 20L);
            long pinned = fixture.insert("pinned", 1L);
            fixture.dao.setFavorite(pinned, true);

            HistoryCleanupService.CleanupStatus status = service.clearRecentAt(30L);

            assertEquals(
                    HistoryCleanupService.CleanupTrigger.MANUAL_CLEAR_RECENT,
                    status.trigger()
            );
            assertEquals(HistoryCleanupService.CleanupOutcome.SUCCESS, status.outcome());
            assertEquals(2, status.deletedCount());
            assertEquals(1, fixture.dao.countAll());
            assertEquals("pinned", fixture.dao.listLatest(10).get(0).content());
        }
    }

    @Test
    void maintenancePauseBlocksCleanupAndCanResumeAfterFailedDataOperation() {
        try (Fixture fixture = fixture();
             HistoryCleanupService service = new HistoryCleanupService(fixture.dao)) {
            long day = HistoryRetentionPolicy.MILLIS_PER_DAY;
            long now = 100L * day;
            fixture.insert("old value", now - 40L * day);

            service.applyPolicy(new HistoryRetentionPolicy(
                    true,
                    30,
                    Map.of(),
                    false
            ));

            HistoryCleanupService.CleanupStatus before = service.status();
            service.pauseForMaintenance();

            HistoryCleanupService.CleanupStatus paused = service.runCleanupAt(
                    now,
                    HistoryCleanupService.CleanupTrigger.MANUAL
            );

            assertSame(before, paused);
            assertEquals(1, fixture.dao.countAll());

            service.resumeAfterMaintenance();
            HistoryCleanupService.CleanupStatus resumed = service.runCleanupAt(
                    now,
                    HistoryCleanupService.CleanupTrigger.MANUAL
            );

            assertEquals(HistoryCleanupService.CleanupOutcome.SUCCESS, resumed.outcome());
            assertEquals(1, resumed.deletedCount());
            assertEquals(0, fixture.dao.countAll());
        }
    }

    @Test
    void closeTerminatesInjectedExecutorThread() {
        try (Fixture fixture = fixture()) {
            AtomicReference<Thread> workerThread = new AtomicReference<>();
            ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                    1,
                    runnable -> {
                        Thread thread = new Thread(runnable, "history-cleanup-test");
                        thread.setDaemon(true);
                        workerThread.set(thread);
                        return thread;
                    }
            );
            executor.prestartCoreThread();

            HistoryCleanupService service = new HistoryCleanupService(
                    fixture.dao,
                    Clock.systemUTC(),
                    executor
            );
            service.close();

            Thread worker = workerThread.get();
            assertNotNull(worker);
            assertTrue(executor.isTerminated());
            assertFalse(worker.isAlive());
        }
    }

    @Test
    void clearOnExitHasHardTimeout() {
        try (Fixture fixture = fixture()) {
            ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
            HistoryCleanupService service = new HistoryCleanupService(
                    fixture.dao,
                    Clock.systemUTC(),
                    executor,
                    () -> {
                        long deadline = System.nanoTime()
                                + java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
                        while (System.nanoTime() < deadline) {
                            try {
                                Thread.sleep(25L);
                            } catch (InterruptedException ignored) {
                                // Simulate a native/database call that does not stop immediately.
                            }
                        }
                        return 0;
                    }
            );
            service.applyPolicy(new HistoryRetentionPolicy(
                    false,
                    HistoryRetentionPolicy.DEFAULT_RECENT_MAX_AGE_DAYS,
                    Map.of(),
                    true
            ));

            long started = System.nanoTime();
            HistoryCleanupService.CleanupStatus status =
                    service.shutdownAndClearOnExit(Duration.ofMillis(75L));
            long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - started
            );

            assertEquals(HistoryCleanupService.CleanupOutcome.TIMED_OUT, status.outcome());
            assertTrue(elapsedMillis < 750L, "exit cleanup exceeded its hard bound");
        }
    }

    private Fixture fixture() {
        Database database = new Database(tempDir.resolve("xclip.db"));
        database.init();
        return new Fixture(database, new ClipEntryDao(database.jdbcUrl()));
    }

    private static final class Fixture implements AutoCloseable {
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

        private void insertDirect(String content, long createdAt) {
            DuplicateContentKeys keys = DuplicateContentKeys.from(content);
            dao.insertNew(
                    content,
                    content.trim(),
                    keys,
                    createdAt
            );
        }

        @Override
        public void close() {
            dao.close();
            database.close();
        }
    }
}
