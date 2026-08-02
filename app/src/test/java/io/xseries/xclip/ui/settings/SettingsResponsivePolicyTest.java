/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsResponsivePolicyTest {

    @Test
    void widthBreakpointsAreStableAndInclusive() {
        assertEquals(
                SettingsResponsivePolicy.LayoutMode.COMPACT,
                SettingsResponsivePolicy.modeFor(
                        SettingsResponsivePolicy.COMPACT_MAX_WIDTH
                )
        );
        assertEquals(
                SettingsResponsivePolicy.LayoutMode.STANDARD,
                SettingsResponsivePolicy.modeFor(
                        SettingsResponsivePolicy.COMPACT_MAX_WIDTH + 1
                )
        );
        assertEquals(
                SettingsResponsivePolicy.LayoutMode.STANDARD,
                SettingsResponsivePolicy.modeFor(
                        SettingsResponsivePolicy.WIDE_MIN_WIDTH - 1
                )
        );
        assertEquals(
                SettingsResponsivePolicy.LayoutMode.WIDE,
                SettingsResponsivePolicy.modeFor(
                        SettingsResponsivePolicy.WIDE_MIN_WIDTH
                )
        );
    }

    @Test
    void invalidWidthFallsBackToStandardLayout() {
        assertEquals(
                SettingsResponsivePolicy.LayoutMode.STANDARD,
                SettingsResponsivePolicy.modeFor(Double.NaN)
        );
        assertEquals(
                SettingsResponsivePolicy.LayoutMode.STANDARD,
                SettingsResponsivePolicy.modeFor(0)
        );
    }

    @Test
    void initialSizeFitsLogicalVisualBoundsAt1366x768And125Percent() {
        SettingsResponsivePolicy.WindowSize size =
                SettingsResponsivePolicy.initialSize(
                        1366.0 / 1.25,
                        720.0 / 1.25
                );

        assertEquals(960.0, size.width());
        assertEquals(552.0, size.height());
        assertTrue(size.width() <= 1366.0 / 1.25);
        assertTrue(size.height() <= 720.0 / 1.25);
        assertTrue(size.width() >= SettingsResponsivePolicy.MIN_WIDTH);
        assertTrue(size.height() >= SettingsResponsivePolicy.MIN_HEIGHT);
    }

    @Test
    void largeDisplayUsesProductDefaultSize() {
        assertEquals(
                SettingsResponsivePolicy.defaultSize(),
                SettingsResponsivePolicy.initialSize(2560, 1400)
        );
    }

    @Test
    void modeStyleClassesAreCompleteAndUnique() {
        assertEquals(
                SettingsResponsivePolicy.LayoutMode.values().length,
                SettingsResponsivePolicy.modeStyleClasses().size()
        );
        assertEquals(
                SettingsResponsivePolicy.modeStyleClasses().size(),
                new HashSet<>(
                        SettingsResponsivePolicy.modeStyleClasses()
                ).size()
        );
    }
}
