/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui;

import io.xseries.xclip.system.window.WindowsTitleBar;
import io.xseries.xclip.util.TextValues;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Central factory for XClip modal surfaces.
 *
 * All dialogs share the same owner/modality rules, dark title bar, stylesheet
 * profile, button semantics, and content hierarchy. Destructive confirmations
 * deliberately keep Cancel as the default button to avoid accidental deletion
 * through an unintentional Enter press.
 */
public final class UiDialogs {

    enum Tone {
        STANDARD("dialog-standard"),
        DANGER("dialog-danger"),
        ERROR("dialog-error"),
        SUCCESS("dialog-success");

        private final String styleClass;

        Tone(String styleClass) {
            this.styleClass = styleClass;
        }
    }

    record DialogCopy(
            String windowTitle,
            String eyebrow,
            String heading,
            String body,
            String actionLabel
    ) {
        DialogCopy {
            windowTitle = TextValues.requireNonBlank(windowTitle, "windowTitle");
            eyebrow = TextValues.requireNonBlank(eyebrow, "eyebrow");
            heading = TextValues.requireNonBlank(heading, "heading");
            body = TextValues.requireNonBlank(body, "body");
            actionLabel = TextValues.requireNonBlank(actionLabel, "actionLabel");
        }
    }

    private UiDialogs() {}

    public static Optional<String> promptPinnedTitle(
            Stage owner,
            String currentTitle,
            int maxLength
    ) {
        Objects.requireNonNull(owner, "owner");
        if (maxLength <= 0) throw new IllegalArgumentException("maxLength must be positive");

        String initial = normalizeTitle(currentTitle, maxLength);
        Dialog<String> dialog = new Dialog<>();
        ButtonType saveType = new ButtonType("Save title", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.setTitle("Rename pinned clip");
        dialog.getDialogPane().getButtonTypes().setAll(cancelType, saveType);
        configure(dialog, owner, Tone.STANDARD, "rename-dialog");

        Label eyebrow = label("PINNED CLIP", "dialog-eyebrow");
        Label heading = label("Rename pinned clip", "dialog-heading");
        Label description = label(
                "Add a short title without changing the original clipboard content.",
                "dialog-description"
        );
        description.setWrapText(true);

        Label fieldLabel = label("Title", "dialog-field-label");
        Label counter = label("0 / " + maxLength, "dialog-character-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox fieldHeader = new HBox(8, fieldLabel, spacer, counter);
        fieldHeader.setAlignment(Pos.CENTER_LEFT);

        TextField editor = new TextField(initial);
        editor.setPromptText("Example: XCC release checklist");
        editor.getStyleClass().add("dialog-text-field");
        editor.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= maxLength ? change : null
        ));

        Label hint = label(
                "Leave the field empty to clear the current title.",
                "dialog-hint"
        );
        hint.setWrapText(true);

        VBox content = new VBox(10, eyebrow, heading, description, fieldHeader, editor, hint);
        content.getStyleClass().add("dialog-content");
        dialog.getDialogPane().setContent(content);

        Node saveNode = dialog.getDialogPane().lookupButton(saveType);
        Node cancelNode = dialog.getDialogPane().lookupButton(cancelType);
        styleActionButton(saveNode, "dialog-primary-button", true);
        styleActionButton(cancelNode, "dialog-secondary-button", false);

        Runnable refresh = () -> {
            String raw = editor.getText() == null ? "" : editor.getText();
            String normalized = normalizeTitle(raw, maxLength);
            counter.setText(raw.length() + " / " + maxLength);
            saveNode.setDisable(normalized.equals(initial));
        };
        editor.textProperty().addListener((obs, oldValue, newValue) -> refresh.run());
        refresh.run();

        dialog.setResultConverter(button ->
                button == saveType ? normalizeTitle(editor.getText(), maxLength) : null
        );
        dialog.setOnShown(event -> {
            applyDarkTitleBar(dialog);
            editor.requestFocus();
            editor.selectAll();
        });

        return dialog.showAndWait();
    }

    public static boolean confirmBatchDelete(
            Stage owner,
            int selectedCount,
            int pinnedCount
    ) {
        return confirmDanger(owner, batchDeleteCopy(selectedCount, pinnedCount));
    }

    public static boolean confirmClearVisible(Stage owner, int visibleCount) {
        return confirmDanger(owner, clearVisibleCopy(visibleCount));
    }

