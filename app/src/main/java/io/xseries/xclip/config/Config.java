/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.config;

import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;
import io.xseries.xclip.domain.privacy.ExcludedApplicationPolicy;
import io.xseries.xclip.domain.privacy.SensitiveContentPolicy;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable XClip configuration stored in config.json.
 */
public final class Config {

    public static final int CURRENT_VERSION = 4;

    private final int version;
    private final int maxHistory;
    private final int minClipLength;

    public static final int DEFAULT_MAX_CLIP_CHARS = 500_000;
    private final int maxClipChars;

    public static final int DEFAULT_UI_CLIP_LIMIT = 200;
    private final int uiClipLimit;

    private final boolean startOnBoot;
    private final boolean startMinimized;
    private final boolean watcherEnabled;

    public static final int MIN_WINDOW_W = 500;
    public static final int MIN_WINDOW_H = 300;
    public static final int DEFAULT_WINDOW_W = 520;
    public static final int DEFAULT_WINDOW_H = 420;

    private final boolean windowMaximized;
    private final double windowX;
    private final double windowY;
    private final double windowW;
    private final double windowH;

    // Stored as stable enum names so malformed/unknown values can fall back safely.
    private final String duplicateRecentPosition;
    private final String duplicatePinnedPosition;
    private final String duplicateWhitespaceMode;
    private final String duplicateCaseSensitivity;
    private final long duplicateWindowMillis;
    private final boolean duplicateExactContentMode;

    private final List<String> excludedApplications;
    private final String sensitivePaymentCardAction;
    private final String sensitiveOneTimeCodeAction;

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
                DEFAULT_UI_CLIP_LIMIT,
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

    public Config(
            int version,
            int maxHistory,
            int minClipLength,
            int maxClipChars,
            int uiClipLimit,
            boolean startOnBoot,
            boolean startMinimized,
            boolean watcherEnabled,
            double windowX,
            double windowY,
            double windowW,
            double windowH,
            boolean windowMaximized
    ) {
        this(
                version,
                maxHistory,
                minClipLength,
                maxClipChars,
                uiClipLimit,
                startOnBoot,
                startMinimized,
                watcherEnabled,
                windowX,
                windowY,
                windowW,
                windowH,
                windowMaximized,
                null,
                null,
                null,
                null,
                DuplicateBehaviorPolicy.UNLIMITED_WINDOW,
                false,
                List.of(),
                null,
                null
        );
    }

    private Config(
            int version,
            int maxHistory,
            int minClipLength,
            int maxClipChars,
            int uiClipLimit,
            boolean startOnBoot,
            boolean startMinimized,
            boolean watcherEnabled,
            double windowX,
            double windowY,
            double windowW,
            double windowH,
            boolean windowMaximized,
            String duplicateRecentPosition,
            String duplicatePinnedPosition,
            String duplicateWhitespaceMode,
            String duplicateCaseSensitivity,
            long duplicateWindowMillis,
            boolean duplicateExactContentMode,
            List<String> excludedApplications,
            String sensitivePaymentCardAction,
            String sensitiveOneTimeCodeAction
    ) {
        this.version = version;
        this.maxHistory = maxHistory;
        this.minClipLength = minClipLength;
        this.maxClipChars = maxClipChars;
        this.uiClipLimit = uiClipLimit;
        this.startOnBoot = startOnBoot;
        this.startMinimized = startMinimized;
        this.watcherEnabled = watcherEnabled;
        this.windowX = windowX;
        this.windowY = windowY;
        this.windowW = windowW;
        this.windowH = windowH;
        this.windowMaximized = windowMaximized;
        this.duplicateRecentPosition = duplicateRecentPosition;
        this.duplicatePinnedPosition = duplicatePinnedPosition;
        this.duplicateWhitespaceMode = duplicateWhitespaceMode;
        this.duplicateCaseSensitivity = duplicateCaseSensitivity;
        this.duplicateWindowMillis = duplicateWindowMillis;
        this.duplicateExactContentMode = duplicateExactContentMode;
        this.excludedApplications = excludedApplications;
        this.sensitivePaymentCardAction = sensitivePaymentCardAction;
        this.sensitiveOneTimeCodeAction = sensitiveOneTimeCodeAction;
    }

    public static Config defaults() {
        return new Config(
                CURRENT_VERSION,
                800,
                0,
                false,
                false,
                true
        ).normalized();
    }

