/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

final class SettingsPageSupport {

    private SettingsPageSupport() {}

    static ScrollPane pageScroll(Node... cards) {
        VBox content = new VBox(14);
        content.setPadding(new Insets(20, 22, 24, 22));
        content.getChildren().addAll(cards);
        content.getStyleClass().add("settings-page-content");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("settings-page-scroll");
        return scroll;
    }

    static VBox informationSection(
            String title,
            String description,
            List<? extends Node> rows
    ) {
        VBox list = new VBox(0);
        list.getStyleClass().add("settings-info-list");
        list.getChildren().addAll(rows);
        return section(title, description, list);
    }

    static HBox infoRow(String label, String value) {
        Label name = new Label(label);
        name.getStyleClass().add("settings-info-name");

        Label detail = new Label(value == null || value.isBlank() ? "DEV" : value);
        detail.setWrapText(true);
        detail.getStyleClass().add("settings-info-value");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(14, name, spacer, detail);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("settings-info-row");
        return row;
    }

    static GridPane settingsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(14);

        ColumnConstraints textColumn = new ColumnConstraints();
        textColumn.setMinWidth(250);
        textColumn.setHgrow(Priority.ALWAYS);

        ColumnConstraints controlColumn = new ColumnConstraints();
        controlColumn.setMinWidth(250);

        grid.getColumnConstraints().addAll(textColumn, controlColumn);
        return grid;
    }

    static int addSettingRow(
            GridPane grid,
            int row,
            String title,
            String description,
            Node control
    ) {
        grid.add(settingText(title, description), 0, row);
        grid.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
        return row + 1;
    }

    static VBox settingText(String title, String description) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("settings-field-title");

        Label descriptionLabel = new Label(description);
        descriptionLabel.setWrapText(true);
        descriptionLabel.getStyleClass().add("settings-field-description");

        return new VBox(3, titleLabel, descriptionLabel);
    }

    static VBox section(
            String title,
            String description,
            Node... content
    ) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("settings-section-title");

        Label descriptionLabel = new Label(description);
        descriptionLabel.setWrapText(true);
        descriptionLabel.getStyleClass().add("settings-section-description");

        VBox section = new VBox(12);
        section.getChildren().addAll(titleLabel, descriptionLabel);
        section.getChildren().addAll(content);
        section.getStyleClass().add("settings-section");
        return section;
    }
}
