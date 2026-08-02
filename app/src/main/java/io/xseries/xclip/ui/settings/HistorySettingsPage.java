/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import static io.xseries.xclip.ui.settings.SettingsPageSupport.actionRow;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.addControlRow;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.addSettingRow;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.pageScroll;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.section;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.settingsGrid;

public final class HistorySettingsPage {

    private HistorySettingsPage() {}

    public record Controls(
            Spinner<Integer> maxHistory,
            CheckBox retentionRecentEnabled,
            Spinner<Integer> retentionRecentDays,
            Spinner<Integer> retentionTextDays,
            Spinner<Integer> retentionCodeDays,
            Spinner<Integer> retentionUrlDays,
            Spinner<Integer> retentionPathDays,
            Spinner<Integer> retentionJsonDays,
            Spinner<Integer> retentionCommandDays,
            CheckBox clearRecentOnExit,
            Label cleanupStatusLabel,
            Button runCleanupNow,
            Button resetRetentionDefaults
    ) {}

    public static ScrollPane create(Controls controls) {
        GridPane capacityGrid = settingsGrid();
        addSettingRow(
                capacityGrid,
                0,
                "Max history",
                "Maximum number of unpinned clipboard entries retained locally.",
                controls.maxHistory()
        );
        VBox capacitySection = section(
                "History capacity",
                "The normal size limit for RECENT clipboard history.",
                capacityGrid
        );

        GridPane retentionGrid = settingsGrid();
        int row = 0;
        row = addControlRow(retentionGrid, row, controls.retentionRecentEnabled());
        row = addSettingRow(
                retentionGrid,
                row,
                "General RECENT age",
                "Applies to every unpinned content type when automatic age cleanup is enabled.",
                controls.retentionRecentDays()
        );
        row = addSettingRow(retentionGrid, row, "TEXT override", "Days to keep TEXT clips. Zero disables this type-specific rule.", controls.retentionTextDays());
        row = addSettingRow(retentionGrid, row, "CODE override", "Days to keep CODE clips. Zero disables this type-specific rule.", controls.retentionCodeDays());
        row = addSettingRow(retentionGrid, row, "URL override", "Days to keep URL clips. Zero disables this type-specific rule.", controls.retentionUrlDays());
        row = addSettingRow(retentionGrid, row, "PATH override", "Days to keep PATH clips. Zero disables this type-specific rule.", controls.retentionPathDays());
        row = addSettingRow(retentionGrid, row, "JSON override", "Days to keep JSON clips. Zero disables this type-specific rule.", controls.retentionJsonDays());
        row = addSettingRow(retentionGrid, row, "COMMAND override", "Days to keep COMMAND clips. Zero disables this type-specific rule.", controls.retentionCommandDays());
        addControlRow(retentionGrid, row, controls.clearRecentOnExit());

        Label hint = new Label(
                "PINNED clips are always preserved. If both general and per-type rules apply, the shorter age wins. Cleanup never rewrites clipboard content."
        );
        hint.setWrapText(true);
        hint.getStyleClass().add("settings-retention-hint");

        var actions = actionRow(
                Pos.CENTER_RIGHT,
                controls.runCleanupNow(),
                controls.resetRetentionDefaults()
        );

        VBox retentionSection = section(
                "History retention & cleanup",
                "Age-based cleanup is opt-in and applies only to RECENT history.",
                retentionGrid,
                hint,
                controls.cleanupStatusLabel(),
                actions
        );
        retentionSection.getStyleClass().add("retention-settings-section");
        return pageScroll(capacitySection, retentionSection);
    }
}