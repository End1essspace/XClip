/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchAssistOverlayPolicyTest {

    @Test
    void overlayUsesAnchorWidthWithinProductiveBounds() {
        assertEquals(360.0, SearchAssistOverlayPolicy.widthFor(240, 920));
        assertEquals(520.0, SearchAssistOverlayPolicy.widthFor(520, 1366));
        assertEquals(720.0, SearchAssistOverlayPolicy.widthFor(1400, 2560));
    }

    @Test
    void overlayNeverExceedsAvailableOwnerWidth() {
        assertEquals(288.0, SearchAssistOverlayPolicy.widthFor(600, 320));
        assertEquals(0.0, SearchAssistOverlayPolicy.widthFor(600, 20));
    }
}
