package io.xseries.xclip.system.clipboard;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ClipboardWatcher implements AutoCloseable {

    private static final long BASE_POLL_MS = 250;
    private static final long MIN_POLL_MS  = 200;
    private static final long MAX_POLL_MS  = 3000;

    private static final int MAX_TEXT_LEN = 50_000;

    private final ScheduledExecutorService exec =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "xclip-clipboard-watcher");
                t.setDaemon(true);
                return t;
            });

    private final ClipboardAccess access;
    private final Consumer<String> onText;
    private final BooleanSupplier isPaused;

    private volatile boolean closed  = false;
    private volatile boolean started = false;

    /** Normalized snapshot of last seen clipboard value */
    private volatile String lastSeenNorm = null;

    /** Pause transition tracking */
    private volatile boolean wasPaused = false;

    /** Backoff state for clipboard lock errors */
    private int consecutiveFailures = 0;
    /** Idle backoff state (no clipboard changes) */
    private int consecutiveNoChange = 0;

    public ClipboardWatcher(
            ClipboardAccess access,
            Consumer<String> onText,
            BooleanSupplier isPaused
    ) {
        this.access   = Objects.requireNonNull(access);
        this.onText   = Objects.requireNonNull(onText);
        this.isPaused = Objects.requireNonNull(isPaused);
    }

    public void start() {
        if (started) return;
        started = true;

        // --- STARTUP BARRIER ---
        // Snapshot clipboard so existing content is NOT ingested
        snapshotClipboardIntoLastSeen();

        consecutiveNoChange = 0;
        scheduleNext(BASE_POLL_MS);
    }

    private void scheduleNext(long delayMs) {
        if (closed) return;
        exec.schedule(this::tickSafely, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
    }

    private void tickSafely() {
        if (closed) return;

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

            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                consecutiveFailures = 0;
                consecutiveNoChange++;
                scheduleNext(idleDelayMs());
                return;
            }

            if (trimmed.length() > MAX_TEXT_LEN) {
                consecutiveFailures = 0;
                consecutiveNoChange++;
                scheduleNext(idleDelayMs());
                return;
            }

            String norm = normalize(trimmed);
            if (norm.isEmpty()) {
                consecutiveFailures = 0;
                consecutiveNoChange++;
                scheduleNext(idleDelayMs());
                return;
            }

            // -------- WATCHER BARRIER --------
            if (norm.equals(lastSeenNorm)) {
                consecutiveFailures = 0;
                consecutiveNoChange++;
                scheduleNext(idleDelayMs());
                return;
            }

            // update immediately to prevent races
            lastSeenNorm = norm;

            // reset idle backoff on real change
            consecutiveNoChange = 0;

            onText.accept(trimmed);

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

            String trimmed = raw.trim();
            if (trimmed.isEmpty()) return;
            if (trimmed.length() > MAX_TEXT_LEN) return;

            lastSeenNorm = normalize(trimmed);
        } catch (Exception ignored) {
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

    /**
     * Same normalization logic as ClipService
     */
    private String normalize(String s) {
        int n = s.length();
        StringBuilder sb = new StringBuilder(n);
        boolean inWs = false;

        for (int i = 0; i < n; i++) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) {
                inWs = true;
                continue;
            }
            if (inWs && sb.length() > 0) sb.append(' ');
            sb.append(ch);
            inWs = false;
        }
        return sb.toString().trim();
    }

    @Override
    public void close() {
        closed = true;
        exec.shutdownNow();
    }
}
