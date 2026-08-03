/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.model;

/**
 * High-level popup scope used before optional content-type filtering.
 *
 * A null favoriteFilter means that both pinned and recent clips are included.
 */
public enum ClipViewScope {
    ALL("All", null),
    PINNED("Pinned", Boolean.TRUE),
    RECENT("Recent", Boolean.FALSE);

    private final String label;
    private final Boolean favoriteFilter;

    ClipViewScope(String label, Boolean favoriteFilter) {
        this.label = label;
        this.favoriteFilter = favoriteFilter;
    }

    public String label() {
        return label;
    }

    public Boolean favoriteFilter() {
        return favoriteFilter;
    }
}
