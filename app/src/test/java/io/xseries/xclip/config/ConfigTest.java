
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.config;

import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    void defaultSentinelMeansNoPersistedWindowPosition() {
        Config config = Config.defaults();

        assertFalse(config.hasWindowPos());
        assertEquals(-1.0, config.windowX());
        assertEquals(-1.0, config.windowY());
    }

    @Test
    void negativeMultiMonitorCoordinatesRemainValid() {
        Config config = Config.defaults()
                .withWindowState(-1680, 72, 960, 720, true);

        assertTrue(config.hasWindowPos());
        assertEquals(-1680.0, config.windowX());
        assertEquals(72.0, config.windowY());
        assertTrue(config.windowMaximized());
    }

    @Test
    void normalizedWindowSizeCannotFallBelowPopupMinimum() {
        Config config = Config.defaults()
                .withWindowState(20, 30, 100, 120, false);

        assertEquals(Config.MIN_WINDOW_W, config.windowW());
        assertEquals(Config.MIN_WINDOW_H, config.windowH());
    }

    @Test
    void legacyDefaultsMigrateToCurrentDuplicatePolicy() {
        Config config = new Config(1, 800, 0, false, false, true).normalized();

        assertEquals(Config.CURRENT_VERSION, config.version());
        assertEquals(DuplicateBehaviorPolicy.defaults(), config.duplicateBehaviorPolicy());
    }

    @Test
    void duplicatePolicySurvivesUnrelatedWithers() {
        DuplicateBehaviorPolicy policy = new DuplicateBehaviorPolicy(
                DuplicateBehaviorPolicy.RecentDuplicatePosition.PRESERVE_EXISTING_POSITION,
                DuplicateBehaviorPolicy.PinnedDuplicatePosition.MOVE_PIN_TO_TOP,
                DuplicateBehaviorPolicy.WhitespaceMode.PRESERVE,
                DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE,
                15_000,
                false
        );

        Config config = Config.defaults()
                .withDuplicateBehaviorPolicy(policy)
                .withMaxHistory(1_200)
                .withWindowState(-900, 30, 850, 600, true);

        assertEquals(policy, config.duplicateBehaviorPolicy());
        assertEquals(1_200, config.maxHistory());
        assertTrue(config.windowMaximized());
    }
}
