/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchAssistOverlayResourceTest {

    @Test
    void searchAssistFloatsOutsideHeaderLayout() throws Exception {
        String popupWindow = Files.readString(Path.of(
                "src/main/java/io/xseries/xclip/ui/PopupWindow.java"
        ));
        String assist = Files.readString(Path.of(
                "src/main/java/io/xseries/xclip/ui/popup/SearchAssistBar.java"
        ));

        assertTrue(popupWindow.contains(
                "searchAssistPopup.getContent().setAll(searchAssistBar);"
        ));
        assertTrue(popupWindow.contains("new VBox(searchWrap)"));
        assertTrue(popupWindow.contains("SearchAssistOverlayPolicy.widthFor("));
        assertFalse(popupWindow.contains(
                "new VBox(5, searchWrap, searchAssistBar)"
        ));
        assertFalse(assist.contains("setMinHeight(28)"));
    }
}
