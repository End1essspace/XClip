/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

/**
 * Stable information architecture for the multi-page Settings shell.
 *
 * Enum order is the canonical left-navigation order and is frozen in the
 * v1.3.0 UI contract.
 */
public enum SettingsPage {
    GENERAL(
            "General",
            "Startup, background capture, and core application behavior."
    ),
    CAPTURE(
            "Capture",
            "Limits applied when clipboard text is observed and prepared."
    ),
    HISTORY(
            "History",
            "Capacity, retention, cleanup status, and exit behavior."
    ),
    DUPLICATE_BEHAVIOR(
            "Duplicate behavior",
            "Matching rules and positioning for repeated clipboard content."
    ),
    PRIVACY(
            "Privacy",
            "Foreground exclusions and local sensitive-content rules."
    ),
    APPEARANCE(
            "Appearance",
            "Current visual system and future explicitly supported options."
    ),
    SHORTCUTS(
            "Shortcuts",
            "Keyboard workflow reference for the popup and Settings."
    ),
    DATA(
            "Data",
            "Local storage, database diagnostics, backup, restore, and ownership actions."
    ),
    ABOUT(
            "About",
            "Version, authorship, license, and local-first product statement."
    );

    private final String title;
    private final String description;

    SettingsPage(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }
}


