
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
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * Responsive structural owner of the popup top and filter bars.
 */
public final class PopupHeader extends VBox {

    private final Node search;
    private final Node statusGroup;
    private final Node controlGroup;
    private final PopupFilterBar filterBar;
    private final GridPane topBar = new GridPane();

    private PopupResponsivePolicy.LayoutMode appliedMode;

    public PopupHeader(
            Node search,
            Node statusGroup,
            Node controlGroup,
            PopupFilterBar filterBar
    ) {
        this.search = Objects.requireNonNull(search, "search");
        this.statusGroup = Objects.requireNonNull(statusGroup, "statusGroup");
        this.controlGroup = Objects.requireNonNull(controlGroup, "controlGroup");
        this.filterBar = Objects.requireNonNull(filterBar, "filterBar");

        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setHgap(10);
        topBar.setVgap(8);
        topBar.setMaxWidth(Double.MAX_VALUE);
        topBar.getStyleClass().add("top-bar");
        topBar.getChildren().setAll(search, statusGroup, controlGroup);

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

        PopupLayoutSupport.resetGridConstraints(search);
        PopupLayoutSupport.resetGridConstraints(statusGroup);
        PopupLayoutSupport.resetGridConstraints(controlGroup);
        topBar.getColumnConstraints().clear();

        ColumnConstraints flexible = new ColumnConstraints();
        flexible.setHgrow(Priority.ALWAYS);
        flexible.setFillWidth(true);

        ColumnConstraints content = new ColumnConstraints();
        content.setHgrow(Priority.NEVER);

        if (PopupResponsivePolicy.stackHeader(width)) {
            topBar.getColumnConstraints().setAll(flexible, content, content);

            GridPane.setRowIndex(search, 0);
            GridPane.setColumnIndex(search, 0);
            GridPane.setColumnSpan(search, 3);
            GridPane.setHgrow(search, Priority.ALWAYS);

            GridPane.setRowIndex(statusGroup, 1);
            GridPane.setColumnIndex(statusGroup, 0);
            GridPane.setHalignment(statusGroup, HPos.LEFT);

            GridPane.setRowIndex(controlGroup, 1);
            GridPane.setColumnIndex(controlGroup, 2);
            GridPane.setHalignment(controlGroup, HPos.RIGHT);
        } else {
            topBar.getColumnConstraints().setAll(flexible, content, content);

            GridPane.setRowIndex(search, 0);
            GridPane.setColumnIndex(search, 0);
            GridPane.setHgrow(search, Priority.ALWAYS);

            GridPane.setRowIndex(statusGroup, 0);
            GridPane.setColumnIndex(statusGroup, 1);
            GridPane.setHalignment(statusGroup, HPos.RIGHT);

            GridPane.setRowIndex(controlGroup, 0);
            GridPane.setColumnIndex(controlGroup, 2);
            GridPane.setHalignment(controlGroup, HPos.RIGHT);
        }
        PopupLayoutSupport.applyResponsiveClass(this, mode);
    }
}
