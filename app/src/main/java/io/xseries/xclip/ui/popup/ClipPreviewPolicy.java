/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

/**
 * Bounds expanded clipboard previews so one large clip can never consume the
 * entire history viewport or create a 100,000-character ListCell.
 */
public final class ClipPreviewPolicy {

    public static final int MAX_EXPANDED_LINES = 18;
    public static final int MAX_EXPANDED_CHARS = 8_000;

    private ClipPreviewPolicy() {}

    public static String expandedPreview(String content) {
        if (content == null || content.isEmpty()) return "";

        StringBuilder output = new StringBuilder(
                Math.min(content.length(), MAX_EXPANDED_CHARS + 1)
        );
        int lines = 1;
        boolean truncated = false;

        for (int index = 0; index < content.length(); index++) {
            char value = content.charAt(index);

            if (value == '\n' && lines >= MAX_EXPANDED_LINES) {
                truncated = true;
                break;
            }
            if (output.length() >= MAX_EXPANDED_CHARS) {
                truncated = true;
                break;
            }

            output.append(value);
            if (value == '\n') lines++;
        }

        String result = output.toString().stripTrailing();
        if (truncated && !result.endsWith("…")) result += "…";
        return result;
    }
}
