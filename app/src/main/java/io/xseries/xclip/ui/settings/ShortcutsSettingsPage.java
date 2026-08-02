/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.xseries.xclip.ui.settings.SettingsPageSupport.infoRow;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.informationSection;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.pageScroll;

public final class ShortcutsSettingsPage {

    private ShortcutsSettingsPage() {}

    public static ScrollPane create() {
        VBox section = informationSection(
                "Keyboard workflow",
                "The popup shortcut contract remains unchanged by the Settings redesign.",
                List.of(
                        infoRow("Open XClip", "Ctrl+Shift+V"),
                        infoRow("Open Settings", "Ctrl+,"),
                        infoRow("Search", "Ctrl+F / Ctrl+K"),
                        infoRow("Direct Paste", "Enter"),
                        infoRow("Actions", "Shift+F10 / Menu"),
                        infoRow("Focus zones", "F6 / Shift+F6")
                )
        );
        return pageScroll(section);
    }
}
