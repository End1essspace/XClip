/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.config;

import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.privacy.ExcludedApplicationPolicy;
import io.xseries.xclip.domain.privacy.SensitiveContentPolicy;
import io.xseries.xclip.domain.retention.HistoryRetentionPolicy;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable XClip configuration stored in config.json.
 */
public final class Config {

    public static final int CURRENT_VERSION = 5;

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

    private final boolean retentionRecentEnabled;
    private final int retentionRecentDays;
    private final int retentionTextDays;
    private final int retentionCodeDays;
    private final int retentionUrlDays;
    private final int retentionPathDays;
    private final int retentionJsonDays;
    private final int retentionCommandDays;
    private final boolean clearRecentOnExit;

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
                null,
                false,
                HistoryRetentionPolicy.DEFAULT_RECENT_MAX_AGE_DAYS,
                0,
                0,
                0,
                0,
                0,
                0,
                false
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
            String sensitiveOneTimeCodeAction,
            boolean retentionRecentEnabled,
            int retentionRecentDays,
            int retentionTextDays,
            int retentionCodeDays,
            int retentionUrlDays,
            int retentionPathDays,
            int retentionJsonDays,
            int retentionCommandDays,
            boolean clearRecentOnExit
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
        this.retentionRecentEnabled = retentionRecentEnabled;
        this.retentionRecentDays = retentionRecentDays;
        this.retentionTextDays = retentionTextDays;
        this.retentionCodeDays = retentionCodeDays;
        this.retentionUrlDays = retentionUrlDays;
        this.retentionPathDays = retentionPathDays;
        this.retentionJsonDays = retentionJsonDays;
        this.retentionCommandDays = retentionCommandDays;
        this.clearRecentOnExit = clearRecentOnExit;
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
        HistoryRetentionPolicy retentionPolicy = historyRetentionPolicy();

