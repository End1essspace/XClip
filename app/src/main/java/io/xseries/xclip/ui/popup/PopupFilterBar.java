/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.ui.components.SvgIcon;
import io.xseries.xclip.ui.components.UiIcon;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/**
 * Responsive filter toolbar. Compact layouts stack scope and type controls
 * instead of allowing either group to leave the visible window.
 */
public final class PopupFilterBar extends GridPane {

    private final HBox scopeButtons;
    private final StackPane typeControl;
    private final Button resetButton;

    private PopupResponsivePolicy.LayoutMode appliedMode;

    public PopupFilterBar(
            ToggleButton allButton,
            ToggleButton pinnedButton,
            ToggleButton recentButton,
            ComboBox<?> typeCombo,
            Button resetButton
    ) {
        Objects.requireNonNull(allButton, "allButton");
        Objects.requireNonNull(pinnedButton, "pinnedButton");
        Objects.requireNonNull(recentButton, "recentButton");
        Objects.requireNonNull(typeCombo, "typeCombo");
        this.resetButton = Objects.requireNonNull(resetButton, "resetButton");

        scopeButtons = new HBox(allButton, pinnedButton, recentButton);
        scopeButtons.getStyleClass().add("filter-segment");

        typeControl = new StackPane();
        typeControl.setAlignment(Pos.CENTER_LEFT);
        typeControl.getStyleClass().add("filter-type-wrap");

        typeCombo.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(typeCombo, Pos.CENTER_LEFT);

        SvgIcon typeIcon = SvgIcon.of(UiIcon.FUNNEL, 13, "filter-type-icon");
        StackPane.setAlignment(typeIcon, Pos.CENTER_LEFT);
        StackPane.setMargin(typeIcon, new Insets(0, 0, 0, 11));

        typeControl.getChildren().setAll(typeCombo, typeIcon);

        setAlignment(Pos.CENTER_LEFT);
        setHgap(8);
        setVgap(7);
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("filter-bar");
        getChildren().setAll(scopeButtons, typeControl, resetButton);

        widthProperty().addListener((obs, oldValue, newValue) ->
                applyAvailableWidth(newValue.doubleValue())
        );
        applyAvailableWidth(0.0);
    }

    public void applyAvailableWidth(double width) {
        PopupResponsivePolicy.LayoutMode mode =
                PopupResponsivePolicy.layoutMode(width);
        if (mode == appliedMode) return;
        appliedMode = mode;

        resetGridConstraints(scopeButtons);
        resetGridConstraints(typeControl);
        resetGridConstraints(resetButton);
        getColumnConstraints().clear();

        ColumnConstraints flexible = new ColumnConstraints();
        flexible.setHgrow(Priority.ALWAYS);
        flexible.setFillWidth(true);

        ColumnConstraints content = new ColumnConstraints();
        content.setHgrow(Priority.NEVER);

        if (PopupResponsivePolicy.stackFilters(width)) {
            getColumnConstraints().setAll(flexible, content);

            GridPane.setRowIndex(scopeButtons, 0);
            GridPane.setColumnIndex(scopeButtons, 0);
            GridPane.setColumnSpan(scopeButtons, 2);
            GridPane.setHalignment(scopeButtons, HPos.LEFT);

            GridPane.setRowIndex(typeControl, 1);
            GridPane.setColumnIndex(typeControl, 0);
            GridPane.setHgrow(typeControl, Priority.ALWAYS);
            typeControl.setMinWidth(0);
            typeControl.setPrefWidth(0);
            typeControl.setMaxWidth(Double.MAX_VALUE);

            GridPane.setRowIndex(resetButton, 1);
            GridPane.setColumnIndex(resetButton, 1);
            GridPane.setHalignment(resetButton, HPos.RIGHT);
        } else {
            getColumnConstraints().setAll(flexible, content, content);

            GridPane.setRowIndex(scopeButtons, 0);
            GridPane.setColumnIndex(scopeButtons, 0);
            GridPane.setHalignment(scopeButtons, HPos.LEFT);

            GridPane.setRowIndex(typeControl, 0);
            GridPane.setColumnIndex(typeControl, 1);
            GridPane.setHalignment(typeControl, HPos.RIGHT);
            typeControl.setMinWidth(190);
            typeControl.setPrefWidth(210);
            typeControl.setMaxWidth(235);

            GridPane.setRowIndex(resetButton, 0);
            GridPane.setColumnIndex(resetButton, 2);
            GridPane.setHalignment(resetButton, HPos.RIGHT);
        }

        getStyleClass().removeAll(
                "responsive-compact",
                "responsive-balanced",
                "responsive-wide"
        );
        getStyleClass().add(switch (mode) {
            case COMPACT -> "responsive-compact";
            case BALANCED -> "responsive-balanced";
            case WIDE -> "responsive-wide";
        });
    }

    private static void resetGridConstraints(Node node) {
        GridPane.setRowIndex(node, null);
        GridPane.setColumnIndex(node, null);
        GridPane.setRowSpan(node, null);
        GridPane.setColumnSpan(node, null);
        GridPane.setHalignment(node, null);
        GridPane.setHgrow(node, null);
    }
}
