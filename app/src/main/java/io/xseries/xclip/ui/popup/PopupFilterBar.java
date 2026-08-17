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
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/**
 * Responsive filter toolbar.
 *
 * Normal layouts mirror the control mass on both sides of the row:
 * scope stays as one bounded left group while the combined type/tag region
 * mirrors that width on the right, leaving one balanced center gap.
 * Compact layouts stack the same controls.
 */
public final class PopupFilterBar extends GridPane {

    private final HBox scopeButtons;
    private final StackPane typeControl;
    private final StackPane tagControl;
    private final Button resetButton;
    private final HBox rightGroup;

    private PopupResponsivePolicy.LayoutMode appliedMode;
    private boolean layoutApplied;
    private boolean appliedStacked;
    private double appliedMirroredGroupWidth = Double.NaN;

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
        scopeButtons.setMaxWidth(Double.MAX_VALUE);
        for (ToggleButton button : new ToggleButton[] {
                allButton,
                pinnedButton,
                recentButton
        }) {
            button.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(button, Priority.ALWAYS);
        }

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

        rightGroup = new HBox(8, tagControl, resetButton);
        rightGroup.setAlignment(Pos.CENTER_RIGHT);
        rightGroup.getStyleClass().add("filter-right-group");

        setAlignment(Pos.CENTER_LEFT);
        setHgap(10);
        setVgap(7);
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("filter-bar");
        getChildren().setAll(scopeButtons, typeControl, rightGroup);

        widthProperty().addListener((obs, oldValue, newValue) ->
                applyAvailableWidth(newValue.doubleValue())
        );
        applyAvailableWidth(0.0);
    }

    public void applyAvailableWidth(double width) {
        PopupResponsivePolicy.LayoutMode mode =
                PopupResponsivePolicy.layoutMode(width);
        boolean stacked = PopupResponsivePolicy.stackFilters(width);

        double mirroredGroupWidth = stacked
                ? Double.NaN
                : PopupResponsivePolicy.mirroredFilterGroupWidth(width);

        boolean sameGeometry = layoutApplied
                && mode == appliedMode
                && stacked == appliedStacked
                && (
                    stacked
                    || Math.abs(mirroredGroupWidth - appliedMirroredGroupWidth) < 0.5
                );
        if (sameGeometry) return;

        layoutApplied = true;
        appliedMode = mode;
        appliedStacked = stacked;
        appliedMirroredGroupWidth = mirroredGroupWidth;

        PopupLayoutSupport.resetGridConstraints(scopeButtons);
        PopupLayoutSupport.resetGridConstraints(typeControl);
        PopupLayoutSupport.resetGridConstraints(rightGroup);
        getColumnConstraints().clear();

        if (stacked) {
            ColumnConstraints flexible = new ColumnConstraints();
            flexible.setHgrow(Priority.ALWAYS);
            flexible.setFillWidth(true);
            getColumnConstraints().setAll(flexible);

            configureFullWidth(scopeButtons, 0);
            configureFullWidth(typeControl, 1);
            configureFullWidth(rightGroup, 2);

            scopeButtons.setPrefWidth(0);
            scopeButtons.setMaxWidth(Double.MAX_VALUE);
            typeControl.setPrefWidth(0);
            typeControl.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(tagControl, Priority.ALWAYS);
            tagControl.setMinWidth(0);
            tagControl.setPrefWidth(0);
            tagControl.setMaxWidth(Double.MAX_VALUE);
            rightGroup.setPrefWidth(0);
            rightGroup.setMaxWidth(Double.MAX_VALUE);
        } else {
            double mirroredControlWidth = PopupResponsivePolicy.mirroredFilterControlWidth(width);

            ColumnConstraints scope = fixed(mirroredGroupWidth);
            ColumnConstraints breathing = flexible();
            ColumnConstraints type = fixed(mirroredControlWidth);
            ColumnConstraints tag = fixed(mirroredControlWidth);
            getColumnConstraints().setAll(scope, breathing, type, tag);

            scopeButtons.setMinWidth(0);
            scopeButtons.setPrefWidth(mirroredGroupWidth);
            scopeButtons.setMaxWidth(mirroredGroupWidth);
            GridPane.setRowIndex(scopeButtons, 0);
            GridPane.setColumnIndex(scopeButtons, 0);
            GridPane.setHgrow(scopeButtons, Priority.NEVER);
            GridPane.setHalignment(scopeButtons, HPos.LEFT);

            typeControl.setMinWidth(0);
            typeControl.setPrefWidth(mirroredControlWidth);
            typeControl.setMaxWidth(mirroredControlWidth);
            GridPane.setRowIndex(typeControl, 0);
            GridPane.setColumnIndex(typeControl, 2);
            GridPane.setHgrow(typeControl, Priority.NEVER);
            GridPane.setHalignment(typeControl, HPos.RIGHT);

            HBox.setHgrow(tagControl, Priority.ALWAYS);
            tagControl.setMinWidth(0);
            tagControl.setPrefWidth(0);
            tagControl.setMaxWidth(Double.MAX_VALUE);
            rightGroup.setMinWidth(0);
            rightGroup.setPrefWidth(mirroredControlWidth);
            rightGroup.setMaxWidth(mirroredControlWidth);
            GridPane.setRowIndex(rightGroup, 0);
            GridPane.setColumnIndex(rightGroup, 3);
            GridPane.setHgrow(rightGroup, Priority.NEVER);
            GridPane.setHalignment(rightGroup, HPos.RIGHT);
        }

        PopupLayoutSupport.applyResponsiveClass(this, mode);
    }

    private ColumnConstraints fixed(double width) {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setMinWidth(width);
        constraints.setPrefWidth(width);
        constraints.setMaxWidth(width);
        constraints.setHgrow(Priority.NEVER);
        constraints.setFillWidth(true);
        return constraints;
    }

    private ColumnConstraints flexible() {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setMinWidth(0);
        constraints.setHgrow(Priority.ALWAYS);
        constraints.setFillWidth(true);
        return constraints;
    }

    private void configureFullWidth(Region control, int row) {
        GridPane.setRowIndex(control, row);
        GridPane.setColumnIndex(control, 0);
        GridPane.setHgrow(control, Priority.ALWAYS);
        control.setMinWidth(0);
        control.setMaxWidth(Double.MAX_VALUE);
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
        control.disableProperty().bind(combo.disableProperty());

        combo.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(combo, Pos.CENTER_LEFT);

        SvgIcon leadingIcon = SvgIcon.of(icon, 15, iconStyleClass);
        StackPane.setAlignment(leadingIcon, Pos.CENTER_LEFT);
        StackPane.setMargin(leadingIcon, new Insets(0, 0, 0, 11));

        control.getChildren().setAll(combo, leadingIcon);
        return control;
    }
}

