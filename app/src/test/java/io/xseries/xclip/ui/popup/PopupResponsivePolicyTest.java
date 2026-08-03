/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PopupResponsivePolicyTest {

    @Test
    void resolutionWidthsSelectDeterministicLayouts() {
        assertEquals(
                PopupResponsivePolicy.LayoutMode.COMPACT,
                PopupResponsivePolicy.layoutMode(500)
        );
        assertEquals(
                PopupResponsivePolicy.LayoutMode.BALANCED,
                PopupResponsivePolicy.layoutMode(760)
        );
        assertEquals(
                PopupResponsivePolicy.LayoutMode.WIDE,
                PopupResponsivePolicy.layoutMode(1366)
        );
        assertEquals(
                PopupResponsivePolicy.LayoutMode.WIDE,
                PopupResponsivePolicy.layoutMode(3840)
        );
    }

    @Test
    void invalidWidthsFailSafeToCompactLayout() {
        assertEquals(
                PopupResponsivePolicy.LayoutMode.COMPACT,
                PopupResponsivePolicy.layoutMode(0)
        );
        assertEquals(
                PopupResponsivePolicy.LayoutMode.COMPACT,
                PopupResponsivePolicy.layoutMode(Double.NaN)
        );
    }

    @Test
    void rowMetadataDropsTimeBeforeContentBecomesTooNarrow() {
        assertEquals(
                PopupResponsivePolicy.RowMetadataMode.COMPACT,
                PopupResponsivePolicy.rowMetadataMode(520)
        );
        assertFalse(PopupResponsivePolicy.showRowTime(699.9));
        assertTrue(PopupResponsivePolicy.showRowTime(700));
        assertTrue(
                PopupResponsivePolicy.rowMetadataWidth(520)
                        < PopupResponsivePolicy.rowMetadataWidth(1366)
        );
    }
}
