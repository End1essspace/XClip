/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import java.util.Objects;

/** One deterministic validation failure for an editable Settings field. */
public record SettingsValidationIssue(SettingsField field, String message) {

    public SettingsValidationIssue {
        field = Objects.requireNonNull(field, "field");
        message = Objects.requireNonNullElse(message, "").trim();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("message is required");
        }
    }

    public SettingsPage page() {
        return field.page();
    }

    public String displayMessage() {
        return field.label() + ": " + message;
    }
}
