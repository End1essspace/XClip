/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import static io.xseries.xclip.ui.settings.SettingsPageSupport.addSettingRow;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.pageScroll;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.section;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.settingsGrid;

public final class GeneralSettingsPage {

    private GeneralSettingsPage() {}

    public static ScrollPane create(
            CheckBox watcherEnabled,
            CheckBox startMinimized,
            CheckBox startOnBoot
    ) {
        GridPane grid = settingsGrid();
        int row = 0;
        row = addSettingRow(
                grid,
                row,
                "Clipboard capture",
                "Enable or pause background clipboard monitoring.",
                watcherEnabled
        );
        row = addSettingRow(
                grid,
                row,
                "Start minimized",
                "Open XClip in the system tray without showing the popup.",
                startMinimized
        );
        addSettingRow(
                grid,
                row,
                "Start on Windows boot",
                "Launch XClip automatically after signing in.",
                startOnBoot
        );

        VBox section = section(
                "Application behavior",
                "Core runtime and startup preferences.",
                grid
        );
        return pageScroll(section);
    }
}
