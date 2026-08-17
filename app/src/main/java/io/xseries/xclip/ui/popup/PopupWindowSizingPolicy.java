/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

/**
 * Screen-aware runtime minimum sizing for the popup.
 *
 * The compact layout remains available only when the current logical visual
 * bounds genuinely cannot fit the normal functional minimum. On ordinary
 * displays the minimum sits comfortably inside the BALANCED layout instead of
 * on its breakpoint, so normal window chrome cannot push the content back into
 * a fully stacked state with almost no useful history area.
 */
public final class PopupWindowSizingPolicy {

    /*
     * Deliberately not tied to COMPACT_MAX_WIDTH + 1.
     *
     * Stage minimum width includes the undecorated shell/borders while the
     * responsive components observe their smaller content width. A minimum on
     * the exact breakpoint can therefore still resolve to COMPACT and stack
     * header, filters, and footer. 920 px leaves a real usability margin inside
     * BALANCED while remaining modest on common 1366+/FHD/QHD displays.
     */
    public static final double FUNCTIONAL_MIN_WIDTH = 920.0;
    public static final double FUNCTIONAL_MIN_HEIGHT = 560.0;

    private PopupWindowSizingPolicy() {}

    public static WindowSize minimumForVisualBounds(
            double visualWidth,
            double visualHeight
    ) {
        return new WindowSize(
                fitToVisualBound(FUNCTIONAL_MIN_WIDTH, visualWidth),
                fitToVisualBound(FUNCTIONAL_MIN_HEIGHT, visualHeight)
        );
    }

    private static double fitToVisualBound(
            double desiredMinimum,
            double visualExtent
    ) {
        if (!Double.isFinite(visualExtent) || visualExtent <= 0.0) {
            return desiredMinimum;
        }
        return Math.min(desiredMinimum, visualExtent);
    }

    public record WindowSize(double width, double height) {
        public WindowSize {
            if (!Double.isFinite(width) || width <= 0.0) {
                throw new IllegalArgumentException("width must be finite and positive");
            }
            if (!Double.isFinite(height) || height <= 0.0) {
                throw new IllegalArgumentException("height must be finite and positive");
            }
        }
    }
}
