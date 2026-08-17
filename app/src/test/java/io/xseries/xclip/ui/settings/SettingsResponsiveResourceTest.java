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

}
