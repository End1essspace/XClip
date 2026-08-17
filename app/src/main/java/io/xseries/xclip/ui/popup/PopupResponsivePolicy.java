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

    /*
     * The enlarged labeled header needs more room than the old icon-only shell.
     * Ordinary installations are protected by the 920px functional minimum;
     * these lower thresholds exist for genuinely constrained logical displays.
     */
    public static final double HEADER_STACK_MAX_WIDTH = 879.0;
    public static final double FILTER_STACK_MAX_WIDTH = 819.0;

    /*
     * The filter row now mirrors its left and right control masses.
     * Scope owns one bounded group on the left; the combined type/tag region
     * matches that width on the right; the remaining monitor width becomes a
     * single centered breathing track.
     */
    public static final double FILTER_GROUP_WIDTH_MIN = 360.0;
    public static final double FILTER_GROUP_WIDTH_MAX = 480.0;
    public static final double FILTER_GROUP_WIDTH_FRACTION = 0.31;
    public static final double FILTER_ROW_HORIZONTAL_PADDING = 32.0;
    public static final double FILTER_ROW_COLUMN_GAP = 10.0;
    public static final double FILTER_CONTROL_WIDTH_MIN = 170.0;

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

    public static double mirroredFilterGroupWidth(double width) {
        if (!Double.isFinite(width) || width <= 0.0) {
            return FILTER_GROUP_WIDTH_MIN;
        }
        double usableWidth = Math.max(0.0, width - FILTER_ROW_HORIZONTAL_PADDING);
        double candidate = usableWidth * FILTER_GROUP_WIDTH_FRACTION;
        return clamp(candidate, FILTER_GROUP_WIDTH_MIN, FILTER_GROUP_WIDTH_MAX);
    }

    public static double mirroredFilterControlWidth(double width) {
        double candidate = (mirroredFilterGroupWidth(width) - FILTER_ROW_COLUMN_GAP) / 2.0;
        return Math.max(FILTER_CONTROL_WIDTH_MIN, candidate);
    }

    public static boolean stackHeader(double width) {
        return !Double.isFinite(width)
                || width <= HEADER_STACK_MAX_WIDTH;
    }

    public static boolean stackFilters(double width) {
        return !Double.isFinite(width)
                || width <= FILTER_STACK_MAX_WIDTH;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static boolean stackFooter(double width) {
        return layoutMode(width) == LayoutMode.COMPACT;
    }
}



