/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TagNamePolicyTest {

    @Test
    void normalizesWhitespaceAndBuildsCaseInsensitiveIdentity() {
        TagNamePolicy.NormalizedTagName normalized =
                TagNamePolicy.normalize("  Project\t  WORK  ");

        assertEquals("Project WORK", normalized.displayName());
        assertEquals("project work", normalized.identity());
    }

    @Test
    void rejectsBlankControlAndOverlongNames() {
        assertThrows(IllegalArgumentException.class,
                () -> TagNamePolicy.normalize("  \t\n  "));
        assertThrows(IllegalArgumentException.class,
                () -> TagNamePolicy.normalize("safe\u0000unsafe"));
        assertThrows(IllegalArgumentException.class,
                () -> TagNamePolicy.normalize("x".repeat(TagNamePolicy.MAX_NAME_LENGTH + 1)));
    }

    @Test
    void acceptsMaximumLengthName() {
        String value = "x".repeat(TagNamePolicy.MAX_NAME_LENGTH);
        assertEquals(value, TagNamePolicy.normalize(value).displayName());
    }
}
