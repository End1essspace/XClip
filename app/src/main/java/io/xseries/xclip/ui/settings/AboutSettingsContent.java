/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** Stable product metadata used by the Settings About page. */
public final class AboutSettingsContent {

    public static final String AUTHOR = "End1essspace | RX";
    public static final String LICENSE = "GNU GPL v3.0";
    public static final String REPOSITORY_URL = "https://github.com/End1essspace/XClip";
    public static final String TELEGRAM_URL = "https://t.me/End1essspace";
    public static final String GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.html";
    public static final String THIRD_PARTY_RESOURCE =
            "/META-INF/THIRD-PARTY-NOTICES.txt";

    private AboutSettingsContent() {}

    public static String thirdPartyNotices() {
        try (var stream = AboutSettingsContent.class.getResourceAsStream(
                THIRD_PARTY_RESOURCE
        )) {
            if (stream == null) return "Third-party notices resource is unavailable.";
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            )) {
                return reader.lines().collect(Collectors.joining("\n")).trim();
            }
        } catch (Exception error) {
            return "Third-party notices could not be loaded.";
        }
    }
}
