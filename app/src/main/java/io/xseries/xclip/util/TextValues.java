/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.util;

import java.util.Objects;

/**
 * Small application-wide primitives for text validation and normalization.
 *
 * Domain-specific policies remain in their owning classes. This helper only
 * centralizes operations that previously had byte-for-byte equivalent copies
 * across domain, system, and UI code.
 */
public final class TextValues {

    private TextValues() {}

    public static String requireNonBlank(String value, String field) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    public static String collapseWhitespace(String value) {
        if (value.isEmpty()) return "";

        StringBuilder out = new StringBuilder(value.length());
        boolean pendingSpace = false;

        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (Character.isWhitespace(ch)) {
                pendingSpace = out.length() > 0;
                continue;
            }
            if (pendingSpace) out.append(' ');
            pendingSpace = false;
            out.append(ch);
        }

        return out.toString().trim();
    }

    public static boolean containsLineBreak(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }
}
