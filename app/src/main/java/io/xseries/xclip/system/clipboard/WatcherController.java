package io.xseries.xclip.system.clipboard;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Owns the ClipboardWatcher lifecycle and allows safe enable/disable without app restart.
 *
 * Guarantees:
 * - enable() snapshots clipboard (via ClipboardWatcher.start()) so current clipboard is NOT ingested
 * - disable() stops background thread immediately (shutdownNow)
 * - idempotent operations (calling enable/disable multiple times is safe)
 */
public final class WatcherController implements AutoCloseable {

    private final ClipboardAccess access;
    private final Consumer<String> onText;
    private final BooleanSupplier isPaused;

    private final Object lock = new Object();
    private final AtomicBoolean enabled = new AtomicBoolean(false);

    private ClipboardWatcher watcher;

    public WatcherController(
            ClipboardAccess access,
            Consumer<String> onText,
            BooleanSupplier isPaused
    ) {
        this.access = Objects.requireNonNull(access);
        this.onText = Objects.requireNonNull(onText);
        this.isPaused = Objects.requireNonNull(isPaused);
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

            ClipboardWatcher w = new ClipboardWatcher(access, onText, isPaused);
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

    /**
     * Convenience: restart watcher (disable -> enable).
     * Useful after config changes.
     */
    public void restart() {
        synchronized (lock) {
            boolean wasEnabled = enabled.get();
            if (wasEnabled) {
                disable();
                enable();
            }
        }
    }

    @Override
    public void close() {
        disable();
    }
}
