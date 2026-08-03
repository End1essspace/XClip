/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

/**
 * Central responsive breakpoints for the popup shell.
 *
 * JavaFX reports logical pixels, so the same thresholds work across Windows
 * scaling factors. Components consume this policy independently while keeping
 * one deterministic layout contract.
 */
public final class PopupResponsivePolicy {

    public static final double COMPACT_MAX_WIDTH = 759.0;
    public static final double BALANCED_MAX_WIDTH = 1119.0;
    public static final double ROW_TIME_MIN_WIDTH = 700.0;

    public enum LayoutMode {
        COMPACT,
        BALANCED,
        WIDE
    }

    public enum RowMetadataMode {
        COMPACT,
        BALANCED,
        FULL
    }

    private PopupResponsivePolicy() {}

    public static LayoutMode layoutMode(double width) {
        if (!Double.isFinite(width) || width <= 0.0) return LayoutMode.COMPACT;
        if (width <= COMPACT_MAX_WIDTH) return LayoutMode.COMPACT;
        if (width <= BALANCED_MAX_WIDTH) return LayoutMode.BALANCED;
        return LayoutMode.WIDE;
    }

    public static RowMetadataMode rowMetadataMode(double width) {
        LayoutMode mode = layoutMode(width);
        if (mode == LayoutMode.COMPACT) return RowMetadataMode.COMPACT;
        if (width < 940.0) return RowMetadataMode.BALANCED;
        return RowMetadataMode.FULL;
    }

    public static boolean showRowTime(double width) {
        return Double.isFinite(width) && width >= ROW_TIME_MIN_WIDTH;
    }

    public static double rowMetadataWidth(double width) {
        return switch (rowMetadataMode(width)) {
            case COMPACT -> 118.0;
            case BALANCED -> 168.0;
            case FULL -> 208.0;
        };
    }

    public static double rowLeadingWidth(double width) {
        return switch (rowMetadataMode(width)) {
            case COMPACT -> 40.0;
            case BALANCED -> 44.0;
            case FULL -> 48.0;
        };
    }

    public static boolean stackHeader(double width) {
        return layoutMode(width) == LayoutMode.COMPACT;
    }

    public static boolean stackFilters(double width) {
        return layoutMode(width) == LayoutMode.COMPACT;
    }

    public static boolean stackFooter(double width) {
        return layoutMode(width) == LayoutMode.COMPACT;
    }
}
