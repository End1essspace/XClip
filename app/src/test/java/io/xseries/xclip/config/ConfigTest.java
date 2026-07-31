/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.config;

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
}
