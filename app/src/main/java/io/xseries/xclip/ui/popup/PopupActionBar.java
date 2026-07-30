
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * Redesigned popup footer with workflow actions on the left, state/destructive
 * actions on the right, and one quiet keyboard-hint strip.
 */
public final class PopupActionBar extends VBox {

    public PopupActionBar(
            Node paste,
            Node copy,
            Node actions,
            Node favorite,
            Node delete
    ) {
        Objects.requireNonNull(paste, "paste");
        Objects.requireNonNull(copy, "copy");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(favorite, "favorite");
        Objects.requireNonNull(delete, "delete");

        HBox left = new HBox(10, paste, copy, actions);
        left.setAlignment(Pos.CENTER_LEFT);
        left.getStyleClass().add("action-group-left");

        HBox right = new HBox(10, favorite, delete);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.getStyleClass().add("action-group-right");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actionRow = new HBox(left, spacer, right);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        actionRow.getStyleClass().add("actions-row");

        Label hint = new Label("↑ / ↓ Navigate   •   Enter Paste   •   Ctrl+C Copy   •   Del Delete");
        hint.getStyleClass().add("actions-hint");
        hint.setMaxWidth(Double.MAX_VALUE);
        hint.setAlignment(Pos.CENTER);

        getChildren().setAll(actionRow, hint);
        getStyleClass().add("actions-bar");
    }
}
