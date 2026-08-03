/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui;

import io.xseries.xclip.data.model.ClipTag;
import io.xseries.xclip.domain.service.TagNamePolicy;
import io.xseries.xclip.system.window.WindowsTitleBar;
import io.xseries.xclip.ui.components.SvgIcon;
import io.xseries.xclip.ui.components.UiIcon;
import io.xseries.xclip.ui.popup.TagEditorModel;
import io.xseries.xclip.ui.popup.TagEditorModel.AddResult;
import io.xseries.xclip.ui.popup.TagEditorModel.EditPlan;
import io.xseries.xclip.ui.popup.TagEditorModel.SelectionState;
import io.xseries.xclip.ui.popup.TagEditorModel.TagOption;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Modal editor for assigning tags to one clip or a multi-selection.
 *
 * No persistence happens while the dialog is open. The returned edit plan is
 * applied by TagDao in one transaction after the user explicitly saves.
 */
public final class TagEditorDialog {

    private TagEditorDialog() {}

    public static Optional<EditPlan> show(
            Stage owner,
            Collection<Long> clipIds,
            Collection<ClipTag> allTags,
            Map<Long, ? extends Collection<ClipTag>> assignmentsByClip
    ) {
        Objects.requireNonNull(owner, "owner");
        TagEditorModel model = TagEditorModel.create(clipIds, allTags, assignmentsByClip);

        Dialog<EditPlan> dialog = new Dialog<>();
        ButtonType saveType = new ButtonType("Save tags", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.setTitle(model.clipCount() == 1 ? "Edit clip tags" : "Edit tags for selected clips");
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(true);

        DialogPane pane = dialog.getDialogPane();
        pane.setHeaderText(null);
        pane.setGraphic(null);
        pane.getButtonTypes().setAll(cancelType, saveType);
        UiStyles.applyDialog(pane);
        pane.getStyleClass().addAll("x-dialog", "dialog-standard", "tag-editor-dialog");
        pane.setMinWidth(500);
        pane.setPrefWidth(540);
        pane.setMinHeight(430);
        pane.setPrefHeight(540);

        Label eyebrow = label("TAGS", "dialog-eyebrow");
        Label heading = label(
                model.clipCount() == 1
                        ? "Organize this clip"
                        : "Organize " + model.clipCount() + " selected clips",
                "dialog-heading"
        );
        Label description = label(
                model.clipCount() == 1
                        ? "Select existing tags or create a new tag. Changes are saved only after confirmation."
                        : "Checked assigns a tag to every selected clip, unchecked removes it from every clip, and mixed leaves existing differences unchanged.",
                "dialog-description"
        );
        description.setWrapText(true);

        Label createLabel = label("Create and assign", "dialog-field-label");
        Label characterCount = label("0 / " + TagNamePolicy.MAX_NAME_LENGTH, "dialog-character-count");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox createHeader = new HBox(8, createLabel, headerSpacer, characterCount);
        createHeader.setAlignment(Pos.CENTER_LEFT);

        TextField editor = new TextField();
        editor.setPromptText("Example: Work");
        editor.setAccessibleText("New tag name");
        editor.setAccessibleHelp("Enter a tag name and activate Add. Maximum 64 characters.");
        editor.getStyleClass().add("dialog-text-field");
        HBox.setHgrow(editor, Priority.ALWAYS);

        Button addButton = new Button("Add");
        addButton.setGraphic(SvgIcon.of(UiIcon.PLUS, 14, "tag-editor-button-icon"));
        addButton.setAccessibleText("Add new tag");
        addButton.getStyleClass().addAll("tag-editor-add-button", "dialog-secondary-button");

        HBox createRow = new HBox(8, editor, addButton);
        createRow.setAlignment(Pos.CENTER_LEFT);

        Label validation = label("", "tag-editor-validation");
        validation.setWrapText(true);
        validation.setVisible(false);
        validation.setManaged(false);

        Label pendingLabel = label("New tags to create", "dialog-field-label");
        VBox pendingRows = new VBox(6);
        pendingRows.getStyleClass().add("tag-editor-pending-list");
        VBox pendingSection = new VBox(7, pendingLabel, pendingRows);
        pendingSection.setVisible(false);
        pendingSection.setManaged(false);

        Label availableLabel = label("Available tags", "dialog-field-label");
        VBox optionRows = new VBox(3);
        optionRows.getStyleClass().add("tag-editor-option-list");
        Map<Long, CheckBox> checkBoxes = new LinkedHashMap<>();
        Map<Long, Label> stateHints = new LinkedHashMap<>();

        for (TagOption option : model.options()) {
            CheckBox checkBox = new CheckBox(option.name());
            checkBox.setAllowIndeterminate(false);
            checkBox.setAccessibleText("Tag " + option.name());
            checkBox.setAccessibleHelp(model.clipCount() == 1
                    ? "Toggle assignment for this clip."
                    : "Checked assigns to all, unchecked removes from all, mixed keeps existing differences.");
            applyState(checkBox, option.currentState());
            checkBox.getStyleClass().add("tag-editor-check-box");

            Label stateHint = new Label();
            stateHint.getStyleClass().add("tag-editor-state-hint");
            stateHint.setMouseTransparent(true);
            refreshStateHint(stateHint, option.currentState(), model.clipCount());

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(8, checkBox, spacer, stateHint);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("tag-editor-option-row");

            checkBox.setOnAction(event -> {
                checkBox.setIndeterminate(false);
                SelectionState state = checkBox.isSelected()
                        ? SelectionState.ASSIGNED
                        : SelectionState.UNASSIGNED;
                model.setState(option.id(), state);
                refreshStateHint(stateHint, state, model.clipCount());
                refreshSaveButton(pane, saveType, model);
            });

            checkBoxes.put(option.id(), checkBox);
            stateHints.put(option.id(), stateHint);
            optionRows.getChildren().add(row);
        }

        if (optionRows.getChildren().isEmpty()) {
            Label empty = label(
                    "No tags exist yet. Create the first tag above.",
                    "tag-editor-empty"
            );
            empty.setWrapText(true);
            optionRows.getChildren().add(empty);
        }

        ScrollPane optionsScroll = new ScrollPane(optionRows);
        optionsScroll.setFitToWidth(true);
        optionsScroll.setPannable(true);
        optionsScroll.setMinHeight(150);
        optionsScroll.setPrefHeight(220);
        optionsScroll.setMaxHeight(280);
        optionsScroll.getStyleClass().add("tag-editor-scroll");

        VBox content = new VBox(
                10,
                eyebrow,
                heading,
                description,
                createHeader,
                createRow,
                validation,
                pendingSection,
                availableLabel,
                optionsScroll
        );
        content.setPadding(Insets.EMPTY);
        content.getStyleClass().addAll("dialog-content", "tag-editor-content");
        pane.setContent(content);

        Node saveNode = pane.lookupButton(saveType);
        Node cancelNode = pane.lookupButton(cancelType);
        saveNode.getStyleClass().add("dialog-primary-button");
        cancelNode.getStyleClass().add("dialog-secondary-button");
        if (saveNode instanceof Button saveButton) saveButton.setDefaultButton(true);

        Runnable clearValidation = () -> {
            validation.setText("");
            validation.setVisible(false);
            validation.setManaged(false);
        };
        java.util.function.Consumer<String> showValidation = message -> {
            validation.setText(Objects.requireNonNullElse(message, ""));
            boolean visible = !validation.getText().isBlank();
            validation.setVisible(visible);
            validation.setManaged(visible);
        };

        Runnable refreshPending = () -> {
            pendingRows.getChildren().clear();
            for (String name : model.pendingTagNames()) {
                Label nameLabel = new Label(name);
                nameLabel.getStyleClass().add("tag-editor-pending-name");

                Label newBadge = new Label("NEW");
                newBadge.getStyleClass().add("tag-editor-new-badge");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Button remove = new Button();
                remove.setGraphic(SvgIcon.of(UiIcon.X, 12, "tag-editor-button-icon"));
                remove.setAccessibleText("Remove pending tag " + name);
                remove.getStyleClass().add("tag-editor-remove-button");
                remove.setOnAction(event -> {
                    model.removePendingTag(name);
                    rebuildPendingRows(pendingRows, pendingSection, model, pane, saveType);
                });

                HBox row = new HBox(8, nameLabel, newBadge, spacer, remove);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("tag-editor-pending-row");
                pendingRows.getChildren().add(row);
            }
            boolean visible = !model.pendingTagNames().isEmpty();
            pendingSection.setVisible(visible);
            pendingSection.setManaged(visible);
            refreshSaveButton(pane, saveType, model);
        };

        Runnable addTag = () -> {
            String raw = editor.getText();
            try {
                AddResult result = model.addPendingTag(raw);
                clearValidation.run();

                switch (result) {
                    case ADDED -> {
                        editor.clear();
                        refreshPending.run();
                    }
                    case SELECTED_EXISTING -> {
                        syncCheckBoxes(model, checkBoxes, stateHints);
                        editor.clear();
                        showValidation.accept("An existing tag with this name was selected.");
                    }
                    case ALREADY_ASSIGNED ->
                            showValidation.accept("This tag is already assigned to the selection.");
                    case ALREADY_PENDING ->
                            showValidation.accept("This new tag is already waiting to be created.");
                }
                refreshSaveButton(pane, saveType, model);
            } catch (IllegalArgumentException invalid) {
                showValidation.accept(invalid.getMessage());
            }
        };

        addButton.setOnAction(event -> addTag.run());
        editor.setOnAction(event -> addTag.run());
        editor.textProperty().addListener((obs, oldValue, newValue) -> {
            String raw = Objects.requireNonNullElse(newValue, "");
            characterCount.setText(raw.length() + " / " + TagNamePolicy.MAX_NAME_LENGTH);
            addButton.setDisable(raw.isBlank());
            if (raw.isBlank()) {
                clearValidation.run();
            } else {
                try {
                    TagNamePolicy.normalize(raw);
                    clearValidation.run();
                } catch (IllegalArgumentException invalid) {
                    showValidation.accept(invalid.getMessage());
                }
            }
        });
        addButton.setDisable(true);
        refreshSaveButton(pane, saveType, model);

        dialog.setResultConverter(button -> button == saveType ? model.plan() : null);
        dialog.setOnShown(event -> {
            Object window = pane.getScene().getWindow();
            if (window instanceof Stage dialogStage) {
                WindowsTitleBar.applyDarkTitleBar(dialogStage);
                dialogStage.setMinWidth(500);
                dialogStage.setMinHeight(430);
            }
            Platform.runLater(editor::requestFocus);
        });

        return dialog.showAndWait().filter(plan -> !plan.isEmpty());
    }

    private static void rebuildPendingRows(
            VBox pendingRows,
            VBox pendingSection,
            TagEditorModel model,
            DialogPane pane,
            ButtonType saveType
    ) {
        pendingRows.getChildren().clear();
        for (String name : model.pendingTagNames()) {
            Label nameLabel = new Label(name);
            nameLabel.getStyleClass().add("tag-editor-pending-name");
            Label badge = new Label("NEW");
            badge.getStyleClass().add("tag-editor-new-badge");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button remove = new Button();
            remove.setGraphic(SvgIcon.of(UiIcon.X, 12, "tag-editor-button-icon"));
            remove.setAccessibleText("Remove pending tag " + name);
            remove.getStyleClass().add("tag-editor-remove-button");
            remove.setOnAction(event -> {
                model.removePendingTag(name);
                rebuildPendingRows(pendingRows, pendingSection, model, pane, saveType);
            });
            HBox row = new HBox(8, nameLabel, badge, spacer, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("tag-editor-pending-row");
            pendingRows.getChildren().add(row);
        }
        boolean visible = !model.pendingTagNames().isEmpty();
        pendingSection.setVisible(visible);
        pendingSection.setManaged(visible);
        refreshSaveButton(pane, saveType, model);
    }

    private static void syncCheckBoxes(
            TagEditorModel model,
            Map<Long, CheckBox> checkBoxes,
            Map<Long, Label> stateHints
    ) {
        for (TagOption option : model.options()) {
            CheckBox checkBox = checkBoxes.get(option.id());
            if (checkBox != null) applyState(checkBox, option.currentState());
            Label stateHint = stateHints.get(option.id());
            if (stateHint != null) {
                refreshStateHint(stateHint, option.currentState(), model.clipCount());
            }
        }
    }

    private static void applyState(CheckBox checkBox, SelectionState state) {
        checkBox.setIndeterminate(state == SelectionState.MIXED);
        checkBox.setSelected(state == SelectionState.ASSIGNED);
    }

    private static void refreshStateHint(
            Label label,
            SelectionState state,
            int clipCount
    ) {
        label.setText(clipCount > 1 && state == SelectionState.MIXED
                ? "MIXED — unchanged"
                : "");
        boolean visible = !label.getText().isBlank();
        label.setVisible(visible);
        label.setManaged(visible);
    }

    private static void refreshSaveButton(
            DialogPane pane,
            ButtonType saveType,
            TagEditorModel model
    ) {
        Node saveNode = pane.lookupButton(saveType);
        if (saveNode != null) saveNode.setDisable(!model.hasChanges());
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
