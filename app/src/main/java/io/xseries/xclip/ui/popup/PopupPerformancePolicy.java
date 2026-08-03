
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

/**
 * Explicit performance budgets used by the popup data pipeline.
 */
public final class PopupPerformancePolicy {

    public static final int TYPE_FILTER_SCAN_LIMIT = 5_000;
    public static final int PREVIEW_CACHE_CAPACITY = 4_096;
    public static final int CONTENT_TYPE_CACHE_CAPACITY = 8_192;
    public static final int TAG_ASSIGNMENT_CACHE_CAPACITY = 8_192;
    public static final long SEARCH_DEBOUNCE_MS = 150L;

    private PopupPerformancePolicy() {}

    public static int candidateLimit(int uiLimit, boolean typeFilterActive) {
        int safeUiLimit = Math.max(1, uiLimit);
        if (!typeFilterActive) return safeUiLimit;
        return Math.max(safeUiLimit, TYPE_FILTER_SCAN_LIMIT);
    }

    /**
     * Does not retain the original clipboard string.
     */
    public static ContentFingerprint fingerprint(String content) {
        String value = content == null ? "" : content;
        int length = value.length();
        char first = length == 0 ? 0 : value.charAt(0);
        char last = length == 0 ? 0 : value.charAt(length - 1);
        return new ContentFingerprint(length, value.hashCode(), first, last);
    }

    public record ContentFingerprint(
            int length,
            int stableHash,
            char firstChar,
            char lastChar
    ) {}
}
