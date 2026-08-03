
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.ui.components.SvgIcon;
import io.xseries.xclip.ui.components.UiIcon;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/**
 * Responsive filter toolbar. Compact layouts stack scope, content type, and
 * tag controls instead of allowing any group to leave the visible window.
 */
public final class PopupFilterBar extends GridPane {

    private final HBox scopeButtons;
    private final StackPane typeControl;
    private final StackPane tagControl;
    private final Button resetButton;

    private PopupResponsivePolicy.LayoutMode appliedMode;

    public PopupFilterBar(
            ToggleButton allButton,
            ToggleButton pinnedButton,
            ToggleButton recentButton,
            ComboBox<?> typeCombo,
            ComboBox<?> tagCombo,
            Button resetButton
    ) {
        Objects.requireNonNull(allButton, "allButton");
        Objects.requireNonNull(pinnedButton, "pinnedButton");
        Objects.requireNonNull(recentButton, "recentButton");
        Objects.requireNonNull(typeCombo, "typeCombo");
        Objects.requireNonNull(tagCombo, "tagCombo");
        this.resetButton = Objects.requireNonNull(resetButton, "resetButton");

        scopeButtons = new HBox(allButton, pinnedButton, recentButton);
        scopeButtons.getStyleClass().add("filter-segment");

        typeControl = createComboControl(
                typeCombo,
                UiIcon.FUNNEL,
                "filter-type-wrap",
                "filter-type-icon"
        );
        tagControl = createComboControl(
                tagCombo,
                UiIcon.TAG,
                "filter-tag-wrap",
                "filter-tag-icon"
        );

        setAlignment(Pos.CENTER_LEFT);
        setHgap(8);
        setVgap(7);
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("filter-bar");
        getChildren().setAll(scopeButtons, typeControl, tagControl, resetButton);

        widthProperty().addListener((obs, oldValue, newValue) ->
                applyAvailableWidth(newValue.doubleValue())
        );
        applyAvailableWidth(0.0);
    }

    public void applyAvailableWidth(double width) {
        PopupResponsivePolicy.LayoutMode mode =
                PopupResponsivePolicy.layoutMode(width);
        if (mode == appliedMode) return;
        appliedMode = mode;

        PopupLayoutSupport.resetGridConstraints(scopeButtons);
        PopupLayoutSupport.resetGridConstraints(typeControl);
        PopupLayoutSupport.resetGridConstraints(tagControl);
        PopupLayoutSupport.resetGridConstraints(resetButton);
        getColumnConstraints().clear();

        ColumnConstraints flexible = new ColumnConstraints();
        flexible.setHgrow(Priority.ALWAYS);
        flexible.setFillWidth(true);

        ColumnConstraints content = new ColumnConstraints();
        content.setHgrow(Priority.NEVER);

        if (PopupResponsivePolicy.stackFilters(width)) {
            getColumnConstraints().setAll(flexible, content);

            GridPane.setRowIndex(scopeButtons, 0);
            GridPane.setColumnIndex(scopeButtons, 0);
            GridPane.setColumnSpan(scopeButtons, 2);
            GridPane.setHalignment(scopeButtons, HPos.LEFT);

            configureStackedCombo(typeControl, 1, true);
            configureStackedCombo(tagControl, 2, false);

            GridPane.setRowIndex(resetButton, 2);
            GridPane.setColumnIndex(resetButton, 1);
            GridPane.setHalignment(resetButton, HPos.RIGHT);
        } else {
            getColumnConstraints().setAll(flexible, content, content, content);

            GridPane.setRowIndex(scopeButtons, 0);
            GridPane.setColumnIndex(scopeButtons, 0);
            GridPane.setHalignment(scopeButtons, HPos.LEFT);

            configureWideCombo(typeControl, 1, 180, 200, 220);
            configureWideCombo(tagControl, 2, 170, 190, 220);

            GridPane.setRowIndex(resetButton, 0);
            GridPane.setColumnIndex(resetButton, 3);
            GridPane.setHalignment(resetButton, HPos.RIGHT);
        }
        PopupLayoutSupport.applyResponsiveClass(this, mode);
    }

    private StackPane createComboControl(
            ComboBox<?> combo,
            UiIcon icon,
            String wrapStyleClass,
            String iconStyleClass
    ) {
        StackPane control = new StackPane();
        control.setAlignment(Pos.CENTER_LEFT);
        control.getStyleClass().add(wrapStyleClass);

        combo.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(combo, Pos.CENTER_LEFT);

        SvgIcon leadingIcon = SvgIcon.of(icon, 13, iconStyleClass);
        StackPane.setAlignment(leadingIcon, Pos.CENTER_LEFT);
        StackPane.setMargin(leadingIcon, new Insets(0, 0, 0, 11));

        control.getChildren().setAll(combo, leadingIcon);
        return control;
    }

    private void configureStackedCombo(
            StackPane control,
            int row,
            boolean spanResetColumn
    ) {
        GridPane.setRowIndex(control, row);
        GridPane.setColumnIndex(control, 0);
        GridPane.setColumnSpan(control, spanResetColumn ? 2 : 1);
        GridPane.setHgrow(control, Priority.ALWAYS);
        control.setMinWidth(0);
        control.setPrefWidth(0);
        control.setMaxWidth(Double.MAX_VALUE);
    }

    private void configureWideCombo(
            StackPane control,
            int column,
            double minWidth,
            double prefWidth,
            double maxWidth
    ) {
        GridPane.setRowIndex(control, 0);
        GridPane.setColumnIndex(control, column);
        GridPane.setHalignment(control, HPos.RIGHT);
        control.setMinWidth(minWidth);
        control.setPrefWidth(prefWidth);
        control.setMaxWidth(maxWidth);
    }
}
