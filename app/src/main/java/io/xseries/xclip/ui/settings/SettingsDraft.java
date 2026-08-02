/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import io.xseries.xclip.config.Config;
import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;
import io.xseries.xclip.domain.privacy.ExcludedApplicationPolicy;
import io.xseries.xclip.domain.privacy.SensitiveContentPolicy;
import io.xseries.xclip.domain.retention.HistoryRetentionPolicy;

import java.util.Objects;

/**
 * Complete editable Settings snapshot independent from JavaFX controls.
 *
 * Window geometry and future non-editable configuration fields are deliberately
 * inherited from the supplied base Config when the draft is materialized.
 */
public record SettingsDraft(
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
    public SettingsDraft {
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

    public static SettingsDraft fromConfig(Config config) {
        Config value = Objects.requireNonNull(config, "config").normalized();
        return new SettingsDraft(
                value.maxHistory(),
                value.minClipLength(),
                value.maxClipChars(),
                value.uiClipLimit(),
                value.watcherEnabled(),
                value.startMinimized(),
                value.startOnBoot(),
                value.duplicateBehaviorPolicy(),
                value.excludedApplicationPolicy(),
                value.sensitiveContentPolicy(),
                value.historyRetentionPolicy()
        );
    }

    /**
     * Applies every editable field to one base snapshot while preserving window
     * state, schema markers, and configuration fields not owned by Settings.
     */
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
                .withExcludedApplications(excludedApplicationPolicy.executableNames())
                .withSensitiveContentPolicy(sensitiveContentPolicy)
                .withHistoryRetentionPolicy(historyRetentionPolicy);
    }
}
