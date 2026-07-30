/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.Objects;

/**
 * Compact filter toolbar matching the redesigned popup hierarchy.
 */
public final class PopupFilterBar extends HBox {

    public PopupFilterBar(
            ToggleButton allButton,
            ToggleButton pinnedButton,
            ToggleButton recentButton,
            ComboBox<?> typeCombo,
            Button resetButton
    ) {
        super(12);

        Objects.requireNonNull(allButton, "allButton");
        Objects.requireNonNull(pinnedButton, "pinnedButton");
        Objects.requireNonNull(recentButton, "recentButton");
        Objects.requireNonNull(typeCombo, "typeCombo");
        Objects.requireNonNull(resetButton, "resetButton");

        HBox scopeButtons = new HBox(allButton, pinnedButton, recentButton);
        scopeButtons.getStyleClass().add("filter-segment");

        HBox filterGroup = new HBox(12, scopeButtons, typeCombo);
        filterGroup.setAlignment(Pos.CENTER_LEFT);
        filterGroup.getStyleClass().add("filter-control-group");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().setAll(filterGroup, spacer, resetButton);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("filter-bar");
    }
}
