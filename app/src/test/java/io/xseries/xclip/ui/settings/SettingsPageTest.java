/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SettingsPageTest {

    @Test
    void navigationOrderMatchesTheFrozenProductContract() {
        assertEquals(
                List.of(
                        "GENERAL",
                        "CAPTURE",
                        "HISTORY",
                        "DUPLICATE_BEHAVIOR",
                        "PRIVACY",
                        "APPEARANCE",
                        "SHORTCUTS",
                        "DATA",
                        "ABOUT"
                ),
                Arrays.stream(SettingsPage.values()).map(Enum::name).toList()
        );
    }

    @Test
    void everyPageHasProductFacingCopy() {
        for (SettingsPage page : SettingsPage.values()) {
            assertFalse(page.title().isBlank());
            assertFalse(page.description().isBlank());
        }
    }
}