    public Config normalized() {
        int v = version <= CURRENT_VERSION ? CURRENT_VERSION : version;

        int mh = Math.max(100, Math.min(50_000, maxHistory));
        int ml = Math.max(0, Math.min(10_000, minClipLength));

        int mcc = maxClipChars;
        if (mcc < 10_000) mcc = 10_000;
        if (mcc > 5_000_000) mcc = 5_000_000;

        int ucl = uiClipLimit;
        if (ucl <= 0) ucl = DEFAULT_UI_CLIP_LIMIT;
        ucl = Math.max(50, Math.min(5_000, ucl));

        double x = windowX;
        double y = windowY;
        double w = windowW;
        double h = windowH;
        boolean max = windowMaximized;

        if (w == 0.0 && h == 0.0) {
            x = -1;
            y = -1;
            w = DEFAULT_WINDOW_W;
            h = DEFAULT_WINDOW_H;
            max = false;
        }

        if (!Double.isFinite(w) || w <= 0) w = DEFAULT_WINDOW_W;
        if (!Double.isFinite(h) || h <= 0) h = DEFAULT_WINDOW_H;
        w = Math.max(MIN_WINDOW_W, w);
        h = Math.max(MIN_WINDOW_H, h);
        if (!Double.isFinite(x)) x = -1;
        if (!Double.isFinite(y)) y = -1;

        DuplicateBehaviorPolicy duplicatePolicy = duplicateBehaviorPolicy();
        ExcludedApplicationPolicy excludedPolicy = excludedApplicationPolicy();
        SensitiveContentPolicy sensitivePolicy = sensitiveContentPolicy();

        return new Config(
                v,
                mh,
                ml,
                mcc,
                ucl,
                startOnBoot,
                startMinimized,
                watcherEnabled,
                x,
                y,
                w,
                h,
                max,
                duplicatePolicy.recentDuplicatePosition().name(),
                duplicatePolicy.pinnedDuplicatePosition().name(),
                duplicatePolicy.whitespaceMode().name(),
                duplicatePolicy.caseSensitivity().name(),
                duplicatePolicy.duplicateWindowMillis(),
                duplicatePolicy.exactContentMode(),
                excludedPolicy.executableNames(),
                sensitivePolicy.paymentCardAction().name(),
                sensitivePolicy.oneTimeCodeAction().name()
        );
    }

    public int version() { return version; }
    public int maxHistory() { return maxHistory; }
    public int minClipLength() { return minClipLength; }
    public int maxClipChars() { return maxClipChars; }
    public int uiClipLimit() { return uiClipLimit; }
    public boolean startOnBoot() { return startOnBoot; }
    public boolean startMinimized() { return startMinimized; }
    public boolean watcherEnabled() { return watcherEnabled; }
    public double windowX() { return windowX; }
    public double windowY() { return windowY; }
    public double windowW() { return windowW; }
    public double windowH() { return windowH; }
    public boolean windowMaximized() { return windowMaximized; }

    public DuplicateBehaviorPolicy duplicateBehaviorPolicy() {
        DuplicateBehaviorPolicy defaults = DuplicateBehaviorPolicy.defaults();
        long window = duplicateWindowMillis < 0
                ? DuplicateBehaviorPolicy.UNLIMITED_WINDOW
                : duplicateWindowMillis;

        return new DuplicateBehaviorPolicy(
                parseEnum(
                        duplicateRecentPosition,
                        DuplicateBehaviorPolicy.RecentDuplicatePosition.class,
                        defaults.recentDuplicatePosition()
                ),
                parseEnum(
                        duplicatePinnedPosition,
                        DuplicateBehaviorPolicy.PinnedDuplicatePosition.class,
                        defaults.pinnedDuplicatePosition()
                ),
                parseEnum(
                        duplicateWhitespaceMode,
                        DuplicateBehaviorPolicy.WhitespaceMode.class,
                        defaults.whitespaceMode()
                ),
                parseEnum(
                        duplicateCaseSensitivity,
                        DuplicateBehaviorPolicy.CaseSensitivity.class,
                        defaults.caseSensitivity()
                ),
                window,
                duplicateExactContentMode
        );
    }

