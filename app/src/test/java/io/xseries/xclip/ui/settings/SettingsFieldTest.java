/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SettingsFieldTest {

    @Test
    void everyEditableFieldHasStablePageAndProductLabel() {
        for (SettingsField field : SettingsField.values()) {
            assertNotNull(field.page());
            assertFalse(field.label().isBlank());
        }
    }
}
