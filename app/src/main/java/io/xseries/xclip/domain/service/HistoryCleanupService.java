
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.service;

import io.xseries.xclip.config.Config;
import io.xseries.xclip.data.dao.ClipEntryDao;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.retention.HistoryRetentionPolicy;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Executes explicit retention policy against unpinned clipboard history.
 *
 * Age cleanup runs at startup, after Settings Apply, on manual request, and on
 * a bounded six-hour cadence. Clear-on-exit is synchronous so the application
 * cannot terminate before the requested destructive action completes.
 */
public final class HistoryCleanupService implements AutoCloseable {

    public static final long PERIODIC_INTERVAL_HOURS = 6L;
    static final int RETENTION_SCAN_BATCH_SIZE = 512;

    private static final long DISABLED_CUTOFF = -1L;

    private final ClipEntryDao dao;
    private final Clock clock;
    private final ScheduledExecutorService executor;
    private final Object cleanupLock = new Object();
    private final AtomicReference<HistoryRetentionPolicy> policy =
            new AtomicReference<>(HistoryRetentionPolicy.defaults());
    private final AtomicReference<CleanupStatus> status =
            new AtomicReference<>(CleanupStatus.notRun());
    private final CopyOnWriteArrayList<Consumer<CleanupStatus>> listeners =
            new CopyOnWriteArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public HistoryCleanupService(ClipEntryDao dao) {
        this(dao, Clock.systemDefaultZone(), newExecutor());
    }

    HistoryCleanupService(
            ClipEntryDao dao,
            Clock clock,
            ScheduledExecutorService executor
    ) {
        this.dao = Objects.requireNonNull(dao, "dao");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public void applyConfig(Config config) {
        if (config == null) return;
        applyPolicy(config.historyRetentionPolicy());
    }

    public void applyPolicy(HistoryRetentionPolicy nextPolicy) {
        policy.set(Objects.requireNonNull(nextPolicy, "nextPolicy"));
        if (started.get() && !closed.get()) {
            requestCleanup(CleanupTrigger.SETTINGS_APPLY);
        }
    }

    public HistoryRetentionPolicy policy() {
        return policy.get();
    }

    public CleanupStatus status() {
        return status.get();
    }

    public void addStatusListener(Consumer<CleanupStatus> listener) {
        if (listener == null) return;
        listeners.add(listener);
        notifyListener(listener, status.get());
    }

    public void start() {
        if (closed.get() || !started.compareAndSet(false, true)) return;

        requestCleanup(CleanupTrigger.STARTUP);
        executor.scheduleAtFixedRate(
                () -> runCleanupSafely(CleanupTrigger.PERIODIC),
                PERIODIC_INTERVAL_HOURS,
                PERIODIC_INTERVAL_HOURS,
                TimeUnit.HOURS
        );
    }

    public void requestCleanup(CleanupTrigger trigger) {
        if (closed.get()) return;
        CleanupTrigger effective = Objects.requireNonNullElse(
                trigger,
                CleanupTrigger.MANUAL
        );
        try {
            executor.execute(() -> runCleanupSafely(effective));
        } catch (RejectedExecutionException ignored) {
            // Service is shutting down. Exit cleanup, when enabled, is handled synchronously.
        }
    }

    public CleanupStatus runCleanupNow(CleanupTrigger trigger) {
        return runCleanupAt(clock.millis(), Objects.requireNonNullElse(
                trigger,
                CleanupTrigger.MANUAL
        ));
    }

    CleanupStatus runCleanupAt(long nowMillis, CleanupTrigger trigger) {
        synchronized (cleanupLock) {
            HistoryRetentionPolicy snapshot = policy.get();
            if (!snapshot.anyAgeRuleEnabled()) {
                return publish(new CleanupStatus(
                        nowMillis,
                        trigger,
                        CleanupOutcome.SKIPPED,
                        0,
                        "No age-based cleanup rules are enabled"
                ));
            }

            try {
                long candidateCutoff = snapshot
                        .candidateCutoffExclusive(nowMillis)
                        .orElseThrow();
                RetentionEvaluationPlan evaluation =
                        RetentionEvaluationPlan.from(snapshot, nowMillis);
                List<Long> deleteIds = new ArrayList<>();

                long afterId = 0L;
                while (true) {
                    List<ClipEntryDao.RetentionCandidate> candidates =
                            dao.listRetentionCandidatesAfter(
                                    candidateCutoff,
                                    afterId,
                                    RETENTION_SCAN_BATCH_SIZE
                            );
                    if (candidates.isEmpty()) break;

                    for (ClipEntryDao.RetentionCandidate candidate : candidates) {
                        if (evaluation.shouldDelete(candidate)) {
                            deleteIds.add(candidate.id());
                        }
                    }

                    afterId = candidates.get(candidates.size() - 1).id();
                    if (candidates.size() < RETENTION_SCAN_BATCH_SIZE) break;
                }

                int deleted = dao.deleteByIds(deleteIds);
                return publish(new CleanupStatus(
                        nowMillis,
                        trigger,
                        CleanupOutcome.SUCCESS,
                        deleted,
                        deleted == 0
                                ? "No eligible RECENT clips"
                                : "Deleted " + deleted + " RECENT clip" + (deleted == 1 ? "" : "s")
                ));
            } catch (Throwable failure) {
                return publish(failedStatus(nowMillis, trigger, failure));
            } finally {
                dao.closeForCurrentThread();
            }
        }
    }

    /**
     * Stops periodic work and applies the explicit clear-on-exit policy.
     */
    public CleanupStatus shutdownAndClearOnExit() {
        close();
        synchronized (cleanupLock) {
            long now = clock.millis();
            HistoryRetentionPolicy snapshot = policy.get();
            if (!snapshot.clearRecentOnExit()) {
                return publish(new CleanupStatus(
                        now,
                        CleanupTrigger.EXIT,
                        CleanupOutcome.SKIPPED,
                        0,
                        "Clear on exit is disabled"
                ));
            }

            try {
                int deleted = dao.deleteAllNonFavorites();
                return publish(new CleanupStatus(
                        now,
                        CleanupTrigger.EXIT,
                        CleanupOutcome.SUCCESS,
                        deleted,
                        deleted == 0
                                ? "No RECENT clips to clear"
                                : "Cleared " + deleted + " RECENT clip" + (deleted == 1 ? "" : "s")
                ));
            } catch (Throwable failure) {
                return publish(failedStatus(now, CleanupTrigger.EXIT, failure));
            } finally {
                dao.closeForCurrentThread();
            }
        }
    }

    private void runCleanupSafely(CleanupTrigger trigger) {
        runCleanupAt(clock.millis(), trigger);
    }

    private CleanupStatus failedStatus(
            long completedAt,
            CleanupTrigger trigger,
            Throwable failure
    ) {
        String detail = failure.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = failure.getClass().getSimpleName();
        }
        return new CleanupStatus(
                completedAt,
                trigger,
                CleanupOutcome.FAILED,
                0,
                detail
        );
    }

