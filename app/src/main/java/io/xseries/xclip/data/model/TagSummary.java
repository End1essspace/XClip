/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.model;

import java.util.Objects;

/**
 * Tag metadata exposed to the management surface.
 *
 * usageCount is the number of current clip assignments. Clipboard content is
 * deliberately not part of this model.
 */
public record TagSummary(
        long id,
        String name,
        long createdAt,
        int usageCount
) {
    public TagSummary {
        if (id <= 0) throw new IllegalArgumentException("id must be positive");
        name = Objects.requireNonNullElse(name, "").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("name is required");
        if (usageCount < 0) throw new IllegalArgumentException("usageCount cannot be negative");
    }

    public boolean unused() {
        return usageCount == 0;
    }
}
