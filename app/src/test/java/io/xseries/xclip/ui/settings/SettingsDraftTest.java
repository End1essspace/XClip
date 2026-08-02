/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import io.xseries.xclip.config.Config;
import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.privacy.SensitiveContentPolicy;
import io.xseries.xclip.domain.retention.HistoryRetentionPolicy;
import io.xseries.xclip.ui.settings.DuplicateSettingsModel.WindowPreset;
import org.junit.jupiter.api.Test;

import java.util.List;
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
                .withExcludedApplications(List.of("KeePass.exe", "1PASSWORD"))
                .withSensitiveContentPolicy(sensitive)
                .withHistoryRetentionPolicy(retention);

        SettingsDraft draft = SettingsDraft.fromConfig(base);
        SettingsDraftValidation validation = draft.validate();
        Config materialized = validation.toConfig(base);

        assertTrue(validation.valid());
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
    void validationKeepsRawInvalidValuesAndOrdersIssuesByPageAndField() {
        SettingsDraft base = SettingsDraft.fromConfig(Config.defaults());
        SettingsDraft invalid = new SettingsDraft(
                base.general(),
                new SettingsDraft.Capture("", "9999", "5001"),
                new SettingsDraft.History("99"),
                new SettingsDraft.Duplicate(
                        DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                        DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                        DuplicateBehaviorPolicy.WhitespaceMode.NORMALIZE,
                        DuplicateBehaviorPolicy.CaseSensitivity.SENSITIVE,
                        WindowPreset.CUSTOM,
                        "",
                        false
                ),
                new SettingsDraft.Privacy(
                        "bad*.exe",
                        SensitiveContentPolicy.RuleAction.CAPTURE,
                        SensitiveContentPolicy.RuleAction.CAPTURE
                ),
                base.retention()
        );

        SettingsDraftValidation validation = invalid.validate();

        assertFalse(validation.valid());
        assertEquals(SettingsField.MIN_CLIP_LENGTH, validation.issues().get(0).field());
        assertEquals(SettingsPage.CAPTURE, validation.issues().get(0).page());
        assertTrue(validation.invalidPages().contains(SettingsPage.CAPTURE));
        assertTrue(validation.invalidPages().contains(SettingsPage.HISTORY));
        assertTrue(validation.invalidPages().contains(SettingsPage.DUPLICATE_BEHAVIOR));
        assertTrue(validation.invalidPages().contains(SettingsPage.PRIVACY));
        assertThrows(IllegalStateException.class, () -> validation.toConfig(Config.defaults()));
    }

    @Test
    void scopedResetsPreserveUnrelatedDraftSections() {
        SettingsDraft original = SettingsDraft.fromConfig(
                Config.defaults()
                        .withMaxHistory(2_000)
                        .withExcludedApplications(List.of("private.exe"))
                        .withSensitiveContentPolicy(new SensitiveContentPolicy(
                                SensitiveContentPolicy.RuleAction.SKIP,
                                SensitiveContentPolicy.RuleAction.SKIP
                        ))
                        .withHistoryRetentionPolicy(new HistoryRetentionPolicy(
                                true,
                                14,
                                Map.of(ClipContentType.CODE, 3),
                                true
                        ))
        );

        SettingsDraft duplicateReset = original.withDuplicateDefaults();
        assertEquals(original.general(), duplicateReset.general());
        assertEquals(original.capture(), duplicateReset.capture());
        assertEquals(original.history(), duplicateReset.history());
        assertEquals(original.privacy(), duplicateReset.privacy());
        assertEquals(original.retention(), duplicateReset.retention());

        SettingsDraft sensitiveReset = original.withSensitiveDefaults();
        assertEquals(original.privacy().excludedApplications(),
                sensitiveReset.privacy().excludedApplications());
        assertEquals(SensitiveContentPolicy.RuleAction.CAPTURE,
                sensitiveReset.privacy().paymentCardAction());
        assertEquals(SensitiveContentPolicy.RuleAction.CAPTURE,
                sensitiveReset.privacy().oneTimeCodeAction());
        assertEquals(original.retention(), sensitiveReset.retention());

        SettingsDraft retentionReset = original.withRetentionDefaults();
        assertEquals(original.history(), retentionReset.history());
        assertEquals("30", retentionReset.retention().recentDays());
        assertFalse(retentionReset.retention().recentEnabled());
        assertFalse(retentionReset.retention().clearRecentOnExit());
    }

    @Test
    void draftRejectsMissingSectionSnapshots() {
        SettingsDraft valid = SettingsDraft.fromConfig(Config.defaults());
        assertThrows(NullPointerException.class, () -> new SettingsDraft(
                null,
                valid.capture(),
                valid.history(),
                valid.duplicate(),
                valid.privacy(),
                valid.retention()
        ));
    }
}
