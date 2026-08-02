/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.clipboard;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Owns the ClipboardWatcher lifecycle and allows safe enable/disable without app restart.
 *
 * Guarantees:
 * - enable() snapshots clipboard (via ClipboardWatcher.start()) so current clipboard is NOT ingested
 * - disable() stops background thread immediately
 * - worker-owned resources are released on the watcher thread before termination
 * - idempotent operations (calling enable/disable multiple times is safe)
 */
public final class WatcherController implements AutoCloseable {

    private final ClipboardAccess access;
    private final Consumer<String> onText;
    private final BooleanSupplier isPaused;
    private final Predicate<String> isCaptureAllowed;
    private final Runnable onWorkerStopped;

    private final Object lock = new Object();
    private final AtomicBoolean enabled = new AtomicBoolean(false);

    private ClipboardWatcher watcher;

    public WatcherController(
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

    public WatcherController(
            ClipboardAccess access,
            Consumer<String> onText,
            BooleanSupplier isPaused,
            Predicate<String> isCaptureAllowed,
            Runnable onWorkerStopped
    ) {
        this.access = Objects.requireNonNull(access);
        this.onText = Objects.requireNonNull(onText);
        this.isPaused = Objects.requireNonNull(isPaused);
        this.isCaptureAllowed = Objects.requireNonNull(isCaptureAllowed);
        this.onWorkerStopped = Objects.requireNonNull(onWorkerStopped);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Enable clipboard capturing (no-op if already enabled).
     */
    public void enable() {
        synchronized (lock) {
            if (enabled.get()) return;

            ClipboardWatcher w = new ClipboardWatcher(
                    access,
                    onText,
                    isPaused,
                    isCaptureAllowed,
                    onWorkerStopped
            );
            w.start();

            watcher = w;
            enabled.set(true);
        }
    }

    /**
     * Disable clipboard capturing (no-op if already disabled).
     */
    public void disable() {
        synchronized (lock) {
            if (!enabled.get()) return;

            try {
                if (watcher != null) watcher.close();
            } catch (Exception ignored) {
            } finally {
                watcher = null;
                enabled.set(false);
            }
        }
    }

    @Override
    public void close() {
        disable();
    }
}
