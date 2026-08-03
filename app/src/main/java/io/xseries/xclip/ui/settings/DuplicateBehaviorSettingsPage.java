/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;
import io.xseries.xclip.ui.settings.DuplicateSettingsModel.WindowPreset;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import static io.xseries.xclip.ui.settings.SettingsPageSupport.actionRow;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.addSettingRow;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.pageScroll;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.section;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.settingsGrid;

public final class DuplicateBehaviorSettingsPage {

    private DuplicateBehaviorSettingsPage() {}

    public record Controls(
            ComboBox<DuplicateBehaviorPolicy.RecentDuplicatePosition> recentPosition,
            ComboBox<DuplicateBehaviorPolicy.PinnedDuplicatePosition> pinnedPosition,
            ComboBox<DuplicateBehaviorPolicy.WhitespaceMode> whitespaceMode,
            ComboBox<DuplicateBehaviorPolicy.CaseSensitivity> caseSensitivity,
            ComboBox<WindowPreset> windowPreset,
            TextField customWindowMillis,
            CheckBox exactContentMode,
            Label exactOverrideHint,
            Button resetDefaults
    ) {}

    public static ScrollPane create(Controls controls) {
        GridPane grid = settingsGrid();
        int row = 0;
        row = addSettingRow(grid, row, "Recent duplicates", "Move an existing RECENT clip to the top or keep its current position.", controls.recentPosition());
        row = addSettingRow(grid, row, "Pinned duplicates", "Keep manual PINNED order or move the copied pinned clip to the top.", controls.pinnedPosition());
        row = addSettingRow(grid, row, "Whitespace", "Normalize collapses whitespace runs; Preserve compares copied characters.", controls.whitespaceMode());
        row = addSettingRow(grid, row, "Letter case", "Case-sensitive treats Alpha and alpha as different content.", controls.caseSensitivity());

        VBox windowControl = new VBox(7, controls.windowPreset(), controls.customWindowMillis());
        row = addSettingRow(
                grid,
                row,
                "Duplicate window",
                "Unlimited checks all history. A finite window allows older matches to create a new row.",
                windowControl
        );

        VBox exactControl = new VBox(
                5,
                controls.exactContentMode(),
                controls.exactOverrideHint()
        );
        addSettingRow(
                grid,
                row,
                "Exact content mode",
                "Compares every character exactly and overrides Whitespace and Letter case.",
                exactControl
        );

        var actions = actionRow(
                Pos.CENTER_RIGHT,
                controls.resetDefaults()
        );

        VBox section = section(
                "Duplicate behavior",
                "These rules apply immediately after Apply and are persisted in config.json.",
                grid,
                actions
        );
        section.getStyleClass().add("duplicate-settings-section");
        return pageScroll(section);
    }
}