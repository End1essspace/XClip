/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipTag;

import java.util.List;

/**
 * Bounds tag metadata rendered inside a virtualized clip row.
 *
 * Tags arrive in deterministic DAO order. Only the first few are rendered as
 * chips; the remainder is represented by a compact +N chip.
 */
public final class TagChipPolicy {

    public static final int MAX_VISIBLE_CHIPS = 3;

    private TagChipPolicy() {}

    public static Summary summarize(List<ClipTag> tags) {
        return summarize(tags, MAX_VISIBLE_CHIPS);
    }

    static Summary summarize(List<ClipTag> tags, int maxVisible) {
        if (tags == null || tags.isEmpty() || maxVisible <= 0) {
            return new Summary(List.of(), Math.max(0, tags == null ? 0 : tags.size()));
        }

        List<ClipTag> copy = List.copyOf(tags);
        int visibleCount = Math.min(copy.size(), maxVisible);
        return new Summary(
                copy.subList(0, visibleCount),
                copy.size() - visibleCount
        );
    }

    public record Summary(List<ClipTag> visibleTags, int hiddenCount) {
        public Summary {
            visibleTags = visibleTags == null ? List.of() : List.copyOf(visibleTags);
            hiddenCount = Math.max(0, hiddenCount);
        }

        public boolean hasTags() {
            return !visibleTags.isEmpty() || hiddenCount > 0;
        }
    }
}
