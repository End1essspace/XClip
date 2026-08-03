/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsAccessibilityTextTest {

    @Test
    void navigationTextIncludesPagePositionAndPurpose() {
        String label = SettingsAccessibilityText.navigationLabel(
                SettingsPage.HISTORY,
                2,
                SettingsPage.values().length
        );
        String help = SettingsAccessibilityText.navigationHelp(
                SettingsPage.HISTORY
        );

        assertEquals("History settings page, 3 of 9", label);
        assertTrue(help.contains(SettingsPage.HISTORY.description()));
        assertTrue(help.contains("Up, Down, Home, or End"));
    }

    @Test
    void pageContentTextPreservesInformationArchitecture() {
        assertEquals(
                "Privacy settings content",
                SettingsAccessibilityText.pageContentLabel(
                        SettingsPage.PRIVACY
                )
        );
        assertTrue(
                SettingsAccessibilityText.pageContentHelp(
                        SettingsPage.PRIVACY
                ).contains("Tab and Shift+Tab")
        );
        assertTrue(
                SettingsAccessibilityText.pageHeading(
                        SettingsPage.PRIVACY
                ).contains(SettingsPage.PRIVACY.description())
        );
    }

    @Test
    void validationActionExplainsKeyboardActivation() {
        String text = SettingsAccessibilityText.validationAction(
                "History • Max history must be at least 100"
        );

        assertTrue(text.contains("History"));
        assertTrue(text.contains("Press Enter"));
    }

    @Test
    void blankStatusUsesStableFallbackText() {
        assertEquals(
                "Settings operation status",
                SettingsAccessibilityText.operationStatus(" ")
        );
        assertEquals(
                "Settings validation status",
                SettingsAccessibilityText.validationAction(null)
        );
    }

    @Test
    void invalidNavigationPositionIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SettingsAccessibilityText.navigationLabel(
                        SettingsPage.GENERAL,
                        -1,
                        9
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SettingsAccessibilityText.navigationLabel(
                        SettingsPage.GENERAL,
                        9,
                        9
                )
        );
    }
}
