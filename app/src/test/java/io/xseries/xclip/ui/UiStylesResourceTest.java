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
    void windowControlsExposeQuietKeyboardFocusWithoutYellowGlow() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/popup.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains(".popup-root .window-control-button:focused"));
            assertTrue(css.contains("/* Mouse/programmatic focus must not inherit JavaFX's default focus halo. */"));
            assertTrue(css.contains(".popup-root .window-control-button:focused {\n    -fx-background-color: transparent;"));
            assertTrue(css.contains(".popup-root .window-control-button:focused:hover"));
            assertTrue(css.contains(".popup-root .window-close-button:focused:hover"));
            assertTrue(css.contains(".popup-root .window-control-button:focus-visible"));
            assertTrue(css.contains("/* Keyboard traversal still receives a quiet, non-accent focus indicator. */"));
            assertFalse(css.contains(".popup-root .window-control-button:focus-visible {\n    -fx-border-color: -x-accent;"));
        }
    }

    @Test
    void scopeTogglesUseSubtleBlueFocusAndReducedHoverIntensity() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/popup.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains(".popup-root .filter-toggle:focus-visible"));
            assertTrue(css.contains("rgba(102, 141, 196, 0.88)"));
            assertTrue(css.contains(".popup-root .filter-toggle:selected:hover"));
            assertTrue(css.contains("rgba(59, 130, 246, 0.16)"));
            assertTrue(css.contains(".popup-root .filter-toggle:hover"));
            assertTrue(css.contains("rgba(255, 255, 255, 0.030)"));
        }
    }


    @Test
    void clipListFocusIsLocalizedToTheSelectedRow() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/popup.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains("/* Keyboard focus belongs to the active row, not to the entire history canvas. */"));
            assertTrue(css.contains(".popup-root .clip-list:focus-visible .list-cell:selected .clip-row-card"));
            assertTrue(css.contains("rgba(102, 160, 236, 0.62)"));
            assertFalse(css.contains(".popup-root .clip-list:focused,\n.quick-help-menu .quick-help-scroll:focused"));
        }
    }

    @Test
    void emptyTagFilterIsReadableAndCannotOpenAnEmptyMenu() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/popup.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains(".filter-tag-wrap:disabled"));
            assertTrue(css.contains(".filter-tag-combo:disabled .list-cell"));
            assertTrue(css.contains(".filter-tag-combo:disabled .arrow-button"));
            assertTrue(css.contains(".popup-root .filter-tag-combo:focus-visible"));
            assertTrue(css.contains(".popup-root .filter-tag-combo:showing"));
        }
    }

    private static int count(String value, char needle) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == needle) count++;
        }
        return count;
    }

    @Test
    void popupSecondaryTextKeepsReadableTimestampAndShortcutTokens() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/popup.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains("M9.2.3 — secondary text readability"));
            assertTrue(css.contains(".popup-root .clip-time"));
            assertTrue(css.contains("-fx-text-fill: #91A3BA;"));
            assertTrue(css.contains("-fx-font-size: 12px;"));
            assertTrue(css.contains("-fx-opacity: 1.0;"));
            assertTrue(css.contains(".popup-root .actions-status"));
            assertTrue(css.contains("-fx-text-fill: #91A6BF;"));
            assertTrue(css.contains("-fx-font-size: 11.5px;"));
        }
    }

    @Test
    void popupSectionHeadersKeepBalancedVerticalRhythm() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/popup.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains("M9.2.4 — section vertical rhythm"));
            assertTrue(css.contains(".popup-root .clip-list .list-cell:section"));
            assertTrue(css.contains("-fx-padding: 11 16 7 16;"));
        }
    }

    @Test
    void popupHeaderUsesOnlyQuietStructuralSeparators() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/popup.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains("M9.2.5 — header separator cleanup"));
            assertTrue(css.contains(".popup-root .top-bar"));
            assertTrue(css.contains("-fx-border-color: transparent;"));
            assertTrue(css.contains("-fx-border-width: 0;"));
            assertTrue(css.contains(".popup-root .filter-bar"));
            assertTrue(css.contains("rgba(65, 86, 113, 0.34)"));
            assertTrue(css.contains(".popup-root .popup-title-bar"));
            assertTrue(css.contains("rgba(65, 86, 113, 0.44)"));
        }
    }

    @Test
    void popupHeaderUsesBalancedMirrorGeometryAndLargerHeaderControls() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/popup.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains("M9.2.8.3 — balanced filter mirror and header scale"));
            assertTrue(css.contains(".popup-root .search-wrap,"));
            assertTrue(css.contains(".popup-root .search-field {\n    -fx-padding: 8 96 8 48;"));
            assertTrue(css.contains(".popup-root .topbar-help"));
            assertTrue(css.contains("-fx-pref-width: 90px;"));
            assertTrue(css.contains(".popup-root .popup-status-group"));
            assertTrue(css.contains("-fx-pref-width: 164px;"));
            assertTrue(css.contains(".popup-root .filter-type-wrap,"));
            assertTrue(css.contains(".popup-root .filter-tag-combo .list-cell"));
        }
    }

    @Test
    void popupHeaderFinalGeometryKeepsSearchFlexibleAndDisabledTagsReadable() throws Exception {
        try (InputStream stream = UiStyles.class.getResourceAsStream("/ui/popup.css")) {
            assertNotNull(stream);
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(css.contains("M9.2.8.4 — header final geometry polish"));
            assertTrue(css.contains(".popup-root .search-area,"));
            assertTrue(css.contains("-fx-max-width: 100000px;"));
            assertTrue(css.contains("-fx-font-size: 14px;"));
            assertTrue(css.contains("-fx-font-size: 13.5px;"));
            assertTrue(css.contains(".popup-root .filter-tag-wrap:disabled"));
            assertTrue(css.contains("-fx-border-color: #2A3C53;"));
            assertTrue(css.contains("-fx-text-fill: #8FA3BA;"));
        }
    }

}