    private CleanupStatus publish(CleanupStatus next) {
        status.set(next);
        for (Consumer<CleanupStatus> listener : listeners) {
            notifyListener(listener, next);
        }
        return next;
    }

    private void notifyListener(
            Consumer<CleanupStatus> listener,
            CleanupStatus value
    ) {
        try {
            listener.accept(value);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static ScheduledExecutorService newExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "xclip-history-cleanup");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Precomputed cutoffs for one cleanup run.
     *
     * Rows older than the general RECENT cutoff can be deleted without content
     * classification. Classification is performed only for candidates that may
     * match a stricter per-type override.
     */
    private static final class RetentionEvaluationPlan {

        private final long nowMillis;
        private final long generalCutoffExclusive;
        private final long[] typeCutoffsExclusive;
        private final boolean hasTypeRules;

        private RetentionEvaluationPlan(
                long nowMillis,
                long generalCutoffExclusive,
                long[] typeCutoffsExclusive,
                boolean hasTypeRules
        ) {
            this.nowMillis = nowMillis;
            this.generalCutoffExclusive = generalCutoffExclusive;
            this.typeCutoffsExclusive = typeCutoffsExclusive;
            this.hasTypeRules = hasTypeRules;
        }

        private static RetentionEvaluationPlan from(
                HistoryRetentionPolicy policy,
                long nowMillis
        ) {
            long generalCutoff = policy.autoDeleteRecentEnabled()
                    ? cutoff(nowMillis, policy.recentMaxAgeDays())
                    : DISABLED_CUTOFF;

            ClipContentType[] types = ClipContentType.values();
            long[] typeCutoffs = new long[types.length];
            java.util.Arrays.fill(typeCutoffs, DISABLED_CUTOFF);

            boolean hasTypeRules = false;
            for (ClipContentType type : types) {
                int days = policy.maxAgeDaysFor(type);
                if (days > HistoryRetentionPolicy.TYPE_RULE_DISABLED) {
                    typeCutoffs[type.ordinal()] = cutoff(nowMillis, days);
                    hasTypeRules = true;
                }
            }

            return new RetentionEvaluationPlan(
                    nowMillis,
                    generalCutoff,
                    typeCutoffs,
                    hasTypeRules
            );
        }

        private boolean shouldDelete(ClipEntryDao.RetentionCandidate candidate) {
            long lastCopiedAt = candidate.lastCopiedAt();
            if (lastCopiedAt < 0 || lastCopiedAt > nowMillis) return false;

            if (generalCutoffExclusive != DISABLED_CUTOFF
                    && lastCopiedAt < generalCutoffExclusive) {
                return true;
            }
            if (!hasTypeRules) return false;

            ClipContentType type = ClipContentClassifier.classify(candidate.content());
            long typeCutoff = typeCutoffsExclusive[type.ordinal()];
            return typeCutoff != DISABLED_CUTOFF && lastCopiedAt < typeCutoff;
        }

        private static long cutoff(long nowMillis, int days) {
            long ageMillis = days * HistoryRetentionPolicy.MILLIS_PER_DAY;
            return Math.max(0L, nowMillis - ageMillis);
        }
    }

    public enum CleanupTrigger {
        STARTUP,
        SETTINGS_APPLY,
        MANUAL,
        PERIODIC,
        EXIT
    }

    public enum CleanupOutcome {
        NOT_RUN,
        SUCCESS,
        SKIPPED,
        FAILED
    }

    public record CleanupStatus(
            long completedAt,
            CleanupTrigger trigger,
            CleanupOutcome outcome,
            int deletedCount,
            String detail
    ) {
        public CleanupStatus {
            if (completedAt < 0) {
                throw new IllegalArgumentException("completedAt cannot be negative");
            }
            trigger = Objects.requireNonNull(trigger, "trigger");
            outcome = Objects.requireNonNull(outcome, "outcome");
            if (deletedCount < 0) {
                throw new IllegalArgumentException("deletedCount cannot be negative");
            }
            detail = Objects.requireNonNullElse(detail, "").trim();
        }

        public static CleanupStatus notRun() {
            return new CleanupStatus(
                    0,
                    CleanupTrigger.STARTUP,
                    CleanupOutcome.NOT_RUN,
                    0,
                    "Cleanup has not run yet"
            );
        }
    }
}
