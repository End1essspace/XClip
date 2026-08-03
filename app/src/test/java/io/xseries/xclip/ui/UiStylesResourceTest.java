
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
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


    @Test
    void duplicateSettingsSelectorsArePackaged() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/dialogs.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains(".duplicate-settings-section"));
            assertTrue(css.contains(".settings-control-wide"));
            assertTrue(css.contains(".settings-override-hint"));
            assertTrue(css.contains(".settings-bottom-bar"));
        }
    }


    @Test
    void privacySettingsSelectorsArePackaged() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/dialogs.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains(".privacy-settings-section"));
            assertTrue(css.contains(".settings-excluded-apps"));
            assertTrue(css.contains(".settings-privacy-hint"));
            assertTrue(css.contains(".text-area.input-error"));
        }
    }


    @Test
    void sensitiveContentSettingsSelectorsArePackaged() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/dialogs.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains(".sensitive-settings-section"));
            assertTrue(css.contains(".settings-sensitive-hint"));
        }
    }

    @Test
    void retentionSettingsSelectorsArePackaged() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/dialogs.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains(".retention-settings-section"));
            assertTrue(css.contains(".settings-retention-hint"));
            assertTrue(css.contains(".settings-cleanup-status"));
        }
    }

    @Test
    void settingsShellSelectorsArePackaged() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/dialogs.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains(".settings-title-bar"));
            assertTrue(css.contains(".settings-navigation"));
            assertTrue(css.contains(".settings-nav-button:selected"));
            assertTrue(css.contains(".settings-page-host"));
            assertTrue(css.contains(".settings-page-scroll"));
            assertTrue(css.contains(".settings-window-control.close"));
        }
    }

    @Test
    void popupComboBoxDropdownThemeIsPackaged() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/popup.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains(".combo-box-popup > .list-view"));
            assertTrue(css.contains(".combo-box-popup > .list-view:focused"));
            assertTrue(css.contains(".combo-box-popup > .list-view .list-cell:filled:hover"));
            assertTrue(css.contains(".combo-box-popup > .list-view .list-cell:filled:selected"));
            assertTrue(css.contains("-fx-background-color: #0C1828"));
            assertTrue(css.contains("-fx-background-color: #17365F"));
        }
    }

    @Test
    void windowControlsExposeFocusRingOnlyForKeyboardNavigation() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/popup.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains(".popup-root .window-control-button:focus-visible"));
            assertFalse(css.contains(".popup-root .window-control-button:focused"));
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
