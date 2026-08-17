/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * Responsive structural owner of the popup top and filter bars.
 *
 * Non-compact layouts use a full-width weighted row:
 * stable left and right controls keep their usable width while Search owns all
 * remaining horizontal space. The row therefore remains visually occupied on
 * FHD/QHD/4K instead of creating large empty anchor zones.
 */
public final class PopupHeader extends VBox {

    private final Node leftGroup;
    private final Node search;
    private final Node rightGroup;
    private final PopupFilterBar filterBar;
    private final GridPane topBar = new GridPane();

    private PopupResponsivePolicy.LayoutMode appliedMode;

    public PopupHeader(
            Node leftGroup,
            Node search,
            Node rightGroup,
            PopupFilterBar filterBar
    ) {
        this.leftGroup = Objects.requireNonNull(leftGroup, "leftGroup");
        this.search = Objects.requireNonNull(search, "search");
        this.rightGroup = Objects.requireNonNull(rightGroup, "rightGroup");
        this.filterBar = Objects.requireNonNull(filterBar, "filterBar");

        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setHgap(12);
        topBar.setVgap(8);
        topBar.setMaxWidth(Double.MAX_VALUE);
        topBar.getStyleClass().add("top-bar");
        topBar.getChildren().setAll(leftGroup, search, rightGroup);

        getChildren().setAll(topBar, filterBar);
        getStyleClass().add("popup-header");
        setMaxWidth(Double.MAX_VALUE);

        widthProperty().addListener((obs, oldValue, newValue) ->
                applyResponsiveLayout(newValue.doubleValue())
        );
        applyResponsiveLayout(0.0);
    }

    private void applyResponsiveLayout(double width) {
        PopupResponsivePolicy.LayoutMode mode =
                PopupResponsivePolicy.layoutMode(width);
        filterBar.applyAvailableWidth(width);

        if (mode == appliedMode) return;
        appliedMode = mode;

        PopupLayoutSupport.resetGridConstraints(leftGroup);
        PopupLayoutSupport.resetGridConstraints(search);
        PopupLayoutSupport.resetGridConstraints(rightGroup);
        topBar.getColumnConstraints().clear();

        if (search instanceof Region searchRegion) {
            searchRegion.setMinWidth(0);
            searchRegion.setMaxWidth(Double.MAX_VALUE);
        }

        if (PopupResponsivePolicy.stackHeader(width)) {
            ColumnConstraints flexible = new ColumnConstraints();
            flexible.setHgrow(Priority.ALWAYS);
            flexible.setFillWidth(true);

            ColumnConstraints right = new ColumnConstraints();
            right.setHgrow(Priority.NEVER);

            topBar.getColumnConstraints().setAll(flexible, right);

            GridPane.setRowIndex(search, 0);
            GridPane.setColumnIndex(search, 0);
            GridPane.setColumnSpan(search, 2);
            GridPane.setHgrow(search, Priority.ALWAYS);
            GridPane.setHalignment(search, HPos.CENTER);

            GridPane.setRowIndex(leftGroup, 1);
            GridPane.setColumnIndex(leftGroup, 0);
            GridPane.setHalignment(leftGroup, HPos.LEFT);

            GridPane.setRowIndex(rightGroup, 1);
            GridPane.setColumnIndex(rightGroup, 1);
            GridPane.setHalignment(rightGroup, HPos.RIGHT);
        } else {
            ColumnConstraints left = new ColumnConstraints();
            left.setHgrow(Priority.NEVER);

            ColumnConstraints center = new ColumnConstraints();
            center.setHgrow(Priority.ALWAYS);
            center.setFillWidth(true);

            ColumnConstraints right = new ColumnConstraints();
            right.setHgrow(Priority.NEVER);

            topBar.getColumnConstraints().setAll(left, center, right);

            GridPane.setRowIndex(leftGroup, 0);
            GridPane.setColumnIndex(leftGroup, 0);
            GridPane.setHalignment(leftGroup, HPos.LEFT);

            GridPane.setRowIndex(search, 0);
            GridPane.setColumnIndex(search, 1);
            GridPane.setHgrow(search, Priority.ALWAYS);
            GridPane.setHalignment(search, HPos.CENTER);

            GridPane.setRowIndex(rightGroup, 0);
            GridPane.setColumnIndex(rightGroup, 2);
            GridPane.setHalignment(rightGroup, HPos.RIGHT);
        }

        PopupLayoutSupport.applyResponsiveClass(this, mode);
    }
}

