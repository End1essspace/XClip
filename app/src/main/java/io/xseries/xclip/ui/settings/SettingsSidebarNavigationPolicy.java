/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

/**
 * Pure index policy for mouse-wheel navigation over the Settings sidebar.
 *
 * One qualifying wheel event advances by exactly one page. The caller keeps
 * keyboard focus where it already is; only the selected Settings page changes.
 */
public final class SettingsSidebarNavigationPolicy {

    public static final double MIN_SCROLL_DELTA = 8.0;

    private SettingsSidebarNavigationPolicy() {}

    public static int targetIndex(
            int currentIndex,
            int pageCount,
            double deltaY
    ) {
        if (pageCount <= 0) {
            throw new IllegalArgumentException("pageCount must be positive");
        }

        int current = Math.max(0, Math.min(pageCount - 1, currentIndex));
        if (!Double.isFinite(deltaY)
                || Math.abs(deltaY) < MIN_SCROLL_DELTA) {
            return current;
        }

        int direction = deltaY < 0.0 ? 1 : -1;
        return Math.max(
                0,
                Math.min(pageCount - 1, current + direction)
        );
    }
}
