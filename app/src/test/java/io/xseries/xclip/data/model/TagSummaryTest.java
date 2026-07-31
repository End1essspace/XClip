/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagSummaryTest {

    @Test
    void exposesUsageAndUnusedStateWithoutClipboardContent() {
        TagSummary unused = new TagSummary(1, "  Work  ", 100L, 0);
        TagSummary used = new TagSummary(2, "Review", 200L, 3);

        assertEquals("Work", unused.name());
        assertTrue(unused.unused());
        assertFalse(used.unused());
        assertEquals(3, used.usageCount());
    }

    @Test
    void rejectsInvalidIdentityAndUsage() {
        assertThrows(IllegalArgumentException.class,
                () -> new TagSummary(0, "Work", 0L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new TagSummary(1, "   ", 0L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new TagSummary(1, "Work", 0L, -1));
    }
}