        return fromPolicies(
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
                duplicatePolicy,
                excludedPolicy,
                sensitivePolicy,
                retentionPolicy
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

    public HistoryRetentionPolicy historyRetentionPolicy() {
        int recentDays = clampRecentDays(retentionRecentDays);
        EnumMap<ClipContentType, Integer> typeDays = new EnumMap<>(ClipContentType.class);
        typeDays.put(ClipContentType.TEXT, clampTypeDays(retentionTextDays));
        typeDays.put(ClipContentType.CODE, clampTypeDays(retentionCodeDays));
        typeDays.put(ClipContentType.URL, clampTypeDays(retentionUrlDays));
        typeDays.put(ClipContentType.PATH, clampTypeDays(retentionPathDays));
        typeDays.put(ClipContentType.JSON, clampTypeDays(retentionJsonDays));
        typeDays.put(ClipContentType.COMMAND, clampTypeDays(retentionCommandDays));
        return new HistoryRetentionPolicy(
                retentionRecentEnabled,
                recentDays,
                typeDays,
                clearRecentOnExit
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
    boolean retentionRecentEnabledValue() { return retentionRecentEnabled; }
    int retentionRecentDaysValue() { return retentionRecentDays; }
    int retentionTextDaysValue() { return retentionTextDays; }
    int retentionCodeDaysValue() { return retentionCodeDays; }
    int retentionUrlDaysValue() { return retentionUrlDays; }
    int retentionPathDaysValue() { return retentionPathDays; }
    int retentionJsonDaysValue() { return retentionJsonDays; }
    int retentionCommandDaysValue() { return retentionCommandDays; }
    boolean clearRecentOnExitValue() { return clearRecentOnExit; }

    public boolean hasWindowPos() {
        return Double.isFinite(windowX)
                && Double.isFinite(windowY)
                && !(windowX == -1.0 && windowY == -1.0);
    }

    public Config withMaxHistory(int value) {
        return copy(value, minClipLength, maxClipChars, uiClipLimit, startOnBoot,
                startMinimized, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy(), excludedApplicationPolicy(),
                sensitiveContentPolicy(), historyRetentionPolicy()).normalized();
    }

    public Config withMinClipLength(int value) {
        return copy(maxHistory, value, maxClipChars, uiClipLimit, startOnBoot,
                startMinimized, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy(), excludedApplicationPolicy(),
                sensitiveContentPolicy(), historyRetentionPolicy()).normalized();
    }

    public Config withMaxClipChars(int value) {
        return copy(maxHistory, minClipLength, value, uiClipLimit, startOnBoot,
                startMinimized, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy(), excludedApplicationPolicy(),
                sensitiveContentPolicy(), historyRetentionPolicy()).normalized();
    }

    public Config withUiClipLimit(int value) {
        return copy(maxHistory, minClipLength, maxClipChars, value, startOnBoot,
                startMinimized, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy(), excludedApplicationPolicy(),
                sensitiveContentPolicy(), historyRetentionPolicy()).normalized();
    }

    public Config withStartOnBoot(boolean value) {
        return copy(maxHistory, minClipLength, maxClipChars, uiClipLimit, value,
                startMinimized, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy(), excludedApplicationPolicy(),
                sensitiveContentPolicy(), historyRetentionPolicy()).normalized();
    }

    public Config withStartMinimized(boolean value) {
        return copy(maxHistory, minClipLength, maxClipChars, uiClipLimit, startOnBoot,
                value, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy(), excludedApplicationPolicy(),
                sensitiveContentPolicy(), historyRetentionPolicy()).normalized();
    }

    public Config withWatcherEnabled(boolean value) {
        return copy(maxHistory, minClipLength, maxClipChars, uiClipLimit, startOnBoot,
                startMinimized, value, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy(), excludedApplicationPolicy(),
                sensitiveContentPolicy(), historyRetentionPolicy()).normalized();
    }

    public Config withWindowState(double x, double y, double w, double h, boolean maximized) {
        return copy(maxHistory, minClipLength, maxClipChars, uiClipLimit, startOnBoot,
                startMinimized, watcherEnabled, x, y, w, h, maximized,
                duplicateBehaviorPolicy(), excludedApplicationPolicy(),
                sensitiveContentPolicy(), historyRetentionPolicy()).normalized();
    }

    public Config withWindowState(double x, double y, double w, double h) {
        return withWindowState(x, y, w, h, windowMaximized);
    }

    public Config withDuplicateBehaviorPolicy(DuplicateBehaviorPolicy policy) {
        return copy(maxHistory, minClipLength, maxClipChars, uiClipLimit, startOnBoot,
                startMinimized, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, Objects.requireNonNull(policy, "policy"),
                excludedApplicationPolicy(), sensitiveContentPolicy(),
                historyRetentionPolicy()).normalized();
    }

    public Config withExcludedApplications(List<String> applications) {
        return copy(maxHistory, minClipLength, maxClipChars, uiClipLimit, startOnBoot,
                startMinimized, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy(),
                new ExcludedApplicationPolicy(applications), sensitiveContentPolicy(),
                historyRetentionPolicy()).normalized();
    }

    public Config withSensitiveContentPolicy(SensitiveContentPolicy policy) {
        return copy(maxHistory, minClipLength, maxClipChars, uiClipLimit, startOnBoot,
                startMinimized, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy(), excludedApplicationPolicy(),
                Objects.requireNonNull(policy, "policy"), historyRetentionPolicy()).normalized();
    }

    public Config withHistoryRetentionPolicy(HistoryRetentionPolicy policy) {
        return copy(maxHistory, minClipLength, maxClipChars, uiClipLimit, startOnBoot,
                startMinimized, watcherEnabled, windowX, windowY, windowW, windowH,
                windowMaximized, duplicateBehaviorPolicy(), excludedApplicationPolicy(),
                sensitiveContentPolicy(), Objects.requireNonNull(policy, "policy")).normalized();
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
            SensitiveContentPolicy sensitivePolicy,
            HistoryRetentionPolicy retentionPolicy
    ) {
        return fromPolicies(
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
                duplicatePolicy,
                excludedPolicy,
                sensitivePolicy,
                retentionPolicy
        );
    }

    private static Config fromPolicies(
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
            DuplicateBehaviorPolicy duplicatePolicy,
            ExcludedApplicationPolicy excludedPolicy,
            SensitiveContentPolicy sensitivePolicy,
            HistoryRetentionPolicy retentionPolicy
    ) {
        Map<ClipContentType, Integer> typeDays = retentionPolicy.perTypeMaxAgeDays();
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
                sensitivePolicy.oneTimeCodeAction().name(),
                retentionPolicy.autoDeleteRecentEnabled(),
                retentionPolicy.recentMaxAgeDays(),
                typeDays.getOrDefault(ClipContentType.TEXT, 0),
                typeDays.getOrDefault(ClipContentType.CODE, 0),
                typeDays.getOrDefault(ClipContentType.URL, 0),
                typeDays.getOrDefault(ClipContentType.PATH, 0),
                typeDays.getOrDefault(ClipContentType.JSON, 0),
                typeDays.getOrDefault(ClipContentType.COMMAND, 0),
                retentionPolicy.clearRecentOnExit()
        );
    }

    private static int clampRecentDays(int value) {
        if (value <= 0) return HistoryRetentionPolicy.DEFAULT_RECENT_MAX_AGE_DAYS;
        return Math.min(value, HistoryRetentionPolicy.MAX_MAX_AGE_DAYS);
    }

    private static int clampTypeDays(int value) {
        if (value <= 0) return HistoryRetentionPolicy.TYPE_RULE_DISABLED;
        return Math.min(value, HistoryRetentionPolicy.MAX_MAX_AGE_DAYS);
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
