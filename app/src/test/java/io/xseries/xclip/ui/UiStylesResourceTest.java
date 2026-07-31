/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiStylesResourceTest {

    private static final List<String> EXPECTED_POPUP = List.of(
            "/ui/theme.css",
            "/ui/controls.css",
            "/ui/popup.css"
    );

    private static final List<String> EXPECTED_SETTINGS = List.of(
            "/ui/theme.css",
            "/ui/controls.css",
            "/ui/dialogs.css"
    );

    @Test
    void profilesKeepDeterministicCascadeOrder() {
        assertEquals(EXPECTED_POPUP, UiStyles.popupResourcePaths());
        assertEquals(EXPECTED_SETTINGS, UiStyles.settingsResourcePaths());
        assertEquals(EXPECTED_SETTINGS, UiStyles.dialogResourcePaths());
    }

    @Test
    void everyActiveStylesheetExistsAndHasBalancedBlocks() throws Exception {
        for (String path : List.of(
                "/ui/theme.css",
                "/ui/controls.css",
                "/ui/popup.css",
                "/ui/dialogs.css"
        )) {
            try (InputStream stream = UiStyles.class.getResourceAsStream(path)) {
                assertNotNull(stream, "Missing resource: " + path);
                String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

                assertFalse(css.isBlank(), "Empty resource: " + path);
                assertTrue(css.contains("SPDX-License-Identifier: GPL-3.0-only"));
                assertEquals(
                        count(css, '{'),
                        count(css, '}'),
                        "Unbalanced CSS blocks: " + path
                );
            }
        }
    }

    @Test
    void legacyStylesheetIsOnlyACompatibilityMarker() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/styles.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains("Compatibility marker"));
            assertEquals(0, count(css, '{'));
            assertEquals(0, count(css, '}'));
        }
    }

    private static int count(String value, char needle) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == needle) count++;
        }
        return count;
    }
}
