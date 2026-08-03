/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

/**
 * Shared mechanics for responsive popup containers.
 *
 * Layout ownership remains in each component. This helper only centralizes the
 * identical JavaFX constraint reset and responsive CSS-class bookkeeping.
 */
final class PopupLayoutSupport {

    private static final String COMPACT_CLASS = "responsive-compact";
    private static final String BALANCED_CLASS = "responsive-balanced";
    private static final String WIDE_CLASS = "responsive-wide";

    private PopupLayoutSupport() {}

    static void applyResponsiveClass(
            Node node,
            PopupResponsivePolicy.LayoutMode mode
    ) {
        node.getStyleClass().removeAll(
                COMPACT_CLASS,
                BALANCED_CLASS,
                WIDE_CLASS
        );
        node.getStyleClass().add(switch (mode) {
            case COMPACT -> COMPACT_CLASS;
            case BALANCED -> BALANCED_CLASS;
            case WIDE -> WIDE_CLASS;
        });
    }

    static void resetGridConstraints(Node node) {
        GridPane.setRowIndex(node, null);
        GridPane.setColumnIndex(node, null);
        GridPane.setRowSpan(node, null);
        GridPane.setColumnSpan(node, null);
        GridPane.setHalignment(node, null);
        GridPane.setHgrow(node, null);
        GridPane.setVgrow(node, null);
    }
}
