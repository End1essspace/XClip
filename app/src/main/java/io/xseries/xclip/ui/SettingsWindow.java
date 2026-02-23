/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui;

import io.xseries.xclip.config.AppPaths;
import io.xseries.xclip.config.Config;
import io.xseries.xclip.config.ConfigService;
import io.xseries.xclip.domain.service.ClipService;
import io.xseries.xclip.system.DataOwnershipService;
import io.xseries.xclip.system.WindowsAutoStartService;
import io.xseries.xclip.system.clipboard.WatcherController;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.Objects;

/**
 * Minimal Settings window for v1.0.
 *
 * Apply writes config.json and updates runtime behavior.
 */
public final class SettingsWindow {

    private final Stage stage;

    private final ConfigService configService;
    private final ClipService clipService;
    private final WatcherController watcherController;
    private final DataOwnershipService dataOwnershipService;

    private Config current;

    private final Spinner<Integer> maxHistory;
    private final Spinner<Integer> minClipLength;
    private final Spinner<Integer> maxClipChars;
    private final CheckBox watcherEnabled;
    private final CheckBox startMinimized;
    private final CheckBox startOnBoot;

    private final Button openDataFolderBtn;
    private final Button clearAllDataBtn;

    // Apply button with dirty-state
    private final Button applyBtn = new Button("Apply");
    private boolean dirty = false;
    private boolean internalSync = false;

    // Status (toast-like) label shown in bottom bar
    private final Label statusLabel = new Label();
    private PauseTransition statusHide;

    public SettingsWindow(
            ConfigService configService,
            ClipService clipService,
            WatcherController watcherController,
            DataOwnershipService dataOwnershipService,
            Config initial
    ) {
        this.configService = Objects.requireNonNull(configService);
        this.clipService = Objects.requireNonNull(clipService);
        this.watcherController = Objects.requireNonNull(watcherController);
        this.dataOwnershipService = Objects.requireNonNull(dataOwnershipService);

        this.current = (initial == null ? Config.defaults() : initial).normalized();

        this.stage = new Stage(StageStyle.DECORATED);
        stage.setTitle("XClip Settings");
        stage.getIcons().add(new javafx.scene.image.Image(
                SettingsWindow.class.getResourceAsStream("/icons/icon.png")
        ));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);

        // Controls
        maxHistory = new Spinner<>(100, 50_000, current.maxHistory(), 50);
        maxHistory.setEditable(true);
        maxHistory.getEditor().setTextFormatter(
                new javafx.scene.control.TextFormatter<>(change ->
                        change.getControlNewText().matches("\\d*") ? change : null
                )
        );

