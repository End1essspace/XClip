/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.domain.model.ClipContentType;

import java.util.Objects;

/**
 * Builds concise screen-reader descriptions without exposing unbounded
 * clipboard content through the accessibility tree.
 */
public final class PopupAccessibility {

    private static final int MAX_CONTENT_CHARS = 180;

    private PopupAccessibility() {}

    public static String sectionLabel(String title, int count) {
        String safeTitle = Objects.requireNonNullElse(title, "Clipboard").trim();
        int safeCount = Math.max(0, count);
        return safeTitle + " section, " + safeCount + " "
                + (safeCount == 1 ? "clip" : "clips");
    }

    public static String clipLabel(
            ClipEntry entry,
            ClipContentType contentType,
            String timeText,
            boolean selected,
            boolean expanded
    ) {
        Objects.requireNonNull(entry, "entry");

        StringBuilder out = new StringBuilder(256);
        out.append(selected ? "Selected, " : "Not selected, ");
        out.append(entry.favorite() ? "pinned clip" : "recent clip");

        if (entry.hasTitle()) {
            out.append(", title ").append(compact(entry.title(), MAX_CONTENT_CHARS));
        }

        String content = compact(entry.content(), MAX_CONTENT_CHARS);
        if (!content.isBlank()) {
            out.append(", content ").append(content);
        }

        ClipContentType safeType =
                contentType == null ? ClipContentType.TEXT : contentType;
        out.append(", type ").append(safeType.label());

        String safeTime = Objects.requireNonNullElse(timeText, "").trim();
        if (!safeTime.isEmpty()) {
            out.append(", copied ").append(safeTime);
        }

        if (!entry.favorite()) {
            out.append(expanded ? ", preview expanded" : ", preview collapsed");
        }
        return out.toString();
    }

    static String compact(String value, int maxChars) {
        if (value == null || value.isBlank()) return "";

        int limit = Math.max(1, maxChars);
        StringBuilder out = new StringBuilder(Math.min(value.length(), limit + 1));
        boolean pendingSpace = false;
        boolean truncated = false;

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isWhitespace(ch)) {
                pendingSpace = out.length() > 0;
                continue;
            }

            if (pendingSpace && out.length() < limit) out.append(' ');
            pendingSpace = false;

            if (out.length() >= limit) {
                truncated = true;
                break;
            }
            out.append(ch);
        }

        String result = out.toString().trim();
        if (truncated && !result.endsWith("…")) result += "…";
        return result;
    }
}
