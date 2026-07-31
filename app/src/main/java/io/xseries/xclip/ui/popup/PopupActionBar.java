/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/**
 * Single-row popup footer.
 *
 * Workflow actions stay on the left, state/destructive actions stay on the
 * right, and the otherwise empty center becomes a responsive status zone.
 * The zone normally shows keyboard hints and temporarily displays operation
 * feedback without adding a second footer strip or covering clipboard rows.
 */
public final class PopupActionBar extends StackPane {

    private static final String FULL_HINTS =
            "↑↓ Navigate   •   Enter Paste   •   Ctrl+C Copy   •   Del Delete";
    private static final String COMPACT_HINTS =
            "↑↓ Navigate   •   Enter Paste   •   Del Delete";
    private static final PseudoClass MESSAGE_PC =
            PseudoClass.getPseudoClass("message");
    private static final PseudoClass SUCCESS_PC =
            PseudoClass.getPseudoClass("success");
    private static final PseudoClass WARNING_PC =
            PseudoClass.getPseudoClass("warning");
    private static final PseudoClass ERROR_PC =
            PseudoClass.getPseudoClass("error");

    public enum StatusTone {
        NEUTRAL,
        SUCCESS,
        WARNING,
        ERROR
    }

    private final HBox left;
    private final HBox right;
    private final Label statusLabel = new Label();

    private boolean messageMode;
    private String messageText = "";
    private StatusTone statusTone = StatusTone.NEUTRAL;

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

        left = new HBox(8, paste, copy, actions);
        left.setAlignment(Pos.CENTER_LEFT);
        left.getStyleClass().add("action-group-left");

        right = new HBox(8, favorite, delete);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.getStyleClass().add("action-group-right");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actionRow = new HBox(left, spacer, right);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        actionRow.setMaxWidth(Double.MAX_VALUE);
        actionRow.getStyleClass().add("actions-row");

        statusLabel.setAlignment(Pos.CENTER);
        statusLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setMinWidth(0);
        statusLabel.setMouseTransparent(true);
        statusLabel.setAccessibleText("Keyboard shortcuts and operation status");
        statusLabel.getStyleClass().add("actions-status");

        StackPane.setAlignment(actionRow, Pos.CENTER);
        StackPane.setAlignment(statusLabel, Pos.CENTER);
        getChildren().setAll(actionRow, statusLabel);
        getStyleClass().add("actions-bar");

        widthProperty().addListener((obs, oldValue, newValue) -> refreshPresentation());
        left.widthProperty().addListener((obs, oldValue, newValue) -> refreshPresentation());
        right.widthProperty().addListener((obs, oldValue, newValue) -> refreshPresentation());

        showHints();
    }

    public void showStatus(String message) {
        showStatus(message, StatusTone.NEUTRAL);
    }

    public void showStatus(String message, StatusTone tone) {
        messageText = Objects.requireNonNullElse(message, "").trim();
        messageMode = !messageText.isEmpty();
        statusTone = tone == null ? StatusTone.NEUTRAL : tone;
        applyStatusPseudoClasses();
        refreshPresentation();
    }

    public void showHints() {
        messageMode = false;
        messageText = "";
        statusTone = StatusTone.NEUTRAL;
        applyStatusPseudoClasses();
        refreshPresentation();
    }

    public boolean isShowingStatus() {
        return messageMode;
    }

    private void applyStatusPseudoClasses() {
        statusLabel.pseudoClassStateChanged(MESSAGE_PC, messageMode);
        statusLabel.pseudoClassStateChanged(
                SUCCESS_PC,
                messageMode && statusTone == StatusTone.SUCCESS
        );
        statusLabel.pseudoClassStateChanged(
                WARNING_PC,
                messageMode && statusTone == StatusTone.WARNING
        );
        statusLabel.pseudoClassStateChanged(
                ERROR_PC,
                messageMode && statusTone == StatusTone.ERROR
        );
    }

    private void refreshPresentation() {
        double sideWidth = Math.max(left.getWidth(), right.getWidth());
        double available = Math.max(
                0.0,
                getWidth() - (sideWidth * 2.0) - 56.0
        );
        statusLabel.setMaxWidth(available);

        String text = messageMode
                ? (available >= 120.0 ? messageText : "")
                : hintTextForAvailableWidth(available);

        statusLabel.setText(text);
        statusLabel.setAccessibleText(messageMode
                ? "Operation status: " + messageText
                : "Keyboard shortcuts: " + FULL_HINTS);
        boolean visible = !text.isBlank();
        statusLabel.setVisible(visible);
        statusLabel.setManaged(visible);
    }

    static String hintTextForAvailableWidth(double available) {
        if (!Double.isFinite(available) || available < 330.0) return "";
        return available >= 520.0 ? FULL_HINTS : COMPACT_HINTS;
    }
}

