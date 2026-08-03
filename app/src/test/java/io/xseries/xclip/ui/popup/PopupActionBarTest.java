/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PopupActionBarTest {

    @Test
    void choosesFullCompactAndHiddenKeyboardHintsByAvailableWidth() {
        assertEquals(
                "↑↓ Navigate   •   Enter Paste   •   Ctrl+C Copy   •   Del Delete",
                PopupActionBar.hintTextForAvailableWidth(520.0)
        );
        assertEquals(
                "↑↓ Navigate   •   Enter Paste   •   Del Delete",
                PopupActionBar.hintTextForAvailableWidth(330.0)
        );
        assertEquals("", PopupActionBar.hintTextForAvailableWidth(329.9));
        assertEquals("", PopupActionBar.hintTextForAvailableWidth(Double.NaN));
    }
}