    public static boolean confirmClearRecent(Stage owner) {
        return confirmDanger(owner, clearRecentCopy());
    }

    public static boolean confirmClearAllData(Stage owner, Path dataDirectory) {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        DialogCopy copy = new DialogCopy(
                "Clear all XClip data",
                "DESTRUCTIVE ACTION",
                "Delete all local XClip data?",
                "Clipboard history and settings will be permanently removed.\n\n"
                        + "Data folder: " + dataDirectory.toAbsolutePath(),
                "Delete all data"
        );
        return confirmDanger(owner, copy);
    }

    public static boolean confirmOptimizeDatabase(Stage owner) {
        return confirmStandard(owner, optimizeDatabaseCopy());
    }

    public static boolean confirmRestoreBackup(
            Stage owner,
            Path backupPath,
            String backupSummary
    ) {
        Objects.requireNonNull(backupPath, "backupPath");
        String summary = Objects.requireNonNullElse(
                backupSummary,
                "Backup metadata unavailable"
        );
        return confirmDanger(
                owner,
                restoreBackupCopy(backupPath, summary)
        );
    }

    public static void showError(
            Stage owner,
            String windowTitle,
            String heading,
            String body
    ) {
        showMessage(
                owner,
                Tone.ERROR,
                new DialogCopy(windowTitle, "ACTION FAILED", heading, body, "Close")
        );
    }

    public static void showInformation(
            Stage owner,
            String windowTitle,
            String heading,
            String body
    ) {
        showMessage(
                owner,
                Tone.SUCCESS,
                new DialogCopy(windowTitle, "COMPLETED", heading, body, "Continue")
        );
    }

    static DialogCopy batchDeleteCopy(int selectedCount, int pinnedCount) {
        if (selectedCount < 2) {
            throw new IllegalArgumentException("Batch delete requires at least two clips");
        }
        if (pinnedCount < 0 || pinnedCount > selectedCount) {
            throw new IllegalArgumentException("Invalid pinnedCount");
        }

        String pinned = pinnedCount == 0
                ? ""
                : " This selection includes " + pinnedCount + " pinned "
                + plural(pinnedCount, "clip", "clips") + ".";

        return new DialogCopy(
                "Delete selected clips",
                "DESTRUCTIVE ACTION",
                "Delete " + selectedCount + " selected clips?",
                "The selected clipboard entries will be permanently deleted."
                        + pinned + " This cannot be undone.",
                "Delete " + selectedCount + " clips"
        );
    }

    static DialogCopy clearRecentCopy() {
        return new DialogCopy(
                "Clear RECENT history",
                "DESTRUCTIVE ACTION",
                "Delete all RECENT clips?",
                "Every unpinned clipboard entry will be permanently deleted. "
                        + "PINNED clips, tags, settings, and retention rules stay intact.",
                "Clear RECENT"
        );
    }

    static DialogCopy optimizeDatabaseCopy() {
        return new DialogCopy(
                "Optimize XClip database",
                "DATABASE MAINTENANCE",
                "Optimize the local database now?",
                "XClip will pause clipboard capture, checkpoint the WAL, run VACUUM, "
                        + "and refresh SQLite planner statistics. The operation can take "
                        + "longer on a large history.",
                "Optimize database"
        );
    }

    static DialogCopy restoreBackupCopy(
            Path backupPath,
            String backupSummary
    ) {
        Objects.requireNonNull(backupPath, "backupPath");
        String summary = Objects.requireNonNullElse(
                backupSummary,
                "Backup metadata unavailable"
        );
        return new DialogCopy(
                "Restore XClip backup",
                "DESTRUCTIVE ACTION",
                "Replace local history and settings?",
                summary + "\n\nBackup: " + backupPath.toAbsolutePath()
                        + "\n\nCurrent clipboard history, PINNED clips, tags, and "
                        + "settings will be replaced. XClip exits after a successful restore.",
                "Restore backup"
        );
    }

    static DialogCopy clearVisibleCopy(int visibleCount) {
        if (visibleCount <= 0) {
            throw new IllegalArgumentException("visibleCount must be positive");
        }

        return new DialogCopy(
                "Clear visible history",
                "DESTRUCTIVE ACTION",
                "Clear " + visibleCount + " visible " + plural(visibleCount, "clip", "clips") + "?",
                "Only non-pinned clips currently shown by the active search and filters "
                        + "will be deleted. Pinned clips and hidden clips stay untouched.",
                "Clear " + visibleCount + " " + plural(visibleCount, "clip", "clips")
        );
    }

