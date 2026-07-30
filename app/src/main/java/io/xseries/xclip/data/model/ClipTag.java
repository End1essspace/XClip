/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.model;

/**
 * Persistent user-defined tag.
 *
 * Tag names are normalized by TagDao before storage. The original display
 * casing of the first created tag is preserved until the user renames it.
 */
public record ClipTag(
        long id,
        String name,
        long createdAt
) {}
