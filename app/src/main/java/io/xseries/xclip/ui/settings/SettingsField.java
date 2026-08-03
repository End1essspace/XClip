/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import java.util.Objects;

/**
 * Stable identity for one editable Settings value.
 *
 * The page association lets validation remain independent from JavaFX while
 * the Settings shell can still select and focus the exact failing field.
 */
public enum SettingsField {
    WATCHER_ENABLED(SettingsPage.GENERAL, "Clipboard capture"),
    START_MINIMIZED(SettingsPage.GENERAL, "Start minimized"),
    START_ON_BOOT(SettingsPage.GENERAL, "Start on Windows boot"),

    MIN_CLIP_LENGTH(SettingsPage.CAPTURE, "Min clip length"),
    MAX_CLIP_CHARS(SettingsPage.CAPTURE, "Max clip chars"),
    UI_CLIP_LIMIT(SettingsPage.CAPTURE, "UI clip limit"),

    MAX_HISTORY(SettingsPage.HISTORY, "Max history"),
    RETENTION_RECENT_ENABLED(SettingsPage.HISTORY, "Automatic retention"),
    RETENTION_RECENT_DAYS(SettingsPage.HISTORY, "General retention days"),
    RETENTION_TEXT_DAYS(SettingsPage.HISTORY, "TEXT retention days"),
    RETENTION_CODE_DAYS(SettingsPage.HISTORY, "CODE retention days"),
    RETENTION_URL_DAYS(SettingsPage.HISTORY, "URL retention days"),
    RETENTION_PATH_DAYS(SettingsPage.HISTORY, "PATH retention days"),
    RETENTION_JSON_DAYS(SettingsPage.HISTORY, "JSON retention days"),
    RETENTION_COMMAND_DAYS(SettingsPage.HISTORY, "COMMAND retention days"),
    CLEAR_RECENT_ON_EXIT(SettingsPage.HISTORY, "Clear RECENT on exit"),

    DUPLICATE_RECENT_POSITION(SettingsPage.DUPLICATE_BEHAVIOR, "Recent duplicates"),
    DUPLICATE_PINNED_POSITION(SettingsPage.DUPLICATE_BEHAVIOR, "Pinned duplicates"),
    DUPLICATE_WHITESPACE_MODE(SettingsPage.DUPLICATE_BEHAVIOR, "Whitespace matching"),
    DUPLICATE_CASE_SENSITIVITY(SettingsPage.DUPLICATE_BEHAVIOR, "Case matching"),
    DUPLICATE_WINDOW(SettingsPage.DUPLICATE_BEHAVIOR, "Duplicate time window"),
    DUPLICATE_EXACT_CONTENT(SettingsPage.DUPLICATE_BEHAVIOR, "Exact content"),

    EXCLUDED_APPLICATIONS(SettingsPage.PRIVACY, "Excluded applications"),
    PAYMENT_CARD_ACTION(SettingsPage.PRIVACY, "Payment card numbers"),
    ONE_TIME_CODE_ACTION(SettingsPage.PRIVACY, "One-time codes");

    private final SettingsPage page;
    private final String label;

    SettingsField(SettingsPage page, String label) {
        this.page = Objects.requireNonNull(page, "page");
        this.label = Objects.requireNonNull(label, "label");
    }

    public SettingsPage page() {
        return page;
    }

    public String label() {
        return label;
    }
}
