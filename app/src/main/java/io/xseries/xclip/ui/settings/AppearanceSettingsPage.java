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

public final class AppearanceSettingsPage {

    private AppearanceSettingsPage() {}

    public static ScrollPane create() {
        VBox section = informationSection(
                "Current appearance",
                "The v1.3.0 interface uses the frozen XClip dark theme.",
                List.of(
                        infoRow("Theme", "Dark"),
                        infoRow("Interface stack", "Programmatic JavaFX 21"),
                        infoRow("Theme controls", "No speculative controls in M6.1")
                )
        );
        return pageScroll(section);
    }
}
