/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import java.util.Objects;

/**
 * Stable, testable accessibility text for the Settings shell.
 */
public final class SettingsAccessibilityText {

    private SettingsAccessibilityText() {}

    public static String navigationLabel(
            SettingsPage page,
            int index,
            int total
    ) {
        SettingsPage value = Objects.requireNonNull(page, "page");
        if (index < 0 || total <= 0 || index >= total) {
            throw new IllegalArgumentException("Invalid navigation position");
        }
        return value.title()
                + " settings page, "
                + (index + 1)
                + " of "
                + total;
    }

    public static String navigationHelp(SettingsPage page) {
        SettingsPage value = Objects.requireNonNull(page, "page");
        return value.description()
                + " Use Up, Down, Home, or End to move between Settings pages.";
    }

    public static String pageContentLabel(SettingsPage page) {
        return Objects.requireNonNull(page, "page").title()
                + " settings content";
    }

    public static String pageContentHelp(SettingsPage page) {
        return "Scrollable controls for "
                + Objects.requireNonNull(page, "page").title()
                + ". Use Tab and Shift+Tab to move through controls.";
    }

    public static String pageHeading(SettingsPage page) {
        SettingsPage value = Objects.requireNonNull(page, "page");
        return value.title() + ". " + value.description();
    }

    public static String validationAction(String message) {
        String value = Objects.requireNonNullElse(message, "").trim();
        return value.isEmpty()
                ? "Settings validation status"
                : "Settings validation error. "
                        + value
                        + ". Press Enter to focus the first invalid field.";
    }

    public static String operationStatus(String message) {
        String value = Objects.requireNonNullElse(message, "").trim();
        return value.isEmpty()
                ? "Settings operation status"
                : "Settings status: " + value;
    }
}