    static String normalizeTitle(String value, int maxLength) {
        if (maxLength <= 0) throw new IllegalArgumentException("maxLength must be positive");
        String normalized = Objects.requireNonNullElse(value, "").trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength).trim();
    }

    private static boolean confirmDanger(Stage owner, DialogCopy copy) {
        return confirm(
                owner,
                copy,
                Tone.DANGER,
                "dialog-danger-button"
        );
    }

    private static boolean confirmStandard(Stage owner, DialogCopy copy) {
        return confirm(
                owner,
                copy,
                Tone.STANDARD,
                "dialog-primary-button"
        );
    }

    private static boolean confirm(
            Stage owner,
            DialogCopy copy,
            Tone tone,
            String actionStyleClass
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(copy, "copy");

        Dialog<Boolean> dialog = new Dialog<>();
        ButtonType actionType = new ButtonType(copy.actionLabel(), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.setTitle(copy.windowTitle());
        dialog.getDialogPane().getButtonTypes().setAll(cancelType, actionType);
        configure(dialog, owner, tone, "confirmation-dialog");
        dialog.getDialogPane().setContent(buildCopyContent(copy));

        Node actionNode = dialog.getDialogPane().lookupButton(actionType);
        Node cancelNode = dialog.getDialogPane().lookupButton(cancelType);
        styleActionButton(actionNode, actionStyleClass, false);
        styleActionButton(cancelNode, "dialog-secondary-button", true);

        dialog.setResultConverter(button -> button == actionType);
        dialog.setOnShown(event -> {
            applyDarkTitleBar(dialog);
            cancelNode.requestFocus();
        });
        return dialog.showAndWait().orElse(false);
    }

    private static void showMessage(Stage owner, Tone tone, DialogCopy copy) {
        Objects.requireNonNull(owner, "owner");
        Dialog<Void> dialog = new Dialog<>();
        ButtonType closeType = new ButtonType(copy.actionLabel(), ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.setTitle(copy.windowTitle());
        dialog.getDialogPane().getButtonTypes().setAll(closeType);
        configure(dialog, owner, tone, "message-dialog");
        dialog.getDialogPane().setContent(buildCopyContent(copy));

        Node closeNode = dialog.getDialogPane().lookupButton(closeType);
        styleActionButton(closeNode, "dialog-primary-button", true);
        dialog.setOnShown(event -> {
            applyDarkTitleBar(dialog);
            closeNode.requestFocus();
        });
        dialog.showAndWait();
    }

    private static VBox buildCopyContent(DialogCopy copy) {
        Label eyebrow = label(copy.eyebrow(), "dialog-eyebrow");
        Label heading = label(copy.heading(), "dialog-heading");
        heading.setWrapText(true);
        Label body = label(copy.body(), "dialog-description");
        body.setWrapText(true);

        VBox content = new VBox(10, eyebrow, heading, body);
        content.setPadding(Insets.EMPTY);
        content.getStyleClass().add("dialog-content");
        return content;
    }

    private static void configure(
            Dialog<?> dialog,
            Stage owner,
            Tone tone,
            String specificStyleClass
    ) {
        Objects.requireNonNull(dialog, "dialog");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(tone, "tone");

        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(false);

        DialogPane pane = dialog.getDialogPane();
        pane.setHeaderText(null);
        pane.setGraphic(null);
        UiStyles.applyDialog(pane);
        pane.getStyleClass().addAll("x-dialog", tone.styleClass, specificStyleClass);
        pane.setMinWidth(430);
        pane.setPrefWidth(460);
    }

    private static void styleActionButton(Node node, String styleClass, boolean defaultButton) {
        if (node == null) return;
        node.getStyleClass().add(styleClass);
        if (node instanceof Button button) {
            button.setDefaultButton(defaultButton);
        }
    }

    private static void applyDarkTitleBar(Dialog<?> dialog) {
        Object window = dialog.getDialogPane().getScene().getWindow();
        if (window instanceof Stage dialogStage) {
            WindowsTitleBar.applyDarkTitleBar(dialogStage);
        }
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static String plural(int count, String singular, String plural) {
        return count == 1 ? singular : plural;
    }

}


