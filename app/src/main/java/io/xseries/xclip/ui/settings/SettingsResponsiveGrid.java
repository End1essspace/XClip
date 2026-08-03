/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Two-column Settings grid that reflows into stacked rows in compact mode.
 */
final class SettingsResponsiveGrid extends GridPane {

    private final List<Entry> entries = new ArrayList<>();
    private final ChangeListener<Number> widthListener =
            (observable, oldValue, newValue) ->
                    applyMode(SettingsResponsivePolicy.modeFor(newValue.doubleValue()));

    private SettingsResponsivePolicy.LayoutMode mode =
            SettingsResponsivePolicy.LayoutMode.STANDARD;

    SettingsResponsiveGrid() {
        getStyleClass().add("settings-grid");
        setHgap(18);
        setVgap(14);
        configureColumns(false);

        sceneProperty().addListener((observable, previous, next) -> {
            if (previous != null) {
                previous.widthProperty().removeListener(widthListener);
            }
            if (next != null) {
                next.widthProperty().addListener(widthListener);
                applyMode(SettingsResponsivePolicy.modeFor(next.getWidth()));
            }
        });
    }

    int addSettingRow(int logicalRow, Node text, Node control) {
        requireNextRow(logicalRow);
        entries.add(new Entry(
                Objects.requireNonNull(text, "text"),
                Objects.requireNonNull(control, "control")
        ));
        relayout();
        return logicalRow + 1;
    }

    int addControlRow(int logicalRow, Node control) {
        requireNextRow(logicalRow);
        entries.add(new Entry(null, Objects.requireNonNull(control, "control")));
        relayout();
        return logicalRow + 1;
    }

    SettingsResponsivePolicy.LayoutMode mode() {
        return mode;
    }

    private void applyMode(SettingsResponsivePolicy.LayoutMode next) {
        SettingsResponsivePolicy.LayoutMode value =
                Objects.requireNonNull(next, "next");
        if (mode == value && !getChildren().isEmpty()) return;
        mode = value;
        relayout();
    }

    private void relayout() {
        getChildren().clear();

        boolean compact = mode == SettingsResponsivePolicy.LayoutMode.COMPACT;
        configureColumns(compact);
        setHgap(compact ? 0 : 18);
        setVgap(compact ? 8 : 14);

        int visualRow = 0;
        for (Entry entry : entries) {
            Node control = entry.control();
            GridPane.setHgrow(control, Priority.ALWAYS);
            GridPane.setFillWidth(control, true);

            if (compact) {
                if (entry.text() != null) {
                    add(entry.text(), 0, visualRow++);
                }
                add(control, 0, visualRow++);
            } else {
                if (entry.text() != null) {
                    add(entry.text(), 0, visualRow);
                }
                add(control, 1, visualRow++);
            }
        }
    }

    private void configureColumns(boolean compact) {
        getColumnConstraints().clear();

        if (compact) {
            ColumnConstraints single = new ColumnConstraints();
            single.setMinWidth(0);
            single.setHgrow(Priority.ALWAYS);
            single.setFillWidth(true);
            getColumnConstraints().add(single);
            return;
        }

        ColumnConstraints textColumn = new ColumnConstraints();
        textColumn.setMinWidth(250);
        textColumn.setHgrow(Priority.ALWAYS);
        textColumn.setFillWidth(true);

        ColumnConstraints controlColumn = new ColumnConstraints();
        controlColumn.setMinWidth(250);
        controlColumn.setHgrow(Priority.SOMETIMES);
        controlColumn.setFillWidth(true);

        getColumnConstraints().addAll(textColumn, controlColumn);
    }

    private void requireNextRow(int logicalRow) {
        if (logicalRow != entries.size()) {
            throw new IllegalArgumentException(
                    "Settings grid rows must be appended in deterministic order"
            );
        }
    }

    private record Entry(Node text, Node control) {}
}
