/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.ui.components.SvgIcon;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/**
 * Balanced filter toolbar: scope navigation stays on the left, while the type
 * filter and conditional reset action form one compact group on the right.
 */
public final class PopupFilterBar extends HBox {

    public PopupFilterBar(
            ToggleButton allButton,
            ToggleButton pinnedButton,
            ToggleButton recentButton,
            ComboBox<?> typeCombo,
            Button resetButton
    ) {
        super(8);

        Objects.requireNonNull(allButton, "allButton");
        Objects.requireNonNull(pinnedButton, "pinnedButton");
        Objects.requireNonNull(recentButton, "recentButton");
        Objects.requireNonNull(typeCombo, "typeCombo");
        Objects.requireNonNull(resetButton, "resetButton");

        HBox scopeButtons = new HBox(allButton, pinnedButton, recentButton);
        scopeButtons.getStyleClass().add("filter-segment");

        StackPane typeControl = new StackPane();
        typeControl.setAlignment(Pos.CENTER_LEFT);
        typeControl.getStyleClass().add("filter-type-wrap");
        typeControl.setMinWidth(190);
        typeControl.setPrefWidth(210);
        typeControl.setMaxWidth(235);

        typeCombo.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(typeCombo, Pos.CENTER_LEFT);

        SvgIcon typeIcon = SvgIcon.of("funnel", 13, "filter-type-icon");
        StackPane.setAlignment(typeIcon, Pos.CENTER_LEFT);
        StackPane.setMargin(typeIcon, new Insets(0, 0, 0, 11));

        typeControl.getChildren().setAll(typeCombo, typeIcon);

        HBox rightGroup = new HBox(7, typeControl, resetButton);
        rightGroup.setAlignment(Pos.CENTER_RIGHT);
        rightGroup.getStyleClass().add("filter-control-group");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().setAll(scopeButtons, spacer, rightGroup);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("filter-bar");
    }
}
