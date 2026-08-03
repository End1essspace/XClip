
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.config;

import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;
import io.xseries.xclip.domain.privacy.SensitiveContentPolicy;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.retention.HistoryRetentionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void migratesV1JsonAndPersistsCanonicalDuplicateDefaults() throws Exception {
        Path path = tempDir.resolve("config.json");
        Files.writeString(path, """
                {
                  "version": 1,
                  "maxHistory": 900,
                  "minClipLength": 2,
                  "maxClipChars": 500000,
                  "uiClipLimit": 200,
                  "startOnBoot": false,
                  "startMinimized": true,
                  "watcherEnabled": true,
                  "windowX": -1,
                  "windowY": -1,
                  "windowW": 520,
                  "windowH": 420,
                  "windowMaximized": false
                }
                """, StandardCharsets.UTF_8);

        Config loaded = new ConfigService(path).loadOrCreate();
        String persisted = Files.readString(path, StandardCharsets.UTF_8);

        assertEquals(Config.CURRENT_VERSION, loaded.version());
        assertEquals(900, loaded.maxHistory());
        assertTrue(loaded.startMinimized());
        assertEquals(DuplicateBehaviorPolicy.defaults(), loaded.duplicateBehaviorPolicy());
        assertTrue(persisted.contains("\"version\": 5"));
        assertTrue(persisted.contains("duplicateRecentPosition"));
        assertTrue(persisted.contains("MOVE_TO_TOP"));
        assertTrue(persisted.contains("excludedApplications"));
        assertTrue(loaded.excludedApplications().isEmpty());
        assertTrue(persisted.contains("sensitivePaymentCardAction"));
        assertEquals(SensitiveContentPolicy.defaults(), loaded.sensitiveContentPolicy());
    }

    @Test
    void invalidDuplicateValuesNormalizeWithoutDiscardingOtherSettings() throws Exception {
        Path path = tempDir.resolve("config.json");
        Files.writeString(path, """
                {
                  "version": 2,
                  "maxHistory": 1234,
                  "minClipLength": 4,
                  "maxClipChars": 800000,
                  "uiClipLimit": 350,
                  "startOnBoot": false,
                  "startMinimized": false,
                  "watcherEnabled": true,
                  "windowX": 20,
                  "windowY": 30,
                  "windowW": 700,
                  "windowH": 500,
                  "windowMaximized": false,
                  "duplicateRecentPosition": "unknown",
                  "duplicatePinnedPosition": "move-pin-to-top",
                  "duplicateWhitespaceMode": " preserve ",
                  "duplicateCaseSensitivity": "insensitive",
                  "duplicateWindowMillis": -99,
                  "duplicateExactContentMode": false
                }
                """, StandardCharsets.UTF_8);

        Config loaded = new ConfigService(path).loadOrCreate();
        DuplicateBehaviorPolicy policy = loaded.duplicateBehaviorPolicy();

        assertEquals(1234, loaded.maxHistory());
        assertEquals(DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                policy.recentDuplicatePosition());
        assertEquals(DuplicateBehaviorPolicy.PinnedDuplicatePosition.MOVE_PIN_TO_TOP,
                policy.pinnedDuplicatePosition());
        assertEquals(DuplicateBehaviorPolicy.WhitespaceMode.PRESERVE, policy.whitespaceMode());
        assertEquals(DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE,
                policy.caseSensitivity());
        assertEquals(0, policy.duplicateWindowMillis());
        assertFalse(policy.exactContentMode());
    }
    @Test
    void migratesV2PrivacyDefaultsAndSanitizesManualEntries() throws Exception {
        Path path = tempDir.resolve("config.json");
        Files.writeString(path, """
                {
                  "version": 2,
                  "maxHistory": 1200,
                  "minClipLength": 0,
                  "maxClipChars": 500000,
                  "uiClipLimit": 200,
                  "startOnBoot": false,
                  "startMinimized": false,
                  "watcherEnabled": true,
                  "windowX": -1,
                  "windowY": -1,
                  "windowW": 520,
                  "windowH": 420,
                  "windowMaximized": false,
                  "excludedApplications": [
                    " Chrome.EXE ",
                    "C:\\\\Tools\\\\KeePassXC.exe",
                    "*.exe"
                  ]
                }
                """, StandardCharsets.UTF_8);

        Config loaded = new ConfigService(path).loadOrCreate();
        String persisted = Files.readString(path, StandardCharsets.UTF_8);

        assertEquals(Config.CURRENT_VERSION, loaded.version());
        assertEquals(1200, loaded.maxHistory());
        assertEquals(
                java.util.List.of("chrome.exe", "keepassxc.exe"),
                loaded.excludedApplications()
        );
        assertTrue(persisted.contains("\"version\": 5"));
        assertTrue(persisted.contains("chrome.exe"));
        assertTrue(persisted.contains("keepassxc.exe"));
        assertFalse(persisted.contains("*.exe"));
    }

    @Test
    void migratesV3SensitiveDefaultsAndNormalizesUnknownActions() throws Exception {
        Path path = tempDir.resolve("config.json");
        Files.writeString(path, """
                {
                  "version": 3,
                  "maxHistory": 1350,
                  "minClipLength": 0,
                  "maxClipChars": 500000,
                  "uiClipLimit": 200,
                  "startOnBoot": false,
                  "startMinimized": false,
                  "watcherEnabled": true,
                  "windowX": -1,
                  "windowY": -1,
                  "windowW": 520,
                  "windowH": 420,
                  "windowMaximized": false,
                  "excludedApplications": ["notepad.exe"],
                  "sensitivePaymentCardAction": "skip",
                  "sensitiveOneTimeCodeAction": "unknown"
                }
                """, StandardCharsets.UTF_8);

        Config loaded = new ConfigService(path).loadOrCreate();
        String persisted = Files.readString(path, StandardCharsets.UTF_8);

        assertEquals(Config.CURRENT_VERSION, loaded.version());
        assertEquals(1350, loaded.maxHistory());
        assertEquals(java.util.List.of("notepad.exe"), loaded.excludedApplications());
        assertEquals(
                SensitiveContentPolicy.RuleAction.SKIP,
                loaded.sensitiveContentPolicy().paymentCardAction()
        );
        assertEquals(
                SensitiveContentPolicy.RuleAction.CAPTURE,
                loaded.sensitiveContentPolicy().oneTimeCodeAction()
        );
        assertTrue(persisted.contains("\"version\": 5"));
        assertTrue(persisted.contains("\"sensitivePaymentCardAction\": \"SKIP\""));
        assertTrue(persisted.contains("\"sensitiveOneTimeCodeAction\": \"CAPTURE\""));
    }

    @Test
    void migratesV4RetentionDefaultsAndNormalizesManualValues() throws Exception {
        Path path = tempDir.resolve("config.json");
        Files.writeString(path, """
                {
                  "version": 4,
                  "maxHistory": 1450,
                  "minClipLength": 0,
                  "maxClipChars": 500000,
                  "uiClipLimit": 200,
                  "startOnBoot": false,
                  "startMinimized": false,
                  "watcherEnabled": true,
                  "windowX": -1,
                  "windowY": -1,
                  "windowW": 520,
                  "windowH": 420,
                  "windowMaximized": false,
                  "retentionRecentEnabled": true,
                  "retentionRecentDays": 0,
                  "retentionTextDays": -5,
                  "retentionCodeDays": 9000,
                  "retentionUrlDays": 7,
                  "retentionPathDays": 0,
                  "retentionJsonDays": 14,
                  "retentionCommandDays": 2,
                  "clearRecentOnExit": true
                }
                """, StandardCharsets.UTF_8);

        Config loaded = new ConfigService(path).loadOrCreate();
        String persisted = Files.readString(path, StandardCharsets.UTF_8);
        HistoryRetentionPolicy policy = loaded.historyRetentionPolicy();

        assertEquals(Config.CURRENT_VERSION, loaded.version());
        assertEquals(1450, loaded.maxHistory());
        assertTrue(policy.autoDeleteRecentEnabled());
        assertEquals(HistoryRetentionPolicy.DEFAULT_RECENT_MAX_AGE_DAYS,
                policy.recentMaxAgeDays());
        assertEquals(0, policy.maxAgeDaysFor(ClipContentType.TEXT));
        assertEquals(HistoryRetentionPolicy.MAX_MAX_AGE_DAYS,
                policy.maxAgeDaysFor(ClipContentType.CODE));
        assertEquals(7, policy.maxAgeDaysFor(ClipContentType.URL));
        assertEquals(14, policy.maxAgeDaysFor(ClipContentType.JSON));
        assertEquals(2, policy.maxAgeDaysFor(ClipContentType.COMMAND));
        assertTrue(policy.clearRecentOnExit());
        assertTrue(persisted.contains("\"version\": 5"));
        assertTrue(persisted.contains("\"retentionRecentDays\": 30"));
        assertTrue(persisted.contains("\"retentionCodeDays\": 3650"));
    }

}
