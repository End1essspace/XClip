/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
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

import static io.xseries.xclip.ui.settings.SettingsPageSupport.actionRow;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.pageScroll;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.section;

public final class DataSettingsPage {

    private DataSettingsPage() {}

    public static View create(
            Path dataDirectory,
            Path databasePath,
            Path configPath,
            Runnable openDataFolder,
            Runnable refreshDatabaseStatus,
            Runnable checkDatabaseIntegrity,
            Runnable checkpointWal,
            Runnable optimizeDatabase,
            Runnable createBackup,
            Runnable restoreBackup,
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
        Runnable refreshAction = Objects.requireNonNull(
                refreshDatabaseStatus,
                "refreshDatabaseStatus"
        );
        Runnable integrityAction = Objects.requireNonNull(
                checkDatabaseIntegrity,
                "checkDatabaseIntegrity"
        );
        Runnable checkpointAction = Objects.requireNonNull(checkpointWal, "checkpointWal");
        Runnable optimizeAction = Objects.requireNonNull(
                optimizeDatabase,
                "optimizeDatabase"
        );
        Runnable backupAction = Objects.requireNonNull(createBackup, "createBackup");
        Runnable restoreAction = Objects.requireNonNull(restoreBackup, "restoreBackup");
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

        Label databaseStatus = statusLabel(
                "Database status has not been loaded yet.",
                "Database status"
        );

        Button refreshStatus = subtleButton(
                "Refresh status",
                "Read the current SQLite schema, journal mode, file sizes, and free-page estimate.",
                refreshAction
        );
        Button checkIntegrity = subtleButton(
                "Check integrity",
                "Run SQLite PRAGMA integrity_check without changing clipboard data.",
                integrityAction
        );

        VBox diagnostics = section(
                "Database status",
                "Diagnostics are read locally. Integrity checks and size inspection run off the JavaFX thread.",
                databaseStatus,
                actionRow(Pos.CENTER_LEFT, refreshStatus, checkIntegrity)
        );

        Label cleanupStatus = statusLabel(
                "Last cleanup: not run yet",
                "Data maintenance status"
        );

        Button runCleanup = subtleButton(
                "Run retention cleanup",
                "Apply the currently saved age-based retention rules immediately.",
                cleanupAction
        );
        Button checkpointButton = subtleButton(
                "Checkpoint WAL",
                "Flush committed WAL frames into the main database and truncate the WAL file.",
                checkpointAction
        );
        Button optimizeButton = subtleButton(
                "Optimize database",
                "Checkpoint WAL, run VACUUM, and refresh SQLite planner statistics.",
                optimizeAction
        );

        Button clearRecentButton = new Button("Clear RECENT history");
        clearRecentButton.setAccessibleHelp(
                "Delete every unpinned clipboard entry while preserving PINNED clips and settings."
        );
        clearRecentButton.getStyleClass().add("button-danger-subtle");
        clearRecentButton.setOnAction(event -> clearRecentAction.run());

        VBox maintenance = section(
                "History and SQLite maintenance",
                "Retention follows the saved History policy. Checkpoint and optimize are explicit exclusive operations.",
                cleanupStatus,
                actionRow(
                        Pos.CENTER_LEFT,
                        runCleanup,
                        checkpointButton,
                        optimizeButton,
                        clearRecentButton
                )
        );

        Label backupStatus = statusLabel(
                "Backups contain a consistent SQLite snapshot, the currently saved normalized config.json, and a versioned manifest. Save them outside the live XClip data folder.",
                "Backup and restore status"
        );

        Button createBackupButton = subtleButton(
                "Create backup",
                "Create a portable .xclip-backup archive with the currently saved configuration outside the live data folder without copying WAL sidecars.",
                backupAction
        );
        Button restoreBackupButton = new Button("Restore backup");
        restoreBackupButton.setAccessibleHelp(
                "Validate and replace local history and settings from an XClip backup, then exit."
        );
        restoreBackupButton.getStyleClass().add("button-danger-subtle");
        restoreBackupButton.setOnAction(event -> restoreAction.run());

        VBox backup = section(
                "Backup and restore",
                "Restore validates the archive, configuration schema, database schema, and integrity before replacing local files.",
                backupStatus,
                actionRow(
                        Pos.CENTER_LEFT,
                        createBackupButton,
                        restoreBackupButton
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
                pageScroll(locations, diagnostics, maintenance, backup, dangerBox),
                databaseStatus,
                cleanupStatus,
                backupStatus,
                List.of(
                        refreshStatus,
                        checkIntegrity,
                        runCleanup,
                        checkpointButton,
                        optimizeButton,
                        clearRecentButton,
                        createBackupButton,
                        restoreBackupButton,
                        clearData
                )
        );
    }

    private static Label statusLabel(
            String text,
            String accessibleText
    ) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setAccessibleText(accessibleText);
        label.getStyleClass().add("settings-cleanup-status");
        return label;
    }

    private static Button subtleButton(
            String text,
            String accessibleHelp,
            Runnable action
    ) {
        Button button = new Button(text);
        button.setAccessibleHelp(accessibleHelp);
        button.getStyleClass().add("btn-subtle");
        button.setOnAction(event -> action.run());
        return button;
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
            Label databaseStatus,
            Label cleanupStatus,
            Label backupStatus,
            List<Button> maintenanceButtons
    ) {
        public View {
            root = Objects.requireNonNull(root, "root");
            databaseStatus = Objects.requireNonNull(databaseStatus, "databaseStatus");
            cleanupStatus = Objects.requireNonNull(cleanupStatus, "cleanupStatus");
            backupStatus = Objects.requireNonNull(backupStatus, "backupStatus");
            maintenanceButtons = List.copyOf(
                    Objects.requireNonNull(maintenanceButtons, "maintenanceButtons")
            );
        }

        public void updateDatabaseStatus(String text) {
            databaseStatus.setText(Objects.requireNonNullElse(text, ""));
        }

        public void updateCleanupStatus(String text) {
            cleanupStatus.setText(Objects.requireNonNullElse(text, ""));
        }

        public void updateBackupStatus(String text) {
            backupStatus.setText(Objects.requireNonNullElse(text, ""));
        }

        public void setMaintenanceBusy(boolean busy) {
            for (Button button : maintenanceButtons) {
                button.setDisable(busy);
            }
        }
    }
}
