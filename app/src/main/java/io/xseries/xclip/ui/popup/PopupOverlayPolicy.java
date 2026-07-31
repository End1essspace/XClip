/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

/**
 * Bounds popup overlays to the current monitor's logical visual area.
 */
public final class PopupOverlayPolicy {

    private static final double DEFAULT_HELP_WIDTH = 420.0;
    private static final double DEFAULT_HELP_HEIGHT = 450.0;
    private static final double MIN_HELP_WIDTH = 280.0;
    private static final double MIN_HELP_HEIGHT = 180.0;
    private static final double HORIZONTAL_MARGIN = 32.0;
    private static final double VERTICAL_MARGIN = 96.0;

    private PopupOverlayPolicy() {}

    public static OverlaySize quickHelpViewport(
            double visualWidth,
            double visualHeight
    ) {
        double safeWidth = finitePositive(visualWidth, DEFAULT_HELP_WIDTH + HORIZONTAL_MARGIN);
        double safeHeight = finitePositive(visualHeight, DEFAULT_HELP_HEIGHT + VERTICAL_MARGIN);

        double availableWidth = Math.max(1.0, safeWidth - HORIZONTAL_MARGIN);
        double availableHeight = Math.max(1.0, safeHeight - VERTICAL_MARGIN);

        double width = clamp(
                availableWidth,
                Math.min(MIN_HELP_WIDTH, availableWidth),
                DEFAULT_HELP_WIDTH
        );
        double height = clamp(
                availableHeight,
                Math.min(MIN_HELP_HEIGHT, availableHeight),
                DEFAULT_HELP_HEIGHT
        );
        return new OverlaySize(width, height);
    }

    private static double finitePositive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (maximum < minimum) return maximum;
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record OverlaySize(double width, double height) {}
}
