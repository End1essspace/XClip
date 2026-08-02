
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.model;

/**
 * Clipboard history entry exposed to the UI.
 *
 * title is optional metadata for pinned clips. It never replaces or mutates
 * the original clipboard content used by Copy/Paste.
 *
 * pinOrder is zero-based and meaningful only while the entry is pinned.
 */
public record ClipEntry(
        long id,
        String content,
        String title,
        boolean favorite,
        Integer pinOrder,
        long createdAt
) {
    public boolean hasTitle() {
        return title != null && !title.isBlank();
    }

}
