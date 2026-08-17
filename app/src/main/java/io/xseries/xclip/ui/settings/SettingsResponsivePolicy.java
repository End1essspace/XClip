/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import java.util.List;

/**
 * Pure responsive policy for the Settings window.
 *
 * Width thresholds use JavaFX logical pixels, so Windows display scaling is
 * already accounted for before layout decisions are made.
 */
public final class SettingsResponsivePolicy {

    public static final double DEFAULT_WIDTH = 960;
    public static final double DEFAULT_HEIGHT = 640;
    public static final double MIN_WIDTH = 840;
    public static final double MIN_HEIGHT = 520;

    public static final double COMPACT_MAX_WIDTH = 919;
    public static final double WIDE_MIN_WIDTH = 1180;

    /*
     * Settings is a form surface, not a dashboard. Very wide monitors should
     * add breathing room after the form rather than stretch label/control
     * relationships across the complete viewport.
     */
    public static final double PAGE_CONTENT_MAX_WIDTH = 1120;
    public static final double FORM_TEXT_COLUMN_MIN_WIDTH = 280;
    public static final double FORM_TEXT_COLUMN_PREF_WIDTH = 420;
    public static final double FORM_TEXT_COLUMN_MAX_WIDTH = 460;
    public static final double FORM_CONTROL_COLUMN_MIN_WIDTH = 270;

    private static final double VISUAL_WIDTH_MARGIN = 32;
    private static final double VISUAL_HEIGHT_MARGIN = 24;

    private SettingsResponsivePolicy() {}

    public static LayoutMode modeFor(double sceneWidth) {
        if (!Double.isFinite(sceneWidth) || sceneWidth <= 0) {
            return LayoutMode.STANDARD;
        }
        if (sceneWidth <= COMPACT_MAX_WIDTH) {
            return LayoutMode.COMPACT;
        }
        if (sceneWidth >= WIDE_MIN_WIDTH) {
            return LayoutMode.WIDE;
        }
        return LayoutMode.STANDARD;
    }

    public static WindowSize initialSize(
            double visualWidth,
            double visualHeight
    ) {
        if (!Double.isFinite(visualWidth) || visualWidth <= 0
                || !Double.isFinite(visualHeight) || visualHeight <= 0) {
            return defaultSize();
        }

        double availableWidth = Math.max(
                MIN_WIDTH,
                visualWidth - VISUAL_WIDTH_MARGIN
        );
        double availableHeight = Math.max(
                MIN_HEIGHT,
                visualHeight - VISUAL_HEIGHT_MARGIN
        );

        return new WindowSize(
                Math.min(DEFAULT_WIDTH, availableWidth),
                Math.min(DEFAULT_HEIGHT, availableHeight)
        );
    }

    public static WindowSize defaultSize() {
        return new WindowSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public static List<String> modeStyleClasses() {
        return List.of(
                LayoutMode.COMPACT.styleClass(),
                LayoutMode.STANDARD.styleClass(),
                LayoutMode.WIDE.styleClass()
        );
    }

    public enum LayoutMode {
        COMPACT("settings-compact"),
        STANDARD("settings-standard"),
        WIDE("settings-wide");

        private final String styleClass;

        LayoutMode(String styleClass) {
            this.styleClass = styleClass;
        }

        public String styleClass() {
            return styleClass;
        }
    }

    public record WindowSize(double width, double height) {
        public WindowSize {
            if (!Double.isFinite(width) || width <= 0
                    || !Double.isFinite(height) || height <= 0) {
                throw new IllegalArgumentException(
                        "Settings window size must be finite and positive"
                );
            }
        }
    }
}

