/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsResponsiveResourceTest {

    @Test
    void dialogsCssContainsResponsiveAndKeyboardFocusContracts()
            throws Exception {
        try (InputStream stream = SettingsResponsiveResourceTest.class
                .getResourceAsStream("/ui/dialogs.css")) {
            assertNotNull(stream);
            String css = new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            assertTrue(css.contains(".settings-root.settings-compact"));
            assertTrue(css.contains(".settings-root.settings-wide"));
            assertTrue(css.contains(".settings-validation-status:focused"));
            assertTrue(css.contains(".settings-feedback"));
            assertTrue(css.contains(".settings-grid"));
        }
    }

    @Test
    void settingsSidebarWheelKeepsSelectionAndKeyboardFocusSynchronized() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Path.of(
                        "src/main/java/io/xseries/xclip/ui/SettingsWindow.java"
                )
        );

        assertTrue(source.contains(
                "focusNavigationButton(pages, targetIndex);"
        ));
        assertFalse(source.contains(
                "if (targetIndex != currentIndex) {\n            selectPage(pages[targetIndex]);"
        ));
    }

    @Test
    void settingsWideCompositionKeepsFormBoundedAndFooterPrimaryRightmost()
            throws Exception {
        String supportSource = java.nio.file.Files.readString(
                java.nio.file.Path.of(
                        "src/main/java/io/xseries/xclip/ui/settings/SettingsPageSupport.java"
                )
        );
        String gridSource = java.nio.file.Files.readString(
                java.nio.file.Path.of(
                        "src/main/java/io/xseries/xclip/ui/settings/SettingsResponsiveGrid.java"
                )
        );
        String windowSource = java.nio.file.Files.readString(
                java.nio.file.Path.of(
                        "src/main/java/io/xseries/xclip/ui/SettingsWindow.java"
                )
        );

        assertTrue(supportSource.contains(
                "content.setMaxWidth(SettingsResponsivePolicy.PAGE_CONTENT_MAX_WIDTH);"
        ));
        assertTrue(supportSource.contains(
                "scroll.setFocusTraversable(false);"
        ));
        assertTrue(supportSource.contains(
                "canvas.getStyleClass().add(\"settings-page-canvas\");"
        ));
        assertTrue(gridSource.contains(
                "SettingsResponsivePolicy.FORM_TEXT_COLUMN_MAX_WIDTH"
        ));
        assertTrue(windowSource.contains(
                "new HBox(10, cancelBtn, applyBtn)"
        ));

        try (InputStream stream = SettingsResponsiveResourceTest.class
                .getResourceAsStream("/ui/dialogs.css")) {
            assertNotNull(stream);
            String css = new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
            assertTrue(css.contains(
                    "M9.2.11 — Settings wide composition and footer hierarchy"
            ));
            assertTrue(css.contains(".settings-page-canvas"));
            assertTrue(css.contains(
                    ".settings-root .settings-page-scroll:focused"
            ));
            assertTrue(css.contains(".settings-root .settings-footer-cancel"));
            assertTrue(css.contains(".settings-root .settings-footer-apply"));
            assertTrue(css.contains("-fx-pref-width: 112px;"));
            assertTrue(css.contains("-fx-text-fill: #8D9AAC;"));
        }
    }

}
