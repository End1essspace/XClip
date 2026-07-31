/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.config;

import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;
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
        assertTrue(persisted.contains("\"version\": 2"));
        assertTrue(persisted.contains("duplicateRecentPosition"));
        assertTrue(persisted.contains("MOVE_TO_TOP"));
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
}