    String duplicateRecentPositionValue() { return duplicateRecentPosition; }
    String duplicatePinnedPositionValue() { return duplicatePinnedPosition; }
    String duplicateWhitespaceModeValue() { return duplicateWhitespaceMode; }
    String duplicateCaseSensitivityValue() { return duplicateCaseSensitivity; }
    long duplicateWindowMillisValue() { return duplicateWindowMillis; }
    boolean duplicateExactContentModeValue() { return duplicateExactContentMode; }
    List<String> excludedApplicationsValue() { return excludedApplications; }
    String sensitivePaymentCardActionValue() { return sensitivePaymentCardAction; }
    String sensitiveOneTimeCodeActionValue() { return sensitiveOneTimeCodeAction; }

    public ExcludedApplicationPolicy excludedApplicationPolicy() {
        return ExcludedApplicationPolicy.sanitized(excludedApplications);
    }

    public List<String> excludedApplications() {
        return excludedApplicationPolicy().executableNames();
    }

    public SensitiveContentPolicy sensitiveContentPolicy() {
        SensitiveContentPolicy defaults = SensitiveContentPolicy.defaults();
        return new SensitiveContentPolicy(
                parseEnum(
                        sensitivePaymentCardAction,
                        SensitiveContentPolicy.RuleAction.class,
                        defaults.paymentCardAction()
                ),
                parseEnum(
                        sensitiveOneTimeCodeAction,
                        SensitiveContentPolicy.RuleAction.class,
                        defaults.oneTimeCodeAction()
                )
        );
    }

    public boolean hasWindowPos() {
        return Double.isFinite(windowX)
                && Double.isFinite(windowY)
                && !(windowX == -1.0 && windowY == -1.0);
    }

    public Config withMaxHistory(int value) {
        return copy(value, minClipLength, maxClipChars, uiClipLimit, startOnBoot,
                startMinimized, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy()).normalized();
    }

    public Config withMinClipLength(int value) {
        return copy(maxHistory, value, maxClipChars, uiClipLimit, startOnBoot,
                startMinimized, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy()).normalized();
    }

    public Config withMaxClipChars(int value) {
        return copy(maxHistory, minClipLength, value, uiClipLimit, startOnBoot,
                startMinimized, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy()).normalized();
    }

    public Config withUiClipLimit(int value) {
        return copy(maxHistory, minClipLength, maxClipChars, value, startOnBoot,
                startMinimized, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy()).normalized();
    }

    public Config withStartOnBoot(boolean value) {
        return copy(maxHistory, minClipLength, maxClipChars, uiClipLimit, value,
                startMinimized, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy()).normalized();
    }

    public Config withStartMinimized(boolean value) {
        return copy(maxHistory, minClipLength, maxClipChars, uiClipLimit, startOnBoot,
                value, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy()).normalized();
    }

    public Config withWatcherEnabled(boolean value) {
        return copy(maxHistory, minClipLength, maxClipChars, uiClipLimit, startOnBoot,
                startMinimized, value, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy()).normalized();
    }

    public Config withWindowState(double x, double y, double w, double h, boolean maximized) {
        return copy(maxHistory, minClipLength, maxClipChars, uiClipLimit, startOnBoot,
                startMinimized, watcherEnabled, x, y, w, h, maximized,
                duplicateBehaviorPolicy()).normalized();
    }

    public Config withWindowState(double x, double y, double w, double h) {
        return withWindowState(x, y, w, h, windowMaximized);
    }

    public Config withDuplicateBehaviorPolicy(DuplicateBehaviorPolicy policy) {
        DuplicateBehaviorPolicy normalizedPolicy = Objects.requireNonNull(policy, "policy");
        return copy(maxHistory, minClipLength, maxClipChars, uiClipLimit, startOnBoot,
                startMinimized, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, normalizedPolicy).normalized();
    }

    public Config withExcludedApplications(List<String> applications) {
        ExcludedApplicationPolicy excludedPolicy = new ExcludedApplicationPolicy(applications);
        return copy(
                maxHistory,
                minClipLength,
                maxClipChars,
                uiClipLimit,
                startOnBoot,
                startMinimized,
                watcherEnabled,
                windowX,
                windowY,
                windowW,
                windowH,
                windowMaximized,
                duplicateBehaviorPolicy(),
                excludedPolicy
        ).normalized();
    }

