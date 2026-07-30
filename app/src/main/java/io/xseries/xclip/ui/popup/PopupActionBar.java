/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.HBox;

import java.util.Objects;

/**
 * Structural owner of the popup footer actions.
 *
 * Button behavior and styling remain owned by PopupWindow during the safe
 * decomposition phase.
 */
public final class PopupActionBar extends HBox {

    public PopupActionBar(Node paste, Node copy, Node favorite, Node delete) {
        super(
                8,
                Objects.requireNonNull(paste, "paste"),
                Objects.requireNonNull(copy, "copy"),
                Objects.requireNonNull(favorite, "favorite"),
                Objects.requireNonNull(delete, "delete")
        );

        setPadding(new Insets(8));
        getStyleClass().add("actions-bar");
    }
}
