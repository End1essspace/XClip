/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsSidebarNavigationPolicyTest {

    @Test
    void wheelDownMovesToNextPageAndWheelUpMovesToPreviousPage() {
        assertEquals(
                4,
                SettingsSidebarNavigationPolicy.targetIndex(3, 9, -40)
        );
        assertEquals(
                2,
                SettingsSidebarNavigationPolicy.targetIndex(3, 9, 40)
        );
    }

    @Test
    void navigationClampsAtFirstAndLastPage() {
        assertEquals(
                0,
                SettingsSidebarNavigationPolicy.targetIndex(0, 9, 40)
        );
        assertEquals(
                8,
                SettingsSidebarNavigationPolicy.targetIndex(8, 9, -40)
        );
    }

    @Test
    void tinyTrackpadNoiseDoesNotSwitchPages() {
        assertEquals(
                3,
                SettingsSidebarNavigationPolicy.targetIndex(3, 9, 2)
        );
        assertEquals(
                3,
                SettingsSidebarNavigationPolicy.targetIndex(3, 9, Double.NaN)
        );
    }

    @Test
    void invalidPageCountIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SettingsSidebarNavigationPolicy.targetIndex(0, 0, -40)
        );
    }
}
