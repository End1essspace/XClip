/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AboutSettingsContentTest {

    @Test
    void projectLinksUseExplicitHttpsDestinations() {
        assertTrue(AboutSettingsContent.REPOSITORY_URL.startsWith("https://"));
        assertTrue(AboutSettingsContent.TELEGRAM_URL.startsWith("https://"));
        assertTrue(AboutSettingsContent.GPL_URL.startsWith("https://"));
    }

    @Test
    void bundledNoticesExposeLucideAttribution() {
        String notices = AboutSettingsContent.thirdPartyNotices();

        assertTrue(notices.contains("Lucide"));
        assertTrue(notices.contains("ISC"));
    }
}
