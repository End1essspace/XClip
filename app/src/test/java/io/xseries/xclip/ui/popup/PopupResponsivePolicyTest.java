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

    @Test
    void expandedHeaderAndFiltersKeepFunctionalResponsiveThresholds() {
        assertFalse(PopupResponsivePolicy.stackHeader(920));
        assertTrue(PopupResponsivePolicy.stackHeader(879));
        assertFalse(PopupResponsivePolicy.stackFilters(920));
        assertTrue(PopupResponsivePolicy.stackFilters(819));
    }

    @Test
    void mirroredFilterGroupsStayBalancedAndClamped() {
        assertEquals(360.0, PopupResponsivePolicy.mirroredFilterGroupWidth(920));
        assertEquals(480.0, PopupResponsivePolicy.mirroredFilterGroupWidth(2560));
        assertTrue(PopupResponsivePolicy.mirroredFilterGroupWidth(1366) > 360.0);
        assertTrue(PopupResponsivePolicy.mirroredFilterGroupWidth(1366) < 480.0);
        assertEquals(
                PopupResponsivePolicy.mirroredFilterGroupWidth(1366),
                PopupResponsivePolicy.mirroredFilterControlWidth(1366) * 2.0
                        + PopupResponsivePolicy.FILTER_ROW_COLUMN_GAP,
                0.0001
        );
    }

    @Test
    void mirroredFilterGroupUsesProductiveWideCap() {
        assertEquals(480.0, PopupResponsivePolicy.FILTER_GROUP_WIDTH_MAX);
        assertEquals(
                480.0,
                PopupResponsivePolicy.mirroredFilterGroupWidth(3840)
        );
    }

}
