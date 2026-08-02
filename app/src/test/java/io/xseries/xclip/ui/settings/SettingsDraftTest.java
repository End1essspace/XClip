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
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsDraftTest {

    @Test
    void roundTripPreservesEveryEditableSettingAndWindowState() {
        DuplicateBehaviorPolicy duplicate = new DuplicateBehaviorPolicy(
                DuplicateBehaviorPolicy.RecentDuplicatePosition.PRESERVE_EXISTING_POSITION,
                DuplicateBehaviorPolicy.PinnedDuplicatePosition.MOVE_PIN_TO_TOP,
                DuplicateBehaviorPolicy.WhitespaceMode.PRESERVE,
                DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE,
                45_000L,
                false
        );
        SensitiveContentPolicy sensitive = new SensitiveContentPolicy(
                SensitiveContentPolicy.RuleAction.SKIP,
                SensitiveContentPolicy.RuleAction.CAPTURE
        );
        HistoryRetentionPolicy retention = new HistoryRetentionPolicy(
                true,
                21,
                Map.of(
                        ClipContentType.TEXT, 7,
                        ClipContentType.URL, 3
                ),
                true
        );

        Config base = Config.defaults()
                .withWindowState(123, 234, 1110, 720, true)
                .withMaxHistory(1_250)
                .withMinClipLength(4)
                .withMaxClipChars(900_000)
                .withUiClipLimit(350)
                .withWatcherEnabled(false)
                .withStartMinimized(true)
                .withStartOnBoot(true)
                .withDuplicateBehaviorPolicy(duplicate)
                .withExcludedApplications(java.util.List.of("KeePass.exe", "1PASSWORD"))
                .withSensitiveContentPolicy(sensitive)
                .withHistoryRetentionPolicy(retention);

        SettingsDraft draft = SettingsDraft.fromConfig(base);
        Config materialized = draft.toConfig(base);

        assertEquals(base.maxHistory(), materialized.maxHistory());
        assertEquals(base.minClipLength(), materialized.minClipLength());
        assertEquals(base.maxClipChars(), materialized.maxClipChars());
        assertEquals(base.uiClipLimit(), materialized.uiClipLimit());
        assertEquals(base.watcherEnabled(), materialized.watcherEnabled());
        assertEquals(base.startMinimized(), materialized.startMinimized());
        assertEquals(base.startOnBoot(), materialized.startOnBoot());
        assertEquals(base.duplicateBehaviorPolicy(), materialized.duplicateBehaviorPolicy());
        assertEquals(base.excludedApplications(), materialized.excludedApplications());
        assertEquals(base.sensitiveContentPolicy(), materialized.sensitiveContentPolicy());
        assertEquals(base.historyRetentionPolicy(), materialized.historyRetentionPolicy());

        assertEquals(123, materialized.windowX());
        assertEquals(234, materialized.windowY());
        assertEquals(1110, materialized.windowW());
        assertEquals(720, materialized.windowH());
        assertTrue(materialized.windowMaximized());
        assertEquals(base.version(), materialized.version());
    }

    @Test
    void materializationChangesOnlyFieldsOwnedByTheDraft() {
        Config base = Config.defaults().withWindowState(44, 55, 880, 660, false);
        SettingsDraft draft = new SettingsDraft(
                2_000,
                8,
                750_000,
                450,
                false,
                true,
                true,
                DuplicateBehaviorPolicy.defaults(),
                new ExcludedApplicationPolicy(java.util.List.of("secret.exe")),
                new SensitiveContentPolicy(
                        SensitiveContentPolicy.RuleAction.SKIP,
                        SensitiveContentPolicy.RuleAction.SKIP
                ),
                new HistoryRetentionPolicy(false, 30, Map.of(), false)
        );

        Config result = draft.toConfig(base);

        assertEquals(2_000, result.maxHistory());
        assertEquals(8, result.minClipLength());
        assertEquals(750_000, result.maxClipChars());
        assertEquals(450, result.uiClipLimit());
        assertFalse(result.watcherEnabled());
        assertTrue(result.startMinimized());
        assertTrue(result.startOnBoot());
        assertEquals(java.util.List.of("secret.exe"), result.excludedApplications());
        assertEquals(44, result.windowX());
        assertEquals(55, result.windowY());
        assertEquals(880, result.windowW());
        assertEquals(660, result.windowH());
        assertFalse(result.windowMaximized());
    }

    @Test
    void draftRejectsMissingPolicySnapshots() {
        assertThrows(NullPointerException.class, () -> new SettingsDraft(
                800,
                0,
                Config.DEFAULT_MAX_CLIP_CHARS,
                Config.DEFAULT_UI_CLIP_LIMIT,
                true,
                false,
                false,
                null,
                ExcludedApplicationPolicy.defaults(),
                SensitiveContentPolicy.defaults(),
                HistoryRetentionPolicy.defaults()
        ));
    }
}
