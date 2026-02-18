package io.xseries.xclip.config;

/**
 * Immutable XClip configuration.
 *
 * Stored in JSON (config.json) under {@link AppPaths#dataDir()}.
 */
public final class Config {

    public static final int CURRENT_VERSION = 1;

    private final int version;

    private final int maxHistory;
    private final int minClipLength;

    // Max chars to capture from clipboard text (hard safety guard)
    public static final int DEFAULT_MAX_CLIP_CHARS = 500_000;
    private final int maxClipChars;

    private final boolean startOnBoot;

    private final boolean startMinimized;
    private final boolean watcherEnabled;

    public static final int DEFAULT_WINDOW_W = 520;
    public static final int DEFAULT_WINDOW_H = 420;

    // Window state (persistent)
    private final boolean windowMaximized;
    private final double windowX;
    private final double windowY;
    private final double windowW;
    private final double windowH;

    public Config(
            int version,
            int maxHistory,
            int minClipLength,
            boolean startOnBoot,
            boolean startMinimized,
            boolean watcherEnabled
    ) {
        this(
                version,
                maxHistory,
                minClipLength,
                DEFAULT_MAX_CLIP_CHARS,
                startOnBoot,
                startMinimized,
                watcherEnabled,
                -1,
                -1,
                DEFAULT_WINDOW_W,
                DEFAULT_WINDOW_H,
                false
        );
    }

    public static Config defaults() {
        return new Config(
                CURRENT_VERSION,
                800,
                0,
                false,
                false,
                true
        );
    }

    public Config(
            int version,
            int maxHistory,
            int minClipLength,
            int maxClipChars,
            boolean startOnBoot,
            boolean startMinimized,
            boolean watcherEnabled,
            double windowX,
            double windowY,
            double windowW,
            double windowH,
            boolean windowMaximized
    ) {
        this.version = version;
        this.maxHistory = maxHistory;
        this.minClipLength = minClipLength;
        this.maxClipChars = maxClipChars;
        this.startOnBoot = startOnBoot;
        this.startMinimized = startMinimized;
        this.watcherEnabled = watcherEnabled;
        this.windowX = windowX;
        this.windowY = windowY;
        this.windowW = windowW;
        this.windowH = windowH;
        this.windowMaximized = windowMaximized;
    }

    public Config normalized() {
        int v = version <= 0 ? CURRENT_VERSION : version;

        int mh = maxHistory;
        if (mh < 100) mh = 100;
        if (mh > 50_000) mh = 50_000;

        int ml = minClipLength;
        if (ml < 0) ml = 0;
        if (ml > 10_000) ml = 10_000;

        int mcc = maxClipChars;
        if (mcc < 10_000) mcc = 10_000;
        if (mcc > 5_000_000) mcc = 5_000_000;

        double x = windowX;
        double y = windowY;
        double w = windowW;
        double h = windowH;
        boolean max = windowMaximized;

        // if fields are absent in json, Gson gives 0.0 -> treat as "not set"
        if (w == 0.0 && h == 0.0) {
            x = -1;
            y = -1;
            w = DEFAULT_WINDOW_W;
            h = DEFAULT_WINDOW_H;
            max = false;
        }

        // basic validity
        if (!Double.isFinite(w) || w <= 0) w = DEFAULT_WINDOW_W;
        if (!Double.isFinite(h) || h <= 0) h = DEFAULT_WINDOW_H;
        if (!Double.isFinite(x)) x = -1;
        if (!Double.isFinite(y)) y = -1;

        return new Config(v, mh, ml, mcc, startOnBoot, startMinimized, watcherEnabled, x, y, w, h, max);
    }

    public int version() { return version; }
    public int maxHistory() { return maxHistory; }
    public int minClipLength() { return minClipLength; }
    public int maxClipChars() { return maxClipChars; }
    public boolean startOnBoot() { return startOnBoot; }
    public boolean startMinimized() { return startMinimized; }
    public boolean watcherEnabled() { return watcherEnabled; }

    public double windowX() { return windowX; }
    public double windowY() { return windowY; }
    public double windowW() { return windowW; }
    public double windowH() { return windowH; }
    public boolean windowMaximized() { return windowMaximized; }

    public boolean hasWindowPos() { return windowX >= 0 && windowY >= 0; }

    // Withers (for UI)
    public Config withMaxHistory(int value) {
        return new Config(version, value, minClipLength, maxClipChars, startOnBoot, startMinimized, watcherEnabled, windowX, windowY, windowW, windowH, windowMaximized)
                .normalized();
    }

    public Config withMinClipLength(int value) {
        return new Config(version, maxHistory, value, maxClipChars, startOnBoot, startMinimized, watcherEnabled, windowX, windowY, windowW, windowH, windowMaximized)
                .normalized();
    }

    public Config withMaxClipChars(int value) {
        return new Config(version, maxHistory, minClipLength, value, startOnBoot, startMinimized, watcherEnabled, windowX, windowY, windowW, windowH, windowMaximized)
                .normalized();
    }

    public Config withStartOnBoot(boolean value) {
        // NOTE: fixed bug: previously 'value' was mistakenly passed into maxHistory slot
        return new Config(version, maxHistory, minClipLength, maxClipChars, value, startMinimized, watcherEnabled, windowX, windowY, windowW, windowH, windowMaximized)
                .normalized();
    }

    public Config withStartMinimized(boolean value) {
        return new Config(version, maxHistory, minClipLength, maxClipChars, startOnBoot, value, watcherEnabled, windowX, windowY, windowW, windowH, windowMaximized)
                .normalized();
    }

    public Config withWatcherEnabled(boolean value) {
        return new Config(version, maxHistory, minClipLength, maxClipChars, startOnBoot, startMinimized, value, windowX, windowY, windowW, windowH, windowMaximized)
                .normalized();
    }

    public Config withWindowState(double x, double y, double w, double h, boolean maximized) {
        return new Config(version, maxHistory, minClipLength, maxClipChars, startOnBoot, startMinimized, watcherEnabled, x, y, w, h, maximized)
                .normalized();
    }

    // Backwards-compatible overload (kept for existing call sites)
    public Config withWindowState(double x, double y, double w, double h) {
        return withWindowState(x, y, w, h, windowMaximized);
    }
}
