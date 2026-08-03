
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.Objects;

/**
 * Responsive popup footer. Wide layouts keep one horizontal row; compact
 * layouts split workflow and state actions so controls never leave the window.
 */
public final class PopupActionBar extends GridPane {

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
    private PopupResponsivePolicy.LayoutMode appliedMode;

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

        statusLabel.setAlignment(Pos.CENTER);
        statusLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setMinWidth(0);
        statusLabel.setMouseTransparent(true);
        statusLabel.setAccessibleText("Keyboard shortcuts and operation status");
        statusLabel.getStyleClass().add("actions-status");

        setAlignment(Pos.CENTER_LEFT);
        setMaxWidth(Double.MAX_VALUE);
        setHgap(12);
        setVgap(6);
        getChildren().setAll(left, statusLabel, right);
        getStyleClass().add("actions-bar");

        widthProperty().addListener((obs, oldValue, newValue) -> {
            applyResponsiveLayout(newValue.doubleValue());
            refreshPresentation();
        });
        left.widthProperty().addListener((obs, oldValue, newValue) -> refreshPresentation());
        right.widthProperty().addListener((obs, oldValue, newValue) -> refreshPresentation());

        applyResponsiveLayout(0.0);
        showHints();
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

    private void applyResponsiveLayout(double width) {
        PopupResponsivePolicy.LayoutMode mode =
                PopupResponsivePolicy.layoutMode(width);
        if (mode == appliedMode) return;
        appliedMode = mode;

        PopupLayoutSupport.resetGridConstraints(left);
        PopupLayoutSupport.resetGridConstraints(statusLabel);
        PopupLayoutSupport.resetGridConstraints(right);
        getColumnConstraints().clear();

        if (PopupResponsivePolicy.stackFooter(width)) {
            ColumnConstraints flexible = new ColumnConstraints();
            flexible.setHgrow(Priority.ALWAYS);
            flexible.setFillWidth(true);
            getColumnConstraints().setAll(flexible);

            GridPane.setRowIndex(left, 0);
            GridPane.setColumnIndex(left, 0);
            GridPane.setHalignment(left, HPos.LEFT);

            GridPane.setRowIndex(right, 1);
            GridPane.setColumnIndex(right, 0);
            GridPane.setHalignment(right, HPos.RIGHT);

            GridPane.setRowIndex(statusLabel, 2);
            GridPane.setColumnIndex(statusLabel, 0);
            GridPane.setHalignment(statusLabel, HPos.CENTER);
            GridPane.setHgrow(statusLabel, Priority.ALWAYS);
        } else {
            ColumnConstraints leftColumn = new ColumnConstraints();
            leftColumn.setHgrow(Priority.NEVER);

            ColumnConstraints centerColumn = new ColumnConstraints();
            centerColumn.setHgrow(Priority.ALWAYS);
            centerColumn.setFillWidth(true);

            ColumnConstraints rightColumn = new ColumnConstraints();
            rightColumn.setHgrow(Priority.NEVER);
            getColumnConstraints().setAll(leftColumn, centerColumn, rightColumn);

            GridPane.setRowIndex(left, 0);
            GridPane.setColumnIndex(left, 0);
            GridPane.setHalignment(left, HPos.LEFT);

            GridPane.setRowIndex(statusLabel, 0);
            GridPane.setColumnIndex(statusLabel, 1);
            GridPane.setHalignment(statusLabel, HPos.CENTER);
            GridPane.setHgrow(statusLabel, Priority.ALWAYS);

            GridPane.setRowIndex(right, 0);
            GridPane.setColumnIndex(right, 2);
            GridPane.setHalignment(right, HPos.RIGHT);
        }
        PopupLayoutSupport.applyResponsiveClass(this, mode);
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
        boolean compact = PopupResponsivePolicy.stackFooter(getWidth());
        double available = compact
                ? Math.max(0.0, getWidth() - 28.0)
                : Math.max(0.0, getWidth()
                        - Math.max(left.getWidth(), right.getWidth()) * 2.0
                        - 56.0);
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
