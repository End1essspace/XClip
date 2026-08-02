/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextValuesTest {

    @Test
    void requireNonBlankTrimsAndRejectsMissingText() {
        assertEquals("value", TextValues.requireNonBlank("  value  ", "field"));

        IllegalArgumentException blank = assertThrows(
                IllegalArgumentException.class,
                () -> TextValues.requireNonBlank(" \t\n ", "field")
        );
        assertEquals("field is required", blank.getMessage());

        assertThrows(
                IllegalArgumentException.class,
                () -> TextValues.requireNonBlank(null, "field")
        );
    }

    @Test
    void collapseWhitespacePreservesCharactersAndNormalizesRuns() {
        assertEquals(
                "alpha beta gamma",
                TextValues.collapseWhitespace(" \talpha \n beta   gamma\r\n")
        );
        assertEquals("", TextValues.collapseWhitespace(""));
        assertThrows(NullPointerException.class, () -> TextValues.collapseWhitespace(null));
    }

    @Test
    void detectsWindowsAndUnixLineBreaks() {
        assertTrue(TextValues.containsLineBreak("alpha\nbeta"));
        assertTrue(TextValues.containsLineBreak("alpha\rbeta"));
        assertFalse(TextValues.containsLineBreak("alpha beta"));
        assertThrows(NullPointerException.class, () -> TextValues.containsLineBreak(null));
    }
}
