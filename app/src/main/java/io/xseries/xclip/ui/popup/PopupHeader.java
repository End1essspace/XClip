/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * Structural owner of the popup top bar and filter bar.
 *
 * The constructor keeps the original node order and CSS hooks unchanged so
 * this extraction has no intended visual effect.
 */
public final class PopupHeader extends VBox {

    public PopupHeader(
            Node search,
            Node statusGroup,
            Node controlGroup,
            PopupFilterBar filterBar
    ) {
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(statusGroup, "statusGroup");
        Objects.requireNonNull(controlGroup, "controlGroup");
        Objects.requireNonNull(filterBar, "filterBar");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(10, search, spacer, statusGroup, controlGroup);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("top-bar");

        getChildren().setAll(topBar, filterBar);
        getStyleClass().add("popup-header");
    }
}
