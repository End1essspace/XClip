/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import io.xseries.xclip.config.Config;
import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.privacy.ExcludedApplicationPolicy;
import io.xseries.xclip.domain.privacy.SensitiveContentPolicy;
import io.xseries.xclip.domain.retention.HistoryRetentionPolicy;
import io.xseries.xclip.ui.settings.DuplicateSettingsModel.WindowPreset;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Complete raw Settings form snapshot independent from JavaFX controls.
 *
 * Text-backed numeric fields intentionally stay as text until validation so an
 * empty or out-of-range editor value remains representable in the draft. A
 * Config can be materialized only through a successful validation result.
 */
public record SettingsDraft(
        General general,
        Capture capture,
        History history,
        Duplicate duplicate,
        Privacy privacy,
        Retention retention
) {
    public SettingsDraft {
        general = Objects.requireNonNull(general, "general");
        capture = Objects.requireNonNull(capture, "capture");
        history = Objects.requireNonNull(history, "history");
        duplicate = Objects.requireNonNull(duplicate, "duplicate");
        privacy = Objects.requireNonNull(privacy, "privacy");
        retention = Objects.requireNonNull(retention, "retention");
    }

    public static SettingsDraft fromConfig(Config config) {
        Config value = Objects.requireNonNull(config, "config").normalized();
        DuplicateBehaviorPolicy duplicatePolicy = value.duplicateBehaviorPolicy();
        HistoryRetentionPolicy retentionPolicy = value.historyRetentionPolicy();

        return new SettingsDraft(
                new General(
                        value.watcherEnabled(),
                        value.startMinimized(),
                        value.startOnBoot()
                ),
                new Capture(
                        integerText(value.minClipLength()),
                        integerText(value.maxClipChars()),
                        integerText(value.uiClipLimit())
                ),
                new History(integerText(value.maxHistory())),
                Duplicate.fromPolicy(duplicatePolicy),
                new Privacy(
                        value.excludedApplicationPolicy().toMultilineText(),
                        value.sensitiveContentPolicy().paymentCardAction(),
                        value.sensitiveContentPolicy().oneTimeCodeAction()
                ),
                Retention.fromPolicy(retentionPolicy)
        );
    }

    public SettingsDraft withDuplicateDefaults() {
        return new SettingsDraft(
                general,
                capture,
                history,
                Duplicate.fromPolicy(DuplicateBehaviorPolicy.defaults()),
                privacy,
                retention
        );
    }

    public SettingsDraft withSensitiveDefaults() {
        SensitiveContentPolicy defaults = SensitiveContentPolicy.defaults();
        return new SettingsDraft(
                general,
                capture,
                history,
                duplicate,
                new Privacy(
                        privacy.excludedApplications(),
                        defaults.paymentCardAction(),
                        defaults.oneTimeCodeAction()
                ),
                retention
        );
    }

    /** Resets only age/type/exit retention controls and preserves max history. */
    public SettingsDraft withRetentionDefaults() {
        return new SettingsDraft(
                general,
                capture,
                history,
                duplicate,
                privacy,
                Retention.fromPolicy(HistoryRetentionPolicy.defaults())
        );
    }

    public SettingsDraftValidation validate() {
        List<SettingsValidationIssue> issues = new ArrayList<>();

        Integer minClipLength = parseInteger(
                capture.minClipLength(),
                SettingsField.MIN_CLIP_LENGTH,
                0,
                10_000,
                issues
        );
        Integer maxClipChars = parseInteger(
                capture.maxClipChars(),
                SettingsField.MAX_CLIP_CHARS,
                10_000,
                5_000_000,
                issues
        );
        Integer uiClipLimit = parseInteger(
                capture.uiClipLimit(),
                SettingsField.UI_CLIP_LIMIT,
                50,
                5_000,
                issues
        );

        Integer maxHistory = parseInteger(
                history.maxHistory(),
                SettingsField.MAX_HISTORY,
                100,
                50_000,
                issues
        );

        Integer retentionRecentDays = parseInteger(
                retention.recentDays(),
                SettingsField.RETENTION_RECENT_DAYS,
                HistoryRetentionPolicy.MIN_MAX_AGE_DAYS,
                HistoryRetentionPolicy.MAX_MAX_AGE_DAYS,
                issues
        );
        Integer retentionTextDays = parseInteger(
                retention.textDays(),
                SettingsField.RETENTION_TEXT_DAYS,
                HistoryRetentionPolicy.TYPE_RULE_DISABLED,
                HistoryRetentionPolicy.MAX_MAX_AGE_DAYS,
                issues
        );
        Integer retentionCodeDays = parseInteger(
                retention.codeDays(),
                SettingsField.RETENTION_CODE_DAYS,
                HistoryRetentionPolicy.TYPE_RULE_DISABLED,
                HistoryRetentionPolicy.MAX_MAX_AGE_DAYS,
                issues
        );
        Integer retentionUrlDays = parseInteger(
                retention.urlDays(),
                SettingsField.RETENTION_URL_DAYS,
                HistoryRetentionPolicy.TYPE_RULE_DISABLED,
                HistoryRetentionPolicy.MAX_MAX_AGE_DAYS,
                issues
        );
        Integer retentionPathDays = parseInteger(
                retention.pathDays(),
                SettingsField.RETENTION_PATH_DAYS,
                HistoryRetentionPolicy.TYPE_RULE_DISABLED,
                HistoryRetentionPolicy.MAX_MAX_AGE_DAYS,
                issues
        );
        Integer retentionJsonDays = parseInteger(
                retention.jsonDays(),
                SettingsField.RETENTION_JSON_DAYS,
                HistoryRetentionPolicy.TYPE_RULE_DISABLED,
                HistoryRetentionPolicy.MAX_MAX_AGE_DAYS,
                issues
        );
        Integer retentionCommandDays = parseInteger(
                retention.commandDays(),
                SettingsField.RETENTION_COMMAND_DAYS,
                HistoryRetentionPolicy.TYPE_RULE_DISABLED,
                HistoryRetentionPolicy.MAX_MAX_AGE_DAYS,
                issues
        );

        DuplicateBehaviorPolicy duplicatePolicy = validateDuplicate(issues);
        ExcludedApplicationPolicy excludedPolicy = validateExcludedApplications(issues);
        SensitiveContentPolicy sensitivePolicy = validateSensitivePolicy(issues);

        HistoryRetentionPolicy retentionPolicy = null;
        if (retentionRecentDays != null
                && retentionTextDays != null
                && retentionCodeDays != null
                && retentionUrlDays != null
                && retentionPathDays != null
                && retentionJsonDays != null
                && retentionCommandDays != null) {
            EnumMap<ClipContentType, Integer> typeDays =
                    new EnumMap<>(ClipContentType.class);
            typeDays.put(ClipContentType.TEXT, retentionTextDays);
            typeDays.put(ClipContentType.CODE, retentionCodeDays);
            typeDays.put(ClipContentType.URL, retentionUrlDays);
            typeDays.put(ClipContentType.PATH, retentionPathDays);
            typeDays.put(ClipContentType.JSON, retentionJsonDays);
            typeDays.put(ClipContentType.COMMAND, retentionCommandDays);
            retentionPolicy = new HistoryRetentionPolicy(
                    retention.recentEnabled(),
                    retentionRecentDays,
                    typeDays,
                    retention.clearRecentOnExit()
            );
        }

        if (!issues.isEmpty()) {
            return new SettingsDraftValidation(issues, null);
        }

        return new SettingsDraftValidation(
                List.of(),
                new ValidatedValues(
                        Objects.requireNonNull(maxHistory),
                        Objects.requireNonNull(minClipLength),
                        Objects.requireNonNull(maxClipChars),
                        Objects.requireNonNull(uiClipLimit),
                        general.watcherEnabled(),
                        general.startMinimized(),
                        general.startOnBoot(),
                        Objects.requireNonNull(duplicatePolicy),
                        Objects.requireNonNull(excludedPolicy),
                        Objects.requireNonNull(sensitivePolicy),
                        Objects.requireNonNull(retentionPolicy)
                )
        );
    }

    /** Convenience API retained for callers that already know the draft is valid. */
    public Config toConfig(Config base) {
        return validate().toConfig(base);
    }

    private DuplicateBehaviorPolicy validateDuplicate(
            List<SettingsValidationIssue> issues
    ) {
        boolean enumValuesPresent = true;
        enumValuesPresent &= requireValue(
                duplicate.recentPosition(),
                SettingsField.DUPLICATE_RECENT_POSITION,
                issues
        );
        enumValuesPresent &= requireValue(
                duplicate.pinnedPosition(),
                SettingsField.DUPLICATE_PINNED_POSITION,
                issues
        );
        enumValuesPresent &= requireValue(
                duplicate.whitespaceMode(),
                SettingsField.DUPLICATE_WHITESPACE_MODE,
                issues
        );
        enumValuesPresent &= requireValue(
                duplicate.caseSensitivity(),
                SettingsField.DUPLICATE_CASE_SENSITIVITY,
                issues
        );
        enumValuesPresent &= requireValue(
                duplicate.windowPreset(),
                SettingsField.DUPLICATE_WINDOW,
                issues
        );
        if (!enumValuesPresent) return null;

        try {
            return DuplicateSettingsModel.toPolicy(
                    duplicate.recentPosition(),
                    duplicate.pinnedPosition(),
                    duplicate.whitespaceMode(),
                    duplicate.caseSensitivity(),
                    duplicate.windowPreset(),
                    duplicate.customWindowMillis(),
                    duplicate.exactContentMode()
            );
        } catch (IllegalArgumentException error) {
            issues.add(new SettingsValidationIssue(
                    SettingsField.DUPLICATE_WINDOW,
                    messageOr(error, "invalid value")
            ));
            return null;
        }
    }

    private ExcludedApplicationPolicy validateExcludedApplications(
            List<SettingsValidationIssue> issues
    ) {
        try {
            return ExcludedApplicationPolicy.fromMultilineText(
                    privacy.excludedApplications()
            );
        } catch (IllegalArgumentException error) {
            issues.add(new SettingsValidationIssue(
                    SettingsField.EXCLUDED_APPLICATIONS,
                    messageOr(error, "invalid executable name")
            ));
            return null;
        }
    }

    private SensitiveContentPolicy validateSensitivePolicy(
            List<SettingsValidationIssue> issues
    ) {
        boolean valuesPresent = true;
        valuesPresent &= requireValue(
                privacy.paymentCardAction(),
                SettingsField.PAYMENT_CARD_ACTION,
                issues
        );
        valuesPresent &= requireValue(
                privacy.oneTimeCodeAction(),
                SettingsField.ONE_TIME_CODE_ACTION,
                issues
        );
        if (!valuesPresent) return null;

        return new SensitiveContentPolicy(
                privacy.paymentCardAction(),
                privacy.oneTimeCodeAction()
        );
    }

    private static Integer parseInteger(
            String raw,
            SettingsField field,
            int min,
            int max,
            List<SettingsValidationIssue> issues
    ) {
        String value = Objects.requireNonNullElse(raw, "").trim();
        if (value.isEmpty()) {
            issues.add(new SettingsValidationIssue(field, "required"));
            return null;
        }
        if (!value.chars().allMatch(Character::isDigit)) {
            issues.add(new SettingsValidationIssue(field, "must contain digits only"));
            return null;
        }

        final int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException error) {
            issues.add(new SettingsValidationIssue(field, "number is too large"));
            return null;
        }

        if (parsed < min || parsed > max) {
            issues.add(new SettingsValidationIssue(
                    field,
                    "must be between " + min + " and " + max
            ));
            return null;
        }
        return parsed;
    }

    private static boolean requireValue(
            Object value,
            SettingsField field,
            List<SettingsValidationIssue> issues
    ) {
        if (value != null) return true;
        issues.add(new SettingsValidationIssue(field, "selection is required"));
        return false;
    }

    private static String messageOr(Throwable error, String fallback) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }

    private static String integerText(int value) {
        return Integer.toString(value);
    }

    public record General(
            boolean watcherEnabled,
            boolean startMinimized,
            boolean startOnBoot
    ) {}

    public record Capture(
            String minClipLength,
            String maxClipChars,
            String uiClipLimit
    ) {
        public Capture {
            minClipLength = Objects.requireNonNullElse(minClipLength, "");
            maxClipChars = Objects.requireNonNullElse(maxClipChars, "");
            uiClipLimit = Objects.requireNonNullElse(uiClipLimit, "");
        }
    }

    public record History(String maxHistory) {
        public History {
            maxHistory = Objects.requireNonNullElse(maxHistory, "");
        }
    }

    public record Duplicate(
            DuplicateBehaviorPolicy.RecentDuplicatePosition recentPosition,
            DuplicateBehaviorPolicy.PinnedDuplicatePosition pinnedPosition,
            DuplicateBehaviorPolicy.WhitespaceMode whitespaceMode,
            DuplicateBehaviorPolicy.CaseSensitivity caseSensitivity,
            WindowPreset windowPreset,
            String customWindowMillis,
            boolean exactContentMode
    ) {
        public Duplicate {
            customWindowMillis = Objects.requireNonNullElse(
                    customWindowMillis,
                    ""
            );
        }

        public static Duplicate fromPolicy(DuplicateBehaviorPolicy policy) {
            DuplicateBehaviorPolicy value = Objects.requireNonNull(policy, "policy");
            return new Duplicate(
                    value.recentDuplicatePosition(),
                    value.pinnedDuplicatePosition(),
                    value.whitespaceMode(),
                    value.caseSensitivity(),
                    DuplicateSettingsModel.presetFor(value.duplicateWindowMillis()),
                    DuplicateSettingsModel.customWindowText(
                            value.duplicateWindowMillis()
                    ),
                    value.exactContentMode()
            );
        }
    }

    public record Privacy(
            String excludedApplications,
            SensitiveContentPolicy.RuleAction paymentCardAction,
            SensitiveContentPolicy.RuleAction oneTimeCodeAction
    ) {
        public Privacy {
            excludedApplications = Objects.requireNonNullElse(
                    excludedApplications,
                    ""
            );
        }
    }

    public record Retention(
            boolean recentEnabled,
            String recentDays,
            String textDays,
            String codeDays,
            String urlDays,
            String pathDays,
            String jsonDays,
            String commandDays,
            boolean clearRecentOnExit
    ) {
        public Retention {
            recentDays = Objects.requireNonNullElse(recentDays, "");
            textDays = Objects.requireNonNullElse(textDays, "");
            codeDays = Objects.requireNonNullElse(codeDays, "");
            urlDays = Objects.requireNonNullElse(urlDays, "");
            pathDays = Objects.requireNonNullElse(pathDays, "");
            jsonDays = Objects.requireNonNullElse(jsonDays, "");
            commandDays = Objects.requireNonNullElse(commandDays, "");
        }

        public static Retention fromPolicy(HistoryRetentionPolicy policy) {
            HistoryRetentionPolicy value = Objects.requireNonNull(policy, "policy");
            return new Retention(
                    value.autoDeleteRecentEnabled(),
                    integerText(value.recentMaxAgeDays()),
                    integerText(value.maxAgeDaysFor(ClipContentType.TEXT)),
                    integerText(value.maxAgeDaysFor(ClipContentType.CODE)),
                    integerText(value.maxAgeDaysFor(ClipContentType.URL)),
                    integerText(value.maxAgeDaysFor(ClipContentType.PATH)),
                    integerText(value.maxAgeDaysFor(ClipContentType.JSON)),
                    integerText(value.maxAgeDaysFor(ClipContentType.COMMAND)),
                    value.clearRecentOnExit()
            );
        }
    }

    /** Fully parsed values that can safely cross the persistence/runtime boundary. */
    public record ValidatedValues(
            int maxHistory,
            int minClipLength,
            int maxClipChars,
            int uiClipLimit,
            boolean watcherEnabled,
            boolean startMinimized,
            boolean startOnBoot,
            DuplicateBehaviorPolicy duplicateBehaviorPolicy,
            ExcludedApplicationPolicy excludedApplicationPolicy,
            SensitiveContentPolicy sensitiveContentPolicy,
            HistoryRetentionPolicy historyRetentionPolicy
    ) {
        public ValidatedValues {
            duplicateBehaviorPolicy = Objects.requireNonNull(
                    duplicateBehaviorPolicy,
                    "duplicateBehaviorPolicy"
            );
            excludedApplicationPolicy = Objects.requireNonNull(
                    excludedApplicationPolicy,
                    "excludedApplicationPolicy"
            );
            sensitiveContentPolicy = Objects.requireNonNull(
                    sensitiveContentPolicy,
                    "sensitiveContentPolicy"
            );
            historyRetentionPolicy = Objects.requireNonNull(
                    historyRetentionPolicy,
                    "historyRetentionPolicy"
            );
        }

        public Config toConfig(Config base) {
            Config value = Objects.requireNonNull(base, "base").normalized();
            return value
                    .withMaxHistory(maxHistory)
                    .withMinClipLength(minClipLength)
                    .withMaxClipChars(maxClipChars)
                    .withUiClipLimit(uiClipLimit)
                    .withWatcherEnabled(watcherEnabled)
                    .withStartMinimized(startMinimized)
                    .withStartOnBoot(startOnBoot)
                    .withDuplicateBehaviorPolicy(duplicateBehaviorPolicy)
                    .withExcludedApplications(
                            excludedApplicationPolicy.executableNames()
                    )
                    .withSensitiveContentPolicy(sensitiveContentPolicy)
                    .withHistoryRetentionPolicy(historyRetentionPolicy);
        }
    }
}
