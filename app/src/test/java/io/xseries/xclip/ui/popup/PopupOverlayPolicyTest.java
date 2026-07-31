/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PopupOverlayPolicyTest {

    @Test
    void standardDesktopKeepsReferenceQuickHelpSize() {
        PopupOverlayPolicy.OverlaySize size =
                PopupOverlayPolicy.quickHelpViewport(1920, 1040);

        assertEquals(420, size.width());
        assertEquals(450, size.height());
    }

    @Test
    void smallVisualAreaBoundsQuickHelpInsideMonitor() {
        PopupOverlayPolicy.OverlaySize size =
                PopupOverlayPolicy.quickHelpViewport(500, 300);

        assertTrue(size.width() <= 468);
        assertTrue(size.height() <= 204);
        assertTrue(size.width() > 0);
        assertTrue(size.height() > 0);
    }

    @Test
    void pathologicalVisualAreaNeverProducesLargerOverlay() {
        PopupOverlayPolicy.OverlaySize size =
                PopupOverlayPolicy.quickHelpViewport(100, 80);

        assertTrue(size.width() <= 68);
        assertTrue(size.height() <= 1);
    }

}
