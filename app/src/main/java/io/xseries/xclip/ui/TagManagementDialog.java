/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui;

import io.xseries.xclip.data.model.TagSummary;
import io.xseries.xclip.domain.service.TagNamePolicy;
import io.xseries.xclip.system.window.WindowsTitleBar;
import io.xseries.xclip.ui.components.SvgIcon;
import io.xseries.xclip.ui.components.UiIcon;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
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
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Global tag-management surface.
 *
 * Database work is supplied by the popup and completes asynchronously. The
 * dialog never owns a JDBC connection and never exposes clipboard content.
 */
public final class TagManagementDialog {

    public record Result(boolean changed, List<TagSummary> tags) {
        public Result {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    public interface Actions {
        CompletionStage<List<TagSummary>> rename(long tagId, String newName);
        CompletionStage<List<TagSummary>> delete(long tagId);
        CompletionStage<List<TagSummary>> cleanupUnused();
    }

    private TagManagementDialog() {}

    public static Result show(
            Stage owner,
            List<TagSummary> initialTags,
            Actions actions
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(actions, "actions");

        Dialog<Void> dialog = new Dialog<>();
        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.setTitle("Manage tags");
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(true);

        DialogPane pane = dialog.getDialogPane();
        pane.setHeaderText(null);
        pane.setGraphic(null);
        pane.getButtonTypes().setAll(closeType);
        UiStyles.applyDialog(pane);
        pane.getStyleClass().addAll("x-dialog", "dialog-standard", "tag-management-dialog");
        pane.setMinWidth(560);
        pane.setPrefWidth(620);
        pane.setMinHeight(430);
        pane.setPrefHeight(560);

        Label eyebrow = label("TAGS", "dialog-eyebrow");
        Label heading = label("Manage tag library", "dialog-heading");
        Label description = label(
                "Rename tags, review assignment counts, remove individual tags, or clean up tags that are no longer used.",
                "dialog-description"
        );
        description.setWrapText(true);

        Label summary = label("", "tag-management-summary");
        Button cleanupButton = new Button("Clean up unused");
        cleanupButton.setGraphic(SvgIcon.of(UiIcon.TRASH_2, 14, "tag-management-button-icon"));
        cleanupButton.getStyleClass().addAll("dialog-secondary-button", "tag-management-cleanup-button");
        cleanupButton.setAccessibleText("Clean up unused tags");
        cleanupButton.setAccessibleHelp("Delete every tag that has no clip assignments after confirmation.");

        Region toolbarSpacer = new Region();
        HBox.setHgrow(toolbarSpacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, summary, toolbarSpacer, cleanupButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("tag-management-toolbar");

        VBox rows = new VBox(5);
        rows.getStyleClass().add("tag-management-list");

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        scroll.setMinHeight(240);
        scroll.setPrefHeight(360);
        scroll.getStyleClass().add("tag-management-scroll");

        Label status = label("", "tag-management-status");
        status.setWrapText(true);
        status.setVisible(false);
        status.setManaged(false);

        VBox content = new VBox(11, eyebrow, heading, description, toolbar, scroll, status);
        content.setPadding(Insets.EMPTY);
        content.getStyleClass().addAll("dialog-content", "tag-management-content");
        pane.setContent(content);

        Node closeNode = pane.lookupButton(closeType);
        closeNode.getStyleClass().add("dialog-secondary-button");

        List<TagSummary> current = new ArrayList<>();
        AtomicBoolean changed = new AtomicBoolean(false);
        AtomicBoolean busy = new AtomicBoolean(false);

        Runnable hideStatus = () -> {
            status.setText("");
            status.getStyleClass().removeAll("success", "error", "neutral");
            status.setVisible(false);
            status.setManaged(false);
        };
        Consumer<String> showNeutral = message -> showStatus(status, message, "neutral");
        Consumer<String> showSuccess = message -> showStatus(status, message, "success");
        Consumer<String> showError = message -> showStatus(status, message, "error");

        Runnable[] renderRef = new Runnable[1];
        Consumer<Boolean> setBusy = value -> {
            busy.set(value);
            rows.setDisable(value);
            cleanupButton.setDisable(value || unusedCount(current) == 0);
            closeNode.setDisable(value);
        };

        java.util.function.BiConsumer<List<TagSummary>, String> replaceAndRender = (tags, message) -> {
            current.clear();
            if (tags != null) current.addAll(sorted(tags));
            renderRef[0].run();
            if (message == null || message.isBlank()) hideStatus.run();
            else showSuccess.accept(message);
        };

        java.util.function.BiConsumer<CompletionStage<List<TagSummary>>, String> await = (operation, successMessage) -> {
            setBusy.accept(true);
            showNeutral.accept("Saving tag changes…");
            operation.whenComplete((tags, failure) -> Platform.runLater(() -> {
                setBusy.accept(false);
                if (failure != null) {
                    showError.accept(errorMessage(failure));
                    return;
                }
                changed.set(true);
                replaceAndRender.accept(tags, successMessage);
            }));
        };

        renderRef[0] = () -> {
            rows.getChildren().clear();
            int unused = unusedCount(current);
            summary.setText(current.size() + (current.size() == 1 ? " tag" : " tags")
                    + "  •  " + unused + " unused");
            cleanupButton.setDisable(busy.get() || unused == 0);

            if (current.isEmpty()) {
                Label empty = label(
                        "No tags exist yet. Create a tag from a clip's Tags… editor.",
                        "tag-management-empty"
                );
                empty.setWrapText(true);
                rows.getChildren().add(empty);
                return;
            }

            for (TagSummary tag : current) {
                rows.getChildren().add(buildRow(
                        pane,
                        owner,
                        tag,
                        actions,
                        await,
                        showError
                ));
            }
        };

        cleanupButton.setOnAction(event -> {
            int unused = unusedCount(current);
            if (unused <= 0 || busy.get()) return;

            boolean confirmed = confirm(
                    pane,
                    owner,
                    "Clean up unused tags",
                    "Delete " + unused + (unused == 1 ? " unused tag?" : " unused tags?"),
                    "Only tags with zero clip assignments will be removed. Clipboard history and assigned tags remain unchanged.",
                    "Delete unused"
            );
            if (!confirmed) return;

            await.accept(
                    actions.cleanupUnused(),
                    unused == 1 ? "Unused tag removed" : unused + " unused tags removed"
            );
        });

        replaceAndRender.accept(initialTags, null);

        dialog.setOnShown(event -> {
            Window window = pane.getScene() == null ? null : pane.getScene().getWindow();
            if (window instanceof Stage stage) WindowsTitleBar.applyDarkTitleBar(stage);
            Platform.runLater(cleanupButton::requestFocus);
        });

        dialog.showAndWait();
        return new Result(changed.get(), current);
    }

    private static Node buildRow(
            DialogPane pane,
            Stage owner,
            TagSummary tag,
            Actions actions,
            java.util.function.BiConsumer<CompletionStage<List<TagSummary>>, String> await,
            Consumer<String> showError
    ) {
        Label name = label(tag.name(), "tag-management-name");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        Label usage = label(usageText(tag.usageCount()), "tag-management-usage");
        if (tag.unused()) usage.getStyleClass().add("unused");

        Button renameButton = iconButton(UiIcon.PENCIL, "Rename " + tag.name(), "tag-management-rename-button");
        Button deleteButton = iconButton(UiIcon.TRASH_2, "Delete " + tag.name(), "tag-management-delete-button");
        deleteButton.getStyleClass().add("danger");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox displayRow = new HBox(9, name, spacer, usage, renameButton, deleteButton);
        displayRow.setAlignment(Pos.CENTER_LEFT);

        VBox shell = new VBox(6, displayRow);
        shell.getStyleClass().add("tag-management-row");

        renameButton.setOnAction(event -> {
            TextField editor = new TextField(tag.name());
            editor.setAccessibleText("Rename tag " + tag.name());
            editor.setAccessibleHelp("Enter a unique tag name up to 64 characters.");
            editor.getStyleClass().add("dialog-text-field");
            HBox.setHgrow(editor, Priority.ALWAYS);

            Button save = new Button("Save");
            save.getStyleClass().addAll("dialog-primary-button", "tag-management-inline-save");
            Button cancel = new Button("Cancel");
            cancel.getStyleClass().addAll("dialog-secondary-button", "tag-management-inline-cancel");

            HBox editRow = new HBox(8, editor, save, cancel);
            editRow.setAlignment(Pos.CENTER_LEFT);
            editRow.getStyleClass().add("tag-management-edit-row");
            shell.getChildren().setAll(editRow);

            Runnable cancelEdit = () -> shell.getChildren().setAll(displayRow);
            cancel.setOnAction(cancelEvent -> cancelEdit.run());

            Runnable saveEdit = () -> {
                String normalized;
                try {
                    normalized = TagNamePolicy.normalize(editor.getText()).displayName();
                } catch (IllegalArgumentException invalid) {
                    showError.accept(Objects.requireNonNullElse(
                            invalid.getMessage(),
                            "The tag name is invalid."
                    ));
                    editor.requestFocus();
                    return;
                }

                if (normalized.equals(tag.name())) {
                    cancelEdit.run();
                    return;
                }

                await.accept(actions.rename(tag.id(), normalized), "Tag renamed");
            };
            save.setOnAction(saveEvent -> saveEdit.run());
            editor.setOnAction(saveEvent -> saveEdit.run());
            Platform.runLater(() -> {
                editor.selectAll();
                editor.requestFocus();
            });
        });

        deleteButton.setOnAction(event -> {
            boolean confirmed = confirm(
                    pane,
                    owner,
                    "Delete tag",
                    "Delete tag “" + tag.name() + "”?",
                    tag.unused()
                            ? "This unused tag will be removed permanently."
                            : "This removes the tag from " + usageText(tag.usageCount())
                                    + ". Clipboard content remains unchanged.",
                    "Delete tag"
            );
            if (!confirmed) return;

            await.accept(actions.delete(tag.id()), "Tag deleted");
        });

        return shell;
    }

    private static Button iconButton(UiIcon icon, String accessibleText, String styleClass) {
        Button button = new Button();
        button.setGraphic(SvgIcon.of(icon, 14, "tag-management-row-icon"));
        button.setAccessibleText(accessibleText);
        button.setAccessibleHelp(accessibleText + ".");
        button.getStyleClass().addAll("tag-management-icon-button", styleClass);
        return button;
    }

    private static boolean confirm(
            DialogPane parentPane,
            Stage fallbackOwner,
            String title,
            String heading,
            String body,
            String actionText
    ) {
        ButtonType destructive = new ButtonType(actionText, ButtonBar.ButtonData.YES);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(heading);
        alert.setContentText(body);
        alert.getButtonTypes().setAll(ButtonType.CANCEL, destructive);
        alert.initModality(Modality.WINDOW_MODAL);

        Window owner = parentPane.getScene() == null ? fallbackOwner : parentPane.getScene().getWindow();
        if (owner != null) alert.initOwner(owner);

        UiStyles.applyDialog(alert.getDialogPane());
        alert.getDialogPane().getStyleClass().addAll("x-dialog", "dialog-danger");
        Node cancel = alert.getDialogPane().lookupButton(ButtonType.CANCEL);
        Node confirm = alert.getDialogPane().lookupButton(destructive);
        if (cancel != null) {
            cancel.getStyleClass().add("dialog-secondary-button");
            if (cancel instanceof Button button) button.setDefaultButton(true);
        }
        if (confirm != null) confirm.getStyleClass().add("dialog-danger-button");

        alert.setOnShown(event -> {
            Window window = alert.getDialogPane().getScene().getWindow();
            if (window instanceof Stage stage) WindowsTitleBar.applyDarkTitleBar(stage);
            if (cancel != null) Platform.runLater(cancel::requestFocus);
        });

        return alert.showAndWait().orElse(ButtonType.CANCEL) == destructive;
    }

    private static List<TagSummary> sorted(List<TagSummary> tags) {
        if (tags == null || tags.isEmpty()) return List.of();
        return tags.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TagSummary::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(TagSummary::id))
                .toList();
    }

    private static int unusedCount(List<TagSummary> tags) {
        if (tags == null || tags.isEmpty()) return 0;
        return (int) tags.stream().filter(Objects::nonNull).filter(TagSummary::unused).count();
    }

    private static String usageText(int count) {
        return count + (count == 1 ? " clip" : " clips");
    }

    private static String errorMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? "The tag operation failed. No partial changes were kept."
                : message;
    }

    private static void showStatus(Label label, String message, String tone) {
        label.setText(Objects.requireNonNullElse(message, ""));
        label.getStyleClass().removeAll("success", "error", "neutral");
        label.getStyleClass().add(tone);
        label.setVisible(true);
        label.setManaged(true);
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(Objects.requireNonNullElse(text, ""));
        label.getStyleClass().add(styleClass);
        return label;
    }
}
