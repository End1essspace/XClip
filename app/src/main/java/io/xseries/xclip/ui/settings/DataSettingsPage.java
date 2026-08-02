/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static io.xseries.xclip.ui.settings.SettingsPageSupport.infoRow;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.informationSection;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.pageScroll;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.section;

public final class DataSettingsPage {

    private DataSettingsPage() {}

    public static View create(
            Path dataDirectory,
            Path databasePath,
            Path configPath,
            Runnable openDataFolder,
            Runnable runRetentionCleanup,
            Runnable clearRecent,
            Runnable clearAllData,
            Consumer<String> statusSink
    ) {
        Path directory = Objects.requireNonNull(dataDirectory, "dataDirectory")
                .toAbsolutePath();
        Path database = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath();
        Path config = Objects.requireNonNull(configPath, "configPath")
                .toAbsolutePath();
        Runnable openAction = Objects.requireNonNull(openDataFolder, "openDataFolder");
        Runnable cleanupAction = Objects.requireNonNull(
                runRetentionCleanup,
                "runRetentionCleanup"
        );
        Runnable clearRecentAction = Objects.requireNonNull(clearRecent, "clearRecent");
        Runnable clearAllAction = Objects.requireNonNull(clearAllData, "clearAllData");
        Consumer<String> status = Objects.requireNonNull(statusSink, "statusSink");

        VBox locations = section(
                "Storage locations",
                "All XClip-owned history and preferences are stored below.",
                pathRow("Data folder", directory, true, openAction, status),
                pathRow("Database", database, false, null, status),
                pathRow("Configuration", config, false, null, status)
        );

        Label cleanupStatus = new Label("Last cleanup: not run yet");
        cleanupStatus.setWrapText(true);
        cleanupStatus.setAccessibleText("Data maintenance status");
        cleanupStatus.getStyleClass().add("settings-cleanup-status");

        Button runCleanup = new Button("Run retention cleanup");
        runCleanup.setAccessibleHelp(
                "Apply the currently saved age-based retention rules immediately."
        );
        runCleanup.getStyleClass().add("btn-subtle");
        runCleanup.setOnAction(event -> cleanupAction.run());

        Button clearRecentButton = new Button("Clear RECENT history");
        clearRecentButton.setAccessibleHelp(
                "Delete every unpinned clipboard entry while preserving PINNED clips and settings."
        );
        clearRecentButton.getStyleClass().add("button-danger-subtle");
        clearRecentButton.setOnAction(event -> clearRecentAction.run());

        HBox maintenanceActions = new HBox(10, runCleanup, clearRecentButton);
        maintenanceActions.setAlignment(Pos.CENTER_LEFT);
        maintenanceActions.getStyleClass().add("settings-action-row");

        VBox maintenance = section(
                "History maintenance",
                "Retention cleanup follows the saved History policy. Clear RECENT removes all unpinned clips.",
                cleanupStatus,
                maintenanceActions
        );

        VBox backup = informationSection(
                "Backup and restore",
                "Entry points are reserved for the database-maintenance milestone; no unsafe placeholder action is exposed.",
                List.of(
                        infoRow("Backup", "Deferred to M7.2"),
                        infoRow("Restore", "Deferred to M7.2")
                )
        );

        Button clearData = new Button("Clear ALL data");
        clearData.setAccessibleHelp(
                "Permanently delete clipboard history, database sidecars, and configuration."
        );
        clearData.getStyleClass().add("button-danger");
        clearData.setOnAction(event -> clearAllAction.run());

        Label dangerTitle = new Label("Danger zone");
        dangerTitle.getStyleClass().add("danger-title");

        Label dangerHint = new Label(
                "Clearing all data deletes clipboard history, PINNED clips, tags, and config.json, then exits XClip."
        );
        dangerHint.setWrapText(true);
        dangerHint.getStyleClass().add("settings-section-description");

        VBox dangerBox = new VBox(8, dangerTitle, dangerHint, clearData);
        dangerBox.getStyleClass().addAll("settings-section", "danger-box");

        return new View(
                pageScroll(locations, maintenance, backup, dangerBox),
                cleanupStatus,
                runCleanup,
                clearRecentButton,
                clearData
        );
    }

    private static HBox pathRow(
            String label,
            Path path,
            boolean openSupported,
            Runnable openAction,
            Consumer<String> status
    ) {
        Label name = new Label(label);
        name.getStyleClass().add("settings-path-label");

        TextField value = new TextField(path.toString());
        value.setEditable(false);
        value.setFocusTraversable(true);
        value.setAccessibleText(label + " path");
        value.setAccessibleHelp("Read-only path. Use Ctrl+C to copy selected text.");
        value.getStyleClass().add("data-path");
        HBox.setHgrow(value, Priority.ALWAYS);

        Button copy = new Button("Copy");
        copy.getStyleClass().add("btn-subtle");
        copy.setAccessibleHelp("Copy the " + label.toLowerCase() + " path.");
        copy.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(value.getText());
            Clipboard.getSystemClipboard().setContent(content);
            status.accept(label + " path copied");
        });

        HBox row = new HBox(10, name, value, copy);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("settings-path-row");

        if (openSupported) {
            Button open = new Button("Open");
            open.getStyleClass().add("btn-subtle");
            open.setAccessibleHelp("Open the XClip data folder in File Explorer.");
            open.setOnAction(event -> openAction.run());
            row.getChildren().add(open);
        }
        return row;
    }

    public record View(
            ScrollPane root,
            Label cleanupStatus,
            Button runCleanup,
            Button clearRecent,
            Button clearAll
    ) {
        public View {
            root = Objects.requireNonNull(root, "root");
            cleanupStatus = Objects.requireNonNull(cleanupStatus, "cleanupStatus");
            runCleanup = Objects.requireNonNull(runCleanup, "runCleanup");
            clearRecent = Objects.requireNonNull(clearRecent, "clearRecent");
            clearAll = Objects.requireNonNull(clearAll, "clearAll");
        }

        public void updateCleanupStatus(String text) {
            cleanupStatus.setText(Objects.requireNonNullElse(text, ""));
        }

        public void setMaintenanceBusy(boolean busy) {
            runCleanup.setDisable(busy);
            clearRecent.setDisable(busy);
            clearAll.setDisable(busy);
        }
    }
}
