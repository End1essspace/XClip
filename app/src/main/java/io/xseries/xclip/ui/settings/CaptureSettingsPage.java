/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import static io.xseries.xclip.ui.settings.SettingsPageSupport.addSettingRow;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.pageScroll;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.section;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.settingsGrid;

public final class CaptureSettingsPage {

    private CaptureSettingsPage() {}

    public static ScrollPane create(
            Spinner<Integer> minClipLength,
            Spinner<Integer> maxClipChars,
            Spinner<Integer> uiClipLimit
    ) {
        GridPane grid = settingsGrid();
        int row = 0;
        row = addSettingRow(
                grid,
                row,
                "Min clip length",
                "Ignore clipboard text shorter than this number of characters.",
                minClipLength
        );
        row = addSettingRow(
                grid,
                row,
                "Max clip chars",
                "Longer clipboard text is truncated before storage.",
                maxClipChars
        );
        addSettingRow(
                grid,
                row,
                "UI clip limit",
                "Maximum number of prepared rows shown in the popup.",
                uiClipLimit
        );

        VBox section = section(
                "Capture limits",
                "Bounds applied before clipboard entries reach persistent history.",
                grid
        );
        return pageScroll(section);
    }
}
