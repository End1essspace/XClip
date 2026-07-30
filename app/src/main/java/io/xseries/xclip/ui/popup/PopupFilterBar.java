/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.Objects;

/**
 * Structural owner of the current popup filter toolbar.
 *
 * This class deliberately preserves the existing controls, style classes,
 * spacing, and child order. Visual redesign is applied only in later phases.
 */
public final class PopupFilterBar extends HBox {

    public PopupFilterBar(
            ToggleButton allButton,
            ToggleButton pinnedButton,
            ToggleButton recentButton,
            ComboBox<?> typeCombo,
            Button resetButton
    ) {
        super(10);

        Objects.requireNonNull(allButton, "allButton");
        Objects.requireNonNull(pinnedButton, "pinnedButton");
        Objects.requireNonNull(recentButton, "recentButton");
        Objects.requireNonNull(typeCombo, "typeCombo");
        Objects.requireNonNull(resetButton, "resetButton");

        Label showFilterLabel = new Label("Show");
        showFilterLabel.getStyleClass().add("filter-label");

        HBox scopeButtons = new HBox(allButton, pinnedButton, recentButton);
        scopeButtons.getStyleClass().add("filter-segment");

        Separator filterSeparator = new Separator(Orientation.VERTICAL);
        filterSeparator.getStyleClass().add("filter-separator");

        Label typeFilterLabel = new Label("Type");
        typeFilterLabel.getStyleClass().add("filter-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().setAll(
                showFilterLabel,
                scopeButtons,
                filterSeparator,
                typeFilterLabel,
                typeCombo,
                spacer,
                resetButton
        );

        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("filter-bar");
    }
}