        // SAFE CONVERTER FOR maxHistory
        maxHistory.getValueFactory().setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : value.toString();
            }

            @Override
            public Integer fromString(String text) {
                try {
                    maxHistory.getEditor().getStyleClass().remove("input-error");
                    return Integer.parseInt(text.trim());
                } catch (Exception e) {
                    maxHistory.getEditor().getStyleClass().add("input-error");
                    return maxHistory.getValue();
                }
            }
        });

        minClipLength = new Spinner<>(0, 10_000, current.minClipLength(), 1);
        minClipLength.setEditable(true);
        minClipLength.getEditor().setTextFormatter(
                new javafx.scene.control.TextFormatter<>(change ->
                        change.getControlNewText().matches("\\d*") ? change : null
                )
        );

        // SAFE CONVERTER FOR minClipLength
        minClipLength.getValueFactory().setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : value.toString();
            }

            @Override
            public Integer fromString(String text) {
                try {
                    minClipLength.getEditor().getStyleClass().remove("input-error");
                    return Integer.parseInt(text.trim());
                } catch (Exception e) {
                    minClipLength.getEditor().getStyleClass().add("input-error");
                    return minClipLength.getValue();
                }
            }
        });
        maxClipChars = new Spinner<>(10_000, 5_000_000, current.maxClipChars(), 10_000);
        maxClipChars.setEditable(true);
        maxClipChars.getEditor().setTextFormatter(
                new javafx.scene.control.TextFormatter<>(change ->
                        change.getControlNewText().matches("\\d*") ? change : null
                )
        );

        // SAFE CONVERTER FOR maxClipChars
        maxClipChars.getValueFactory().setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : value.toString();
            }

            @Override
            public Integer fromString(String text) {
                try {
                    maxClipChars.getEditor().getStyleClass().remove("input-error");
                    return Integer.parseInt(text.trim());
                } catch (Exception e) {
                    maxClipChars.getEditor().getStyleClass().add("input-error");
                    return maxClipChars.getValue();
                }
            }
        });

        watcherEnabled = new CheckBox("Enable clipboard capture");
        watcherEnabled.setSelected(current.watcherEnabled());

        startMinimized = new CheckBox("Start minimized (tray)");
        startMinimized.setSelected(current.startMinimized());

        startOnBoot = new CheckBox("Start on Windows boot");
        startOnBoot.setSelected(current.startOnBoot());

        // Layout grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        int r = 0;
        grid.add(new Label("Max history:"), 0, r);
        grid.add(maxHistory, 1, r++);

        grid.add(new Label("Min clip length:"), 0, r);
        grid.add(minClipLength, 1, r++);

        grid.add(new Label("Max clip chars:"), 0, r);
        grid.add(maxClipChars, 1, r++);


        grid.add(watcherEnabled, 1, r++);
        grid.add(startMinimized, 1, r++);
        grid.add(startOnBoot, 1, r++);

        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(140);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1);
        grid.getStyleClass().add("settings-section");

        Label hint = new Label("Changes apply immediately.");
        hint.setStyle("-fx-opacity: 0.75;");

        // Status label styling
        statusLabel.getStyleClass().add("status-text");
        statusLabel.setManaged(false);
        statusLabel.setVisible(false);

        // Data path + copy
        TextField dataPath = new TextField(AppPaths.dataDir().toAbsolutePath().toString());
        dataPath.setEditable(false);
        dataPath.setFocusTraversable(false);
        dataPath.setPrefColumnCount(28);
        dataPath.getStyleClass().add("data-path");

        Button copyPathBtn = new Button("Copy path");
        copyPathBtn.setFocusTraversable(false);
        copyPathBtn.getStyleClass().add("btn-subtle");
        copyPathBtn.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(dataPath.getText());
            Clipboard.getSystemClipboard().setContent(cc);
            showStatus("Path copied");
        });

        HBox pathRow = new HBox(10, dataPath, copyPathBtn);
        pathRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(dataPath, Priority.ALWAYS);

        // Data ownership buttons
        openDataFolderBtn = new Button("Open data folder");
        openDataFolderBtn.setOnAction(e -> dataOwnershipService.openDataFolder());

        clearAllDataBtn = new Button("Clear ALL data");
        clearAllDataBtn.getStyleClass().add("button-danger");
        clearAllDataBtn.setOnAction(e -> clearAllDataFlow());

        HBox dataButtons = new HBox(10, openDataFolderBtn);
        dataButtons.setAlignment(Pos.CENTER_LEFT);

        // Danger zone section
        Label dangerTitle = new Label("Danger zone");
        dangerTitle.getStyleClass().add("danger-title");

        Separator sep = new Separator();
        sep.getStyleClass().add("separator-subtle");

        Label dangerHint = new Label("Clearing data deletes clipboard history and config.json.");
        dangerHint.setStyle("-fx-opacity: 0.75;");

        VBox dangerBox = new VBox(8, dangerTitle, dangerHint, clearAllDataBtn);
        dangerBox.getStyleClass().add("danger-box");

        // Bottom buttons
        Button closeBtn = new Button("Close");

        applyBtn.setDefaultButton(true);
        applyBtn.setDisable(true); // disabled until something changes
        closeBtn.setCancelButton(true);

        applyBtn.setOnAction(e -> apply());
        closeBtn.setOnAction(e -> {
            if (dirty) resetUiToCurrentSilently();
            stage.hide();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // IMPORTANT: statusLabel is inside the bottom bar -> no layout jumps
        HBox buttons = new HBox(10, statusLabel, spacer, dataButtons, applyBtn, closeBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12, grid, hint, pathRow, sep, dangerBox, buttons);
        root.setPadding(new Insets(12));
        root.getStyleClass().add("settings-root");

        Scene scene = new Scene(root);
        scene.getStylesheets().add(
                getClass().getResource("/ui/styles.css").toExternalForm()
        );

        stage.setScene(scene);
        stage.setOnHiding(e -> {
            if (dirty) {
                internalSync = true;

                // откат к current
                maxHistory.getValueFactory().setValue(current.maxHistory());
                minClipLength.getValueFactory().setValue(current.minClipLength());
                watcherEnabled.setSelected(current.watcherEnabled());
                startMinimized.setSelected(current.startMinimized());
                startOnBoot.setSelected(current.startOnBoot());

                syncAutostartCheckbox();
                forceSyncSpinnerEditors();

                internalSync = false;
                clearDirty();
            } else {
                // даже если не dirty — на всякий случай убираем "ффф"
                internalSync = true;
                forceSyncSpinnerEditors();
                internalSync = false;
            }
        });

        stage.setOnCloseRequest(e -> {
            if (dirty) resetUiToCurrentSilently();
            stage.hide();
            e.consume(); // чтобы всегда вести себя одинаково
        });

        // Dirty listeners (ignore internal sync updates)
        wireDirtyForIntSpinner(maxHistory);
        wireDirtyForIntSpinner(minClipLength);
        wireDirtyForIntSpinner(maxClipChars);
        watcherEnabled.selectedProperty().addListener((obs, o, n) -> { if (!internalSync) markDirty(); });
        startMinimized.selectedProperty().addListener((obs, o, n) -> { if (!internalSync) markDirty(); });
        startOnBoot.selectedProperty().addListener((obs, o, n) -> { if (!internalSync) markDirty(); });

        // initial state
        clearDirty();
    }

    public void show() {
        internalSync = true;
        syncAutostartCheckbox();
        forceSyncSpinnerEditors();
        internalSync = false;

        if (!stage.isShowing()) {
            stage.centerOnScreen();
            stage.show();
        }
        stage.toFront();
        stage.requestFocus();
    }

    /**
     * Reload from disk (useful if config.json edited externally).
     */
    public void reloadFromDisk() {
        internalSync = true;

        current = configService.loadOrCreate();
        maxHistory.getValueFactory().setValue(current.maxHistory());
        minClipLength.getValueFactory().setValue(current.minClipLength());
        watcherEnabled.setSelected(current.watcherEnabled());
        startMinimized.setSelected(current.startMinimized());
        startOnBoot.setSelected(current.startOnBoot());
        maxHistory.getEditor().getStyleClass().remove("input-error");
        minClipLength.getEditor().getStyleClass().remove("input-error");
        syncAutostartCheckbox();
        forceSyncSpinnerEditors();

        internalSync = false;
        clearDirty();
    }
    
    private void apply() {
        if (!dirty) {
            showStatus("No changes");
            return;
        }
        if (!validateIntSpinner(maxHistory, 100, 50_000, "Max history")) return;
        if (!validateIntSpinner(minClipLength, 0, 10_000, "Min clip length")) return;
        if (!validateIntSpinner(maxClipChars, 10_000, 5_000_000, "Max clip chars")) return;


        Config next = current
                .withMaxHistory(maxHistory.getValue())
                .withMinClipLength(minClipLength.getValue())
                .withMaxClipChars(maxClipChars.getValue())
                .withWatcherEnabled(watcherEnabled.isSelected())
                .withStartMinimized(startMinimized.isSelected())
                .withStartOnBoot(startOnBoot.isSelected());

        boolean autoStartChanged = current.startOnBoot() != next.startOnBoot();

        // Persist + system integrations (autostart is applied inside ConfigService.save)
        configService.save(next);
        current = next;

        // Apply runtime behavior
        try {
            clipService.applyConfig(next);
        } catch (Throwable ignored) {
        }

        if (next.watcherEnabled()) watcherController.enable();
        else watcherController.disable();

        // Status message: autostart text only if the flag changed
        if (autoStartChanged) {
            showStatus(next.startOnBoot()
                    ? "Saved • Autostart enabled"
                    : "Saved • Autostart disabled");
        } else {
            showStatus("Saved");
        }

        clearDirty();
    }

    private void clearAllDataFlow() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Clear ALL data");
        confirm.setHeaderText("This will permanently delete all XClip data");
        confirm.setContentText("Clipboard history (database) and settings (config.json) will be removed.\nContinue?");

        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;

            try { watcherController.disable(); } catch (Throwable ignored) {}

            try {
                dataOwnershipService.clearAllData();
            } catch (Throwable ex) {
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.setTitle("Failed to clear data");
                err.setHeaderText("XClip couldn't delete its data files");
                err.setContentText(
                        "Close other XClip instances and try again.\n\n" +
                        "Data folder: " + AppPaths.dataDir().toAbsolutePath()
                );
                err.showAndWait();
                return;
            }

            Alert done = new Alert(Alert.AlertType.INFORMATION);
            done.setTitle("Data cleared");
            done.setHeaderText("All data removed");
            done.setContentText("XClip will exit now. Restart it to continue.");
            done.showAndWait();

            Platform.exit();
            System.exit(0);
        });
    }

    private void markDirty() {
        dirty = true;
        applyBtn.setDisable(false);
    }

    private void clearDirty() {
        dirty = false;
        applyBtn.setDisable(true);
    }

    private void showStatus(String text) {
        if (statusHide != null) statusHide.stop();

        statusLabel.setText(text);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);

        statusHide = new PauseTransition(Duration.seconds(2));
        statusHide.setOnFinished(e -> {
            statusLabel.setVisible(false);
            statusLabel.setManaged(false);
        });
        statusHide.playFromStart();
    }

    private void syncAutostartCheckbox() {
        try {
            boolean enabled = WindowsAutoStartService.isEnabled();
            startOnBoot.setSelected(enabled);
        } catch (Throwable ignored) {
            // If we can't query, keep config state
        }
    }

    private void resetUiToCurrentSilently() {
        internalSync = true;

        maxHistory.getValueFactory().setValue(current.maxHistory());
        minClipLength.getValueFactory().setValue(current.minClipLength());
        watcherEnabled.setSelected(current.watcherEnabled());
        startMinimized.setSelected(current.startMinimized());
        startOnBoot.setSelected(current.startOnBoot());

        // keep checkbox aligned with real registry state
        syncAutostartCheckbox();

        internalSync = false;
        clearDirty();
    }

    private void wireDirtyForIntSpinner(Spinner<Integer> sp) {
        // 1) если крутим стрелочки — valueProperty меняется
        sp.valueProperty().addListener((obs, o, n) -> {
            if (!internalSync) markDirty();
        });

        // 2) если печатаем руками — слушаем текст редактора
        TextField editor = sp.getEditor();
        editor.textProperty().addListener((obs, o, n) -> {
            if (!internalSync) markDirty();
        });

        // 3) на потере фокуса — коммитим текст в value (чтобы дальше всё было консистентно)
        editor.focusedProperty().addListener((obs, was, now) -> {
            if (was && !now) commitSpinnerEditor(sp);
        });

        // 4) Enter в editor — тоже коммит
        editor.setOnAction(e -> commitSpinnerEditor(sp));
    }

    private void commitSpinnerEditor(Spinner<Integer> sp) {
        try {
            String text = sp.getEditor().getText();
            if (text == null) return;

            text = text.trim();
            if (text.isEmpty()) return;

            int val = Integer.parseInt(text);
            sp.getValueFactory().setValue(val);
        } catch (Exception ignored) {
            // if invalid input, do nothing (you can later show validation)
        }
    }

    private boolean validateIntSpinner(Spinner<Integer> sp, int min, int max, String name) {
        TextField editor = sp.getEditor();
        editor.getStyleClass().remove("input-error");

        String text = editor.getText();
        if (text == null) text = "";
        text = text.trim();

        if (text.isEmpty()) {
            editor.getStyleClass().add("input-error");
            showStatus(name + ": required");
            return false;
        }

        int val;
        try {
            val = Integer.parseInt(text);
        } catch (Exception ex) {
            editor.getStyleClass().add("input-error");
            showStatus(name + ": invalid number");
            return false;
        }

        if (val < min) {
            val = min;
            editor.setText(String.valueOf(val));
            commitSpinnerEditor(sp);
            showStatus(name + ": clamped to " + min);
        } else if (val > max) {
            val = max;
            editor.setText(String.valueOf(val));
            commitSpinnerEditor(sp);
            showStatus(name + ": clamped to " + max);
        }

        return true;
    }

    private void forceSyncSpinnerEditors() {
        // переписываем текст в editor из value (иначе "ффф" останется)
        maxHistory.getEditor().setText(String.valueOf(maxHistory.getValue()));
        minClipLength.getEditor().setText(String.valueOf(minClipLength.getValue()));
        maxClipChars.getEditor().setText(String.valueOf(maxClipChars.getValue()));

        // убрать красную рамку
        maxHistory.getEditor().getStyleClass().remove("input-error");
        minClipLength.getEditor().getStyleClass().remove("input-error");
        maxClipChars.getEditor().getStyleClass().remove("input-error");
    }
}