    public Config withSensitiveContentPolicy(SensitiveContentPolicy policy) {
        SensitiveContentPolicy sensitivePolicy = Objects.requireNonNull(policy, "policy");
        return copy(
                maxHistory,
                minClipLength,
                maxClipChars,
                uiClipLimit,
                startOnBoot,
                startMinimized,
                watcherEnabled,
                windowX,
                windowY,
                windowW,
                windowH,
                windowMaximized,
                duplicateBehaviorPolicy(),
                excludedApplicationPolicy(),
                sensitivePolicy
        ).normalized();
    }

    private Config copy(
            int maxHistory,
            int minClipLength,
            int maxClipChars,
            int uiClipLimit,
            boolean startOnBoot,
            boolean startMinimized,
            boolean watcherEnabled,
            double windowX,
            double windowY,
            double windowW,
            double windowH,
            boolean windowMaximized,
            DuplicateBehaviorPolicy duplicatePolicy
    ) {
        return new Config(
                version,
                maxHistory,
                minClipLength,
                maxClipChars,
                uiClipLimit,
                startOnBoot,
                startMinimized,
                watcherEnabled,
                windowX,
                windowY,
                windowW,
                windowH,
                windowMaximized,
                duplicatePolicy.recentDuplicatePosition().name(),
                duplicatePolicy.pinnedDuplicatePosition().name(),
                duplicatePolicy.whitespaceMode().name(),
                duplicatePolicy.caseSensitivity().name(),
                duplicatePolicy.duplicateWindowMillis(),
                duplicatePolicy.exactContentMode(),
                excludedApplicationPolicy().executableNames(),
                sensitiveContentPolicy().paymentCardAction().name(),
                sensitiveContentPolicy().oneTimeCodeAction().name()
        );
    }

    private Config copy(
            int maxHistory,
            int minClipLength,
            int maxClipChars,
            int uiClipLimit,
            boolean startOnBoot,
            boolean startMinimized,
            boolean watcherEnabled,
            double windowX,
            double windowY,
            double windowW,
            double windowH,
            boolean windowMaximized,
            DuplicateBehaviorPolicy duplicatePolicy,
            ExcludedApplicationPolicy excludedPolicy
    ) {
        return new Config(
                version,
                maxHistory,
                minClipLength,
                maxClipChars,
                uiClipLimit,
                startOnBoot,
                startMinimized,
                watcherEnabled,
                windowX,
                windowY,
                windowW,
                windowH,
                windowMaximized,
                duplicatePolicy.recentDuplicatePosition().name(),
                duplicatePolicy.pinnedDuplicatePosition().name(),
                duplicatePolicy.whitespaceMode().name(),
                duplicatePolicy.caseSensitivity().name(),
                duplicatePolicy.duplicateWindowMillis(),
                duplicatePolicy.exactContentMode(),
                excludedPolicy.executableNames(),
                sensitiveContentPolicy().paymentCardAction().name(),
                sensitiveContentPolicy().oneTimeCodeAction().name()
        );
    }

    private Config copy(
            int maxHistory,
            int minClipLength,
            int maxClipChars,
            int uiClipLimit,
            boolean startOnBoot,
            boolean startMinimized,
            boolean watcherEnabled,
            double windowX,
            double windowY,
            double windowW,
            double windowH,
            boolean windowMaximized,
            DuplicateBehaviorPolicy duplicatePolicy,
            ExcludedApplicationPolicy excludedPolicy,
            SensitiveContentPolicy sensitivePolicy
    ) {
        return new Config(
                version,
                maxHistory,
                minClipLength,
                maxClipChars,
                uiClipLimit,
                startOnBoot,
                startMinimized,
                watcherEnabled,
                windowX,
                windowY,
                windowW,
                windowH,
                windowMaximized,
                duplicatePolicy.recentDuplicatePosition().name(),
                duplicatePolicy.pinnedDuplicatePosition().name(),
                duplicatePolicy.whitespaceMode().name(),
                duplicatePolicy.caseSensitivity().name(),
                duplicatePolicy.duplicateWindowMillis(),
                duplicatePolicy.exactContentMode(),
                excludedPolicy.executableNames(),
                sensitivePolicy.paymentCardAction().name(),
                sensitivePolicy.oneTimeCodeAction().name()
        );
    }

    private static <E extends Enum<E>> E parseEnum(
            String value,
            Class<E> type,
            E fallback
    ) {
        if (value == null || value.isBlank()) return fallback;

        try {
            String normalized = value.trim()
                    .replace('-', '_')
                    .replace(' ', '_')
                    .toUpperCase(Locale.ROOT);
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
