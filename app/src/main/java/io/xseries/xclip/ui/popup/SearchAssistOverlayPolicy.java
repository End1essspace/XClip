/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

/**
 * Pure sizing policy for the floating advanced-search assistance surface.
 */
public final class SearchAssistOverlayPolicy {

    public static final double MIN_WIDTH = 360.0;
    public static final double MAX_WIDTH = 720.0;
    public static final double OWNER_HORIZONTAL_MARGIN = 32.0;
    public static final double VERTICAL_GAP = 6.0;

    private SearchAssistOverlayPolicy() {}

    public static double widthFor(double anchorWidth, double ownerWidth) {
        double safeOwner = Double.isFinite(ownerWidth) && ownerWidth > 0.0
                ? ownerWidth
                : MAX_WIDTH + OWNER_HORIZONTAL_MARGIN;
        double available = Math.max(0.0, safeOwner - OWNER_HORIZONTAL_MARGIN);
        if (available == 0.0) return 0.0;

        double safeAnchor = Double.isFinite(anchorWidth) && anchorWidth > 0.0
                ? anchorWidth
                : MIN_WIDTH;
        double target = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, safeAnchor));
        return Math.min(target, available);
    }
}
