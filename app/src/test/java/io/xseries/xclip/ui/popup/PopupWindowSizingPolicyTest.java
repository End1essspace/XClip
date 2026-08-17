/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PopupWindowSizingPolicyTest {

    @Test
    void normalDisplaysUseComfortableBalancedMinimum() {
        var size = PopupWindowSizingPolicy.minimumForVisualBounds(2560, 1400);

        assertEquals(920.0, size.width());
        assertEquals(560.0, size.height());
        assertEquals(
                PopupResponsivePolicy.LayoutMode.BALANCED,
                PopupResponsivePolicy.layoutMode(
                        PopupWindowSizingPolicy.FUNCTIONAL_MIN_WIDTH
                )
        );
    }

    @Test
    void scaledViewportCapsToAvailableLogicalSpaceWithoutForcingCompact() {
        var size = PopupWindowSizingPolicy.minimumForVisualBounds(911, 480);

        assertEquals(911.0, size.width());
        assertEquals(480.0, size.height());
        assertEquals(
                PopupResponsivePolicy.LayoutMode.BALANCED,
                PopupResponsivePolicy.layoutMode(size.width())
        );
    }

    @Test
    void constrainedViewportCapsMinimumToAvailableLogicalSpace() {
        var size = PopupWindowSizingPolicy.minimumForVisualBounds(680, 450);

        assertEquals(680.0, size.width());
        assertEquals(450.0, size.height());
    }

    @Test
    void invalidVisualBoundsFallBackToFunctionalMinimum() {
        var size = PopupWindowSizingPolicy.minimumForVisualBounds(
                Double.NaN,
                Double.POSITIVE_INFINITY
        );

        assertEquals(920.0, size.width());
        assertEquals(560.0, size.height());
    }
}
