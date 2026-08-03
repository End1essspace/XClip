/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.tray;

/**
 * Runtime registration state for the fixed Ctrl+Shift+V global hotkey.
 */
public enum HotkeyRegistrationStatus {
    NOT_STARTED("Not started", "The tray and global hotkey have not been initialized yet.", false),
    REGISTERING("Registering", "Windows hotkey registration is in progress.", false),
    ACTIVE("Active", "Ctrl+Shift+V is registered and ready.", false),
    CONFLICT("Conflict", "Another application already owns Ctrl+Shift+V.", true),
    UNSUPPORTED("Unavailable", "Global hotkeys are available only in the supported Windows tray runtime.", true),
    FAILED("Failed", "Windows rejected or interrupted global hotkey registration.", true),
    STOPPED("Stopped", "The global hotkey is no longer registered.", false);

    private final String label;
    private final String detail;
    private final boolean problem;

    HotkeyRegistrationStatus(String label, String detail, boolean problem) {
        this.label = label;
        this.detail = detail;
        this.problem = problem;
    }

    public String label() {
        return label;
    }

    public String detail() {
        return detail;
    }

    public boolean problem() {
        return problem;
    }
}
