/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TagChipPolicyTest {

    @Test
    void exposesOnlyBoundedVisibleChipsAndStableOverflow() {
        List<ClipTag> tags = List.of(
                new ClipTag(1, "Alpha", 1),
                new ClipTag(2, "Beta", 2),
                new ClipTag(3, "Gamma", 3),
                new ClipTag(4, "Delta", 4),
                new ClipTag(5, "Epsilon", 5)
        );

        TagChipPolicy.Summary summary = TagChipPolicy.summarize(tags);

        assertEquals(
                List.of("Alpha", "Beta", "Gamma"),
                summary.visibleTags().stream().map(ClipTag::name).toList()
        );
        assertEquals(2, summary.hiddenCount());
        assertTrue(summary.hasTags());
    }

    @Test
    void emptyAndInvalidBoundsRemainSafe() {
        assertFalse(TagChipPolicy.summarize(null).hasTags());

        TagChipPolicy.Summary hiddenOnly = TagChipPolicy.summarize(
                List.of(new ClipTag(1, "Alpha", 1)),
                0
        );
        assertTrue(hiddenOnly.visibleTags().isEmpty());
        assertEquals(1, hiddenOnly.hiddenCount());
    }
}
