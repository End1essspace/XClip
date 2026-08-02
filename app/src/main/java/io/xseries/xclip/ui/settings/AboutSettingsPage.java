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

public final class AboutSettingsPage {

    private AboutSettingsPage() {}

    public static ScrollPane create(String version) {
        VBox section = informationSection(
                "About XClip",
                "Local-first Windows clipboard management.",
                List.of(
                        infoRow("Version", version),
                        infoRow("Author", "XCON | RX"),
                        infoRow("License", "GNU GPL v3.0"),
                        infoRow("Data model", "Local SQLite + config.json"),
                        infoRow("UI contract", "v1.3.0 revision 12")
                )
        );
        return pageScroll(section);
    }
}
