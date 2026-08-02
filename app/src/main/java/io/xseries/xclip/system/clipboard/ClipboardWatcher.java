/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.clipboard;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ClipboardWatcher implements AutoCloseable {

    private static final long BASE_POLL_MS = 250;
    private static final long MIN_POLL_MS  = 200;
    private static final long MAX_POLL_MS  = 3000;

    private static final int DEFAULT_MAX_TEXT_LEN = 500_000;
    private static final long WORKER_STOP_TIMEOUT_SECONDS = 2L;

    private final java.util.function.IntSupplier maxTextLen;
    private final ScheduledExecutorService exec;

    private final ClipboardAccess access;
    private final Consumer<String> onText;
    private final BooleanSupplier isPaused;
    private final Predicate<String> isCaptureAllowed;
    private final Runnable onWorkerStopped;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean workerCleanupCompleted = new AtomicBoolean(false);
    private volatile boolean started = false;

    /** Exact snapshot state after the clipboard safety cap. */
    private final ClipboardObservationState observationState =
            new ClipboardObservationState();

    /** Pause transition tracking */
    private volatile boolean wasPaused = false;

    /** Backoff state for clipboard lock errors */
    private int consecutiveFailures = 0;
    /** Idle backoff state (no clipboard changes) */
    private int consecutiveNoChange = 0;

    public ClipboardWatcher(
            ClipboardAccess access,
            Consumer<String> onText,
            BooleanSupplier isPaused,
            Predicate<String> isCaptureAllowed
    ) {
        this(
                access,
                onText,
                isPaused,
                isCaptureAllowed,
                () -> {}
        );
    }

    public ClipboardWatcher(
            ClipboardAccess access,
            Consumer<String> onText,
            BooleanSupplier isPaused,
            Predicate<String> isCaptureAllowed,
            Runnable onWorkerStopped
    ) {
        this.access = Objects.requireNonNull(access);
        this.onText = Objects.requireNonNull(onText);
        this.isPaused = Objects.requireNonNull(isPaused);
        this.maxTextLen = () -> DEFAULT_MAX_TEXT_LEN;
        this.isCaptureAllowed = Objects.requireNonNull(isCaptureAllowed);
        this.onWorkerStopped = Objects.requireNonNull(onWorkerStopped);
        this.exec = newWorkerExecutor();
    }

    public void start() {
        if (started || closed.get()) return;
        started = true;

        // --- STARTUP BARRIER ---
        // Snapshot clipboard so existing content is NOT ingested
        snapshotClipboardIntoLastSeen();

        consecutiveNoChange = 0;
        scheduleNext(BASE_POLL_MS);
    }

    private ScheduledExecutorService newWorkerExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1,
                runnable -> {
                    Thread thread = new Thread(() -> {
                        try {
                            runnable.run();
                        } finally {
                            releaseWorkerResources();
                        }
                    }, "xclip-clipboard-watcher");
                    thread.setDaemon(true);
                    return thread;
                }
        );
        executor.setRemoveOnCancelPolicy(true);
        executor.prestartCoreThread();
        return executor;
    }

    private void scheduleNext(long delayMs) {
        if (closed.get()) return;
        try {
            exec.schedule(
                    this::tickSafely,
                    Math.max(0, delayMs),
                    TimeUnit.MILLISECONDS
            );
        } catch (RejectedExecutionException ignored) {
            // close() won the race with the next scheduling request.
        }
    }

    private void tickSafely() {
        if (closed.get()) return;

        try {
            boolean pausedNow = isPaused.getAsBoolean();

            // ----------------------------
            // Pause barrier
            // ----------------------------
            if (pausedNow) {
                wasPaused = true;
                snapshotClipboardIntoLastSeen();
                consecutiveFailures = 0;

                // reset idle backoff while paused
                consecutiveNoChange = 0;

                scheduleNext(600);
                return;
            }

            if (wasPaused) {
                wasPaused = false;
                snapshotClipboardIntoLastSeen();
                consecutiveFailures = 0;

                consecutiveNoChange = 0;

                scheduleNext(BASE_POLL_MS);
                return;
            }

            // ----------------------------
            // Normal capture
            // ----------------------------
            String raw = access.getTextSafely();
            if (raw == null) {
                consecutiveFailures = 0;
                consecutiveNoChange++;
                scheduleNext(idleDelayMs());
                return;
            }

            String captured = applySafetyCap(raw);
            if (captured == null || captured.isBlank()) {
                consecutiveFailures = 0;
                consecutiveNoChange++;
                scheduleNext(idleDelayMs());
                return;
            }

            // Exact observation belongs to the watcher; duplicate normalization
            // remains in ClipService. Mark before the privacy decision so blocked
            // content cannot be ingested later after a foreground-window switch.
            // This prevents excluded content from being captured later merely because
            // the user switched to another application without changing the clipboard.
            if (!observationState.markIfChanged(captured)) {
                consecutiveFailures = 0;
                consecutiveNoChange++;
                scheduleNext(idleDelayMs());
                return;
            }
            consecutiveNoChange = 0;

            if (captureAllowedFailOpen(isCaptureAllowed, captured)) {
                onText.accept(captured);
            }

            consecutiveFailures = 0;
            scheduleNext(MIN_POLL_MS);

        } catch (Exception e) {
            consecutiveNoChange = 0;

            consecutiveFailures++;
            scheduleNext(backoffDelayMs(consecutiveFailures));
        }
    }

    private void snapshotClipboardIntoLastSeen() {
        try {
            String raw = access.getTextSafely();
            if (raw == null) return;

            String captured = applySafetyCap(raw);
            if (captured == null || captured.isBlank()) return;

            observationState.snapshot(captured);
        } catch (Exception ignored) {
        }
    }

    static boolean captureAllowedFailOpen(
            Predicate<String> capturePolicy,
            String content
    ) {
        try {
            return capturePolicy == null || capturePolicy.test(content);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private long backoffDelayMs(int failures) {
        long d = BASE_POLL_MS * (1L << Math.min(failures, 4));
        return Math.min(Math.max(BASE_POLL_MS, d), MAX_POLL_MS);
    }

    private long idleDelayMs() {
        // Step-based backoff: stable and predictable
        if (consecutiveNoChange <= 10)  return BASE_POLL_MS; // first ~2.5s
        if (consecutiveNoChange <= 30)  return 400;
        if (consecutiveNoChange <= 60)  return 650;
        if (consecutiveNoChange <= 120) return 1000;
        if (consecutiveNoChange <= 240) return 2000;
        return MAX_POLL_MS;
    }

    private String applySafetyCap(String value) {
        if (value == null) return null;

        int cap = maxTextLen.getAsInt();
        if (cap > 0 && value.length() > cap) {
            return value.substring(0, cap);
        }
        return value;
    }

    private void releaseWorkerResources() {
        if (!workerCleanupCompleted.compareAndSet(false, true)) return;
        try {
            onWorkerStopped.run();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;

        exec.shutdownNow();
        try {
            exec.awaitTermination(
                    WORKER_STOP_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
