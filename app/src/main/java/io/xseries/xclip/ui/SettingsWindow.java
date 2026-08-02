/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui;

import io.xseries.xclip.config.AppPaths;
import io.xseries.xclip.config.Config;
import io.xseries.xclip.config.ConfigService;
import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;
import io.xseries.xclip.domain.privacy.ExcludedApplicationPolicy;
import io.xseries.xclip.domain.service.ClipService;
import io.xseries.xclip.system.DataOwnershipService;
import io.xseries.xclip.system.WindowsAutoStartService;
import io.xseries.xclip.system.clipboard.WatcherController;
import io.xseries.xclip.system.window.WindowsTitleBar;
import io.xseries.xclip.ui.settings.DuplicateSettingsModel;
import io.xseries.xclip.ui.settings.DuplicateSettingsModel.WindowPreset;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.Objects;
import java.util.function.Function;

/**
 * Product Settings window.
 *
 * Apply persists config.json and immediately updates runtime behavior. Closing
 * the window discards unapplied edits and restores controls from the last saved
 * configuration snapshot.
 */
public final class SettingsWindow {

    private static final double DEFAULT_WIDTH = 700;
    private static final double DEFAULT_HEIGHT = 720;
    private static final double MIN_WIDTH = 620;
    private static final double MIN_HEIGHT = 520;

    private final Stage stage;

    private final ConfigService configService;
    private final ClipService clipService;
    private final WatcherController watcherController;
    private final DataOwnershipService dataOwnershipService;

    private Config current;

    private final Spinner<Integer> maxHistory;
    private final Spinner<Integer> minClipLength;
    private final Spinner<Integer> maxClipChars;
    private final Spinner<Integer> uiClipLimit;
    private final CheckBox watcherEnabled;
    private final CheckBox startMinimized;
    private final CheckBox startOnBoot;

    private final ComboBox<DuplicateBehaviorPolicy.RecentDuplicatePosition>
            duplicateRecentPosition;
    private final ComboBox<DuplicateBehaviorPolicy.PinnedDuplicatePosition>
            duplicatePinnedPosition;
    private final ComboBox<DuplicateBehaviorPolicy.WhitespaceMode>
            duplicateWhitespaceMode;
    private final ComboBox<DuplicateBehaviorPolicy.CaseSensitivity>
            duplicateCaseSensitivity;
    private final ComboBox<WindowPreset> duplicateWindowPreset;
    private final TextField duplicateCustomWindowMillis;
    private final CheckBox duplicateExactContentMode;
    private final Label duplicateExactOverrideHint;
    private final Button resetDuplicateDefaultsBtn;

    private final TextArea excludedApplications;
    private final Button clearExcludedApplicationsBtn;

    private final Button openDataFolderBtn;
    private final Button clearAllDataBtn;

    private final Button applyBtn = new Button("Apply");
    private boolean dirty = false;
    private boolean internalSync = false;

    private final Label statusLabel = new Label();
    private PauseTransition statusHide;
    private final java.util.function.Consumer<Config> onConfigApplied;

    public SettingsWindow(
            ConfigService configService,
            ClipService clipService,
            WatcherController watcherController,
            DataOwnershipService dataOwnershipService,
            Config initial,
            java.util.function.Consumer<Config> onConfigApplied
    ) {
        this.configService = Objects.requireNonNull(configService);
        this.clipService = Objects.requireNonNull(clipService);
        this.watcherController = Objects.requireNonNull(watcherController);
        this.dataOwnershipService = Objects.requireNonNull(dataOwnershipService);
        this.current = (initial == null ? Config.defaults() : initial).normalized();
        this.onConfigApplied = onConfigApplied != null ? onConfigApplied : cfg -> {};

        stage = new Stage(StageStyle.DECORATED);
        stage.setTitle("XClip Settings");
        stage.getIcons().add(new javafx.scene.image.Image(
                SettingsWindow.class.getResourceAsStream("/icons/icon.png")
        ));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(true);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);

        maxHistory = intSpinner(
                100,
                50_000,
                current.maxHistory(),
                50,
                "Maximum clipboard history entries",
                "Allowed range: 100 to 50,000 clips."
        );
        minClipLength = intSpinner(
                0,
                10_000,
                current.minClipLength(),
                1,
                "Minimum captured clip length",
                "Clipboard text shorter than this value is ignored."
        );
        maxClipChars = intSpinner(
                10_000,
                5_000_000,
                current.maxClipChars(),
                10_000,
                "Maximum characters captured per clip",
                "Longer clipboard text is truncated at this limit."
        );
        uiClipLimit = intSpinner(
                50,
                5_000,
                current.uiClipLimit(),
                50,
                "Maximum clips shown in the popup",
                "Allowed range: 50 to 5,000 visible clips."
        );

        watcherEnabled = new CheckBox("Enable clipboard capture");
        watcherEnabled.setAccessibleHelp(
                "Enable or disable background clipboard monitoring."
        );

        startMinimized = new CheckBox("Start minimized (tray)");
        startMinimized.setAccessibleHelp(
                "Start XClip in the system tray without opening the popup."
        );

        startOnBoot = new CheckBox("Start on Windows boot");
        startOnBoot.setAccessibleHelp(
                "Launch XClip automatically after signing in to Windows."
        );

        duplicateRecentPosition = enumCombo(
                DuplicateBehaviorPolicy.RecentDuplicatePosition.values(),
                DuplicateSettingsModel::recentPositionLabel,
                "Recent duplicate position",
                "Choose whether copying an existing unpinned clip changes its position."
        );
        duplicatePinnedPosition = enumCombo(
                DuplicateBehaviorPolicy.PinnedDuplicatePosition.values(),
                DuplicateSettingsModel::pinnedPositionLabel,
                "Pinned duplicate position",
                "Choose whether copying a pinned clip changes manual pinned order."
        );
        duplicateWhitespaceMode = enumCombo(
                DuplicateBehaviorPolicy.WhitespaceMode.values(),
                DuplicateSettingsModel::whitespaceLabel,
                "Duplicate whitespace matching",
                "Normalize whitespace or compare whitespace exactly as copied."
        );
        duplicateCaseSensitivity = enumCombo(
                DuplicateBehaviorPolicy.CaseSensitivity.values(),
                DuplicateSettingsModel::caseSensitivityLabel,
                "Duplicate case matching",
                "Choose whether uppercase and lowercase characters are distinct."
        );

        duplicateWindowPreset = new ComboBox<>();
        duplicateWindowPreset.getItems().setAll(DuplicateSettingsModel.windowPresets());
        duplicateWindowPreset.setConverter(labelConverter(WindowPreset::label));
        duplicateWindowPreset.setAccessibleText("Duplicate time window");
        duplicateWindowPreset.setAccessibleHelp(
                "Only matching clips within this age are treated as one duplicate occurrence."
        );
        duplicateWindowPreset.getStyleClass().add("settings-control-wide");

        duplicateCustomWindowMillis = new TextField();
        duplicateCustomWindowMillis.setPromptText("Milliseconds");
        duplicateCustomWindowMillis.setAccessibleText(
                "Custom duplicate window in milliseconds"
        );
        duplicateCustomWindowMillis.setAccessibleHelp(
                "Enter a non-negative duration. Zero means unlimited."
        );
        duplicateCustomWindowMillis.setTextFormatter(
                new TextFormatter<>(change ->
                        change.getControlNewText().matches("\\d*") ? change : null
                )
        );
        duplicateCustomWindowMillis.getStyleClass().add("settings-control-wide");

        duplicateExactContentMode = new CheckBox("Require exact content");
        duplicateExactContentMode.setAccessibleHelp(
                "Compare every captured character exactly and ignore whitespace and case options."
        );

        duplicateExactOverrideHint = new Label(
                "Exact content is active. Whitespace and case controls are ignored."
        );
        duplicateExactOverrideHint.setWrapText(true);
        duplicateExactOverrideHint.getStyleClass().add("settings-override-hint");

        resetDuplicateDefaultsBtn = new Button("Reset duplicate defaults");
        resetDuplicateDefaultsBtn.setAccessibleHelp(
                "Restore the safe duplicate behavior that XClip used before preferences existed."
        );
        resetDuplicateDefaultsBtn.getStyleClass().add("btn-subtle");

        excludedApplications = new TextArea();
        excludedApplications.setPromptText("One executable per line, for example: 1password.exe");
        excludedApplications.setPrefRowCount(5);
        excludedApplications.setWrapText(false);
        excludedApplications.setAccessibleText("Excluded applications");
        excludedApplications.setAccessibleHelp(
                "Clipboard changes are ignored while one of these executables owns the foreground window."
        );
        excludedApplications.getStyleClass().addAll(
                "settings-control-wide",
                "settings-excluded-apps"
        );

        clearExcludedApplicationsBtn = new Button("Clear exclusions");
        clearExcludedApplicationsBtn.setAccessibleHelp(
                "Remove every application from the clipboard capture exclusion list."
        );
        clearExcludedApplicationsBtn.getStyleClass().add("btn-subtle");

        GridPane captureGrid = settingsGrid();
        int captureRow = 0;
        captureRow = addSettingRow(
                captureGrid,
                captureRow,
                "Max history",
                "Maximum number of unpinned clipboard entries retained locally.",
                maxHistory
        );
        captureRow = addSettingRow(
                captureGrid,
                captureRow,
                "Min clip length",
                "Ignore clipboard text shorter than this number of characters.",
                minClipLength
        );
        captureRow = addSettingRow(
                captureGrid,
                captureRow,
                "Max clip chars",
                "Longer clipboard text is truncated before storage.",
                maxClipChars
        );
        captureRow = addSettingRow(
                captureGrid,
                captureRow,
                "UI clip limit",
                "Maximum number of prepared rows shown in the popup.",
                uiClipLimit
        );
        captureGrid.add(watcherEnabled, 1, captureRow++);
        captureGrid.add(startMinimized, 1, captureRow++);
        captureGrid.add(startOnBoot, 1, captureRow);

        VBox captureSection = section(
                "Capture & history",
                "Clipboard limits, background capture, and startup behavior.",
                captureGrid
        );

        GridPane duplicateGrid = settingsGrid();
        int duplicateRow = 0;
        duplicateRow = addSettingRow(
                duplicateGrid,
                duplicateRow,
                "Recent duplicates",
                "Move an existing RECENT clip to the top or keep its current position.",
                duplicateRecentPosition
        );
        duplicateRow = addSettingRow(
                duplicateGrid,
                duplicateRow,
                "Pinned duplicates",
                "Keep manual PINNED order or move the copied pinned clip to the top.",
                duplicatePinnedPosition
        );
        duplicateRow = addSettingRow(
                duplicateGrid,
                duplicateRow,
                "Whitespace",
                "Normalize collapses whitespace runs; Preserve compares copied characters.",
                duplicateWhitespaceMode
        );
        duplicateRow = addSettingRow(
                duplicateGrid,
                duplicateRow,
                "Letter case",
                "Case-sensitive treats Alpha and alpha as different content.",
                duplicateCaseSensitivity
        );

        VBox windowControl = new VBox(7, duplicateWindowPreset, duplicateCustomWindowMillis);
        duplicateRow = addSettingRow(
                duplicateGrid,
                duplicateRow,
                "Duplicate window",
                "Unlimited checks all history. A finite window allows older matches to create a new row.",
                windowControl
        );

        VBox exactControl = new VBox(
                5,
                duplicateExactContentMode,
                duplicateExactOverrideHint
        );
        duplicateGrid.add(settingText(
                "Exact content mode",
                "Compares every character exactly and overrides Whitespace and Letter case."
        ), 0, duplicateRow);
        duplicateGrid.add(exactControl, 1, duplicateRow);

        HBox duplicateActions = new HBox(resetDuplicateDefaultsBtn);
        duplicateActions.setAlignment(Pos.CENTER_RIGHT);

        VBox duplicateSection = section(
                "Duplicate behavior",
                "These rules apply immediately after Apply and are persisted in config.json.",
                duplicateGrid,
                duplicateActions
        );
        duplicateSection.getStyleClass().add("duplicate-settings-section");

        GridPane privacyGrid = settingsGrid();
        addSettingRow(
                privacyGrid,
                0,
                "Excluded applications",
                "XClip skips clipboard changes while a listed executable owns the foreground window. Matching uses the executable name only and is case-insensitive.",
                excludedApplications
        );

        Label privacyFallbackHint = new Label(
                "Resolver failures are fail-open: unidentified applications remain capturable instead of silently losing clipboard data."
        );
        privacyFallbackHint.setWrapText(true);
        privacyFallbackHint.getStyleClass().add("settings-privacy-hint");

        HBox privacyActions = new HBox(clearExcludedApplicationsBtn);
        privacyActions.setAlignment(Pos.CENTER_RIGHT);

        VBox privacySection = section(
                "Privacy — excluded applications",
                "Process-based capture exclusions are stored locally in config.json.",
                privacyGrid,
                privacyFallbackHint,
                privacyActions
        );
        privacySection.getStyleClass().add("privacy-settings-section");

        statusLabel.getStyleClass().add("status-text");
        statusLabel.setAccessibleText("Settings operation status");
        statusLabel.setManaged(false);
        statusLabel.setVisible(false);

        TextField dataPath = new TextField(
                AppPaths.dataDir().toAbsolutePath().toString()
        );
        dataPath.setEditable(false);
        dataPath.setFocusTraversable(true);
        dataPath.setAccessibleText("XClip data folder path");
        dataPath.setAccessibleHelp(
                "Read-only path. Use Ctrl+C to copy selected text."
        );
        dataPath.setPrefColumnCount(28);
        dataPath.getStyleClass().add("data-path");

        Button copyPathBtn = new Button("Copy path");
        copyPathBtn.setAccessibleHelp(
                "Copy the XClip data folder path to the clipboard."
        );
        copyPathBtn.getStyleClass().add("btn-subtle");
        copyPathBtn.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(dataPath.getText());
            Clipboard.getSystemClipboard().setContent(content);
            showStatus("Path copied");
        });

        HBox pathRow = new HBox(10, dataPath, copyPathBtn);
        pathRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(dataPath, Priority.ALWAYS);

        VBox dataSection = section(
                "Local data",
                "All history and preferences stay in this user-owned folder.",
                pathRow
        );

        openDataFolderBtn = new Button("Open data folder");
        openDataFolderBtn.setAccessibleHelp(
                "Open the XClip data folder in File Explorer."
        );
        openDataFolderBtn.getStyleClass().add("btn-subtle");
        openDataFolderBtn.setOnAction(
                event -> dataOwnershipService.openDataFolder()
        );

        clearAllDataBtn = new Button("Clear ALL data");
        clearAllDataBtn.setAccessibleHelp(
                "Permanently delete clipboard history and configuration."
        );
        clearAllDataBtn.getStyleClass().add("button-danger");
        clearAllDataBtn.setOnAction(event -> clearAllDataFlow());

        Label dangerTitle = new Label("Danger zone");
        dangerTitle.getStyleClass().add("danger-title");

        Label dangerHint = new Label(
                "Clearing data deletes clipboard history and config.json."
        );
        dangerHint.getStyleClass().add("settings-section-description");

        VBox dangerBox = new VBox(8, dangerTitle, dangerHint, clearAllDataBtn);
        dangerBox.getStyleClass().addAll("settings-section", "danger-box");

        Button closeBtn = new Button("Close");
        closeBtn.setAccessibleHelp(
                "Close Settings and discard unapplied changes."
        );
        closeBtn.getStyleClass().add("btn-subtle");
        closeBtn.setCancelButton(true);

        applyBtn.setDefaultButton(true);
        applyBtn.setAccessibleHelp("Save and apply the current settings.");
        applyBtn.getStyleClass().add("btn-apply");
        applyBtn.setDisable(true);

        applyBtn.setOnAction(event -> apply());
        closeBtn.setOnAction(event -> {
            if (dirty) resetUiToCurrentSilently();
            stage.hide();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bottomBar = new HBox(
                10,
                statusLabel,
                spacer,
                openDataFolderBtn,
                applyBtn,
                closeBtn
        );
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        bottomBar.getStyleClass().add("settings-bottom-bar");

        VBox content = new VBox(
                12,
                captureSection,
                duplicateSection,
                privacySection,
                dataSection,
                new Separator(),
                dangerBox
        );
        content.setPadding(new Insets(14));
        content.getStyleClass().add("settings-content");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("settings-scroll");

        BorderPane root = new BorderPane();
        root.setCenter(scroll);
        root.setBottom(bottomBar);
        root.getStyleClass().add("settings-root");

        Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        UiStyles.applySettings(scene);
        stage.setScene(scene);

        stage.setOnHiding(event -> {
            if (dirty) {
                resetUiToCurrentSilently();
            } else {
                internalSync = true;
                forceSyncSpinnerEditors();
                internalSync = false;
            }
        });

        stage.setOnCloseRequest(event -> {
            if (dirty) resetUiToCurrentSilently();
            stage.hide();
            event.consume();
        });

        wireDirtyForIntSpinner(maxHistory);
        wireDirtyForIntSpinner(minClipLength);
        wireDirtyForIntSpinner(maxClipChars);
        wireDirtyForIntSpinner(uiClipLimit);

        watcherEnabled.selectedProperty().addListener(
                (observable, oldValue, newValue) -> markDirtyUnlessSyncing()
        );
        startMinimized.selectedProperty().addListener(
                (observable, oldValue, newValue) -> markDirtyUnlessSyncing()
        );
        startOnBoot.selectedProperty().addListener(
                (observable, oldValue, newValue) -> markDirtyUnlessSyncing()
        );

        duplicateRecentPosition.valueProperty().addListener(
                (observable, oldValue, newValue) -> markDirtyUnlessSyncing()
        );
        duplicatePinnedPosition.valueProperty().addListener(
                (observable, oldValue, newValue) -> markDirtyUnlessSyncing()
        );
        duplicateWhitespaceMode.valueProperty().addListener(
                (observable, oldValue, newValue) -> markDirtyUnlessSyncing()
        );
        duplicateCaseSensitivity.valueProperty().addListener(
                (observable, oldValue, newValue) -> markDirtyUnlessSyncing()
        );
        duplicateWindowPreset.valueProperty().addListener(
                (observable, oldValue, newValue) -> {
                    syncDuplicateWindowEditor();
                    markDirtyUnlessSyncing();
                }
        );
        duplicateCustomWindowMillis.textProperty().addListener(
                (observable, oldValue, newValue) -> markDirtyUnlessSyncing()
        );
        duplicateExactContentMode.selectedProperty().addListener(
                (observable, oldValue, newValue) -> {
                    syncDuplicateMatchingAvailability();
                    markDirtyUnlessSyncing();
                }
        );

        resetDuplicateDefaultsBtn.setOnAction(
                event -> resetDuplicateControlsToDefaults()
        );

        excludedApplications.textProperty().addListener(
                (observable, oldValue, newValue) -> markDirtyUnlessSyncing()
        );
        clearExcludedApplicationsBtn.setOnAction(event -> {
            excludedApplications.clear();
            showStatus("Application exclusions cleared • Apply to save");
        });

        internalSync = true;
        syncUiFromCurrent();
        internalSync = false;
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
            WindowsTitleBar.applyDarkTitleBar(stage);
        }

        stage.toFront();
        stage.requestFocus();
        WindowsTitleBar.applyDarkTitleBar(stage);
    }

    /**
     * Reloads config.json and discards every unsaved Settings edit.
     */
    public void reloadFromDisk() {
        internalSync = true;
        current = configService.loadOrCreate();
        syncUiFromCurrent();
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
        if (!validateIntSpinner(
                maxClipChars,
                10_000,
                5_000_000,
                "Max clip chars"
        )) return;
        if (!validateIntSpinner(uiClipLimit, 50, 5_000, "UI clip limit")) return;

        DuplicateBehaviorPolicy duplicatePolicy = duplicatePolicyFromUi();
        if (duplicatePolicy == null) return;

        ExcludedApplicationPolicy excludedPolicy = excludedApplicationPolicyFromUi();
        if (excludedPolicy == null) return;

        Config next = current
                .withMaxHistory(maxHistory.getValue())
                .withMinClipLength(minClipLength.getValue())
                .withMaxClipChars(maxClipChars.getValue())
                .withUiClipLimit(uiClipLimit.getValue())
                .withWatcherEnabled(watcherEnabled.isSelected())
                .withStartMinimized(startMinimized.isSelected())
                .withStartOnBoot(startOnBoot.isSelected())
                .withDuplicateBehaviorPolicy(duplicatePolicy)
                .withExcludedApplications(excludedPolicy.executableNames());

        boolean autoStartChanged = current.startOnBoot() != next.startOnBoot();

        configService.save(next);
        current = next;

        try {
            clipService.applyConfig(next);
        } catch (Throwable ignored) {
        }

        if (next.watcherEnabled()) watcherController.enable();
        else watcherController.disable();

        try {
            onConfigApplied.accept(next);
        } catch (Throwable ignored) {
        }

        if (autoStartChanged) {
            showStatus(next.startOnBoot()
                    ? "Saved • Autostart enabled"
                    : "Saved • Autostart disabled");
        } else {
            showStatus("Saved");
        }

        clearDirty();
    }

    private DuplicateBehaviorPolicy duplicatePolicyFromUi() {
        duplicateCustomWindowMillis.getStyleClass().remove("input-error");

        try {
            return DuplicateSettingsModel.toPolicy(
                    duplicateRecentPosition.getValue(),
                    duplicatePinnedPosition.getValue(),
                    duplicateWhitespaceMode.getValue(),
                    duplicateCaseSensitivity.getValue(),
                    duplicateWindowPreset.getValue(),
                    duplicateCustomWindowMillis.getText(),
                    duplicateExactContentMode.isSelected()
            );
        } catch (IllegalArgumentException error) {
            duplicateCustomWindowMillis.getStyleClass().add("input-error");
            duplicateCustomWindowMillis.requestFocus();
            showStatus(error.getMessage() == null
                    ? "Invalid duplicate window"
                    : error.getMessage());
            return null;
        }
    }

    private ExcludedApplicationPolicy excludedApplicationPolicyFromUi() {
        excludedApplications.getStyleClass().remove("input-error");

        try {
            return ExcludedApplicationPolicy.fromMultilineText(
                    excludedApplications.getText()
            );
        } catch (IllegalArgumentException error) {
            excludedApplications.getStyleClass().add("input-error");
            excludedApplications.requestFocus();
            showStatus(error.getMessage() == null
                    ? "Invalid excluded application"
                    : error.getMessage());
            return null;
        }
    }

    private void resetDuplicateControlsToDefaults() {
        internalSync = true;
        syncDuplicateControls(DuplicateBehaviorPolicy.defaults());
        internalSync = false;
        markDirty();
        showStatus("Duplicate defaults restored • Apply to save");
    }

    private void syncUiFromCurrent() {
        maxHistory.getValueFactory().setValue(current.maxHistory());
        minClipLength.getValueFactory().setValue(current.minClipLength());
        maxClipChars.getValueFactory().setValue(current.maxClipChars());
        uiClipLimit.getValueFactory().setValue(current.uiClipLimit());
        watcherEnabled.setSelected(current.watcherEnabled());
        startMinimized.setSelected(current.startMinimized());
        startOnBoot.setSelected(current.startOnBoot());
        syncDuplicateControls(current.duplicateBehaviorPolicy());
        excludedApplications.setText(
                current.excludedApplicationPolicy().toMultilineText()
        );
        excludedApplications.getStyleClass().remove("input-error");
        syncAutostartCheckbox();
        forceSyncSpinnerEditors();
    }

    private void syncDuplicateControls(DuplicateBehaviorPolicy policy) {
        DuplicateBehaviorPolicy value = policy == null
                ? DuplicateBehaviorPolicy.defaults()
                : policy;

        duplicateRecentPosition.setValue(value.recentDuplicatePosition());
        duplicatePinnedPosition.setValue(value.pinnedDuplicatePosition());
        duplicateWhitespaceMode.setValue(value.whitespaceMode());
        duplicateCaseSensitivity.setValue(value.caseSensitivity());

        WindowPreset preset = DuplicateSettingsModel.presetFor(
                value.duplicateWindowMillis()
        );
        duplicateWindowPreset.setValue(preset);
        duplicateCustomWindowMillis.setText(
                DuplicateSettingsModel.customWindowText(
                        value.duplicateWindowMillis()
                )
        );
        duplicateCustomWindowMillis.getStyleClass().remove("input-error");

        duplicateExactContentMode.setSelected(value.exactContentMode());
        syncDuplicateWindowEditor();
        syncDuplicateMatchingAvailability();
    }

    private void syncDuplicateWindowEditor() {
        WindowPreset preset = duplicateWindowPreset.getValue();
        boolean custom = preset != null && preset.custom();
        duplicateCustomWindowMillis.setManaged(custom);
        duplicateCustomWindowMillis.setVisible(custom);
    }

    private void syncDuplicateMatchingAvailability() {
        boolean exact = duplicateExactContentMode.isSelected();
        duplicateWhitespaceMode.setDisable(exact);
        duplicateCaseSensitivity.setDisable(exact);
        duplicateExactOverrideHint.setManaged(exact);
        duplicateExactOverrideHint.setVisible(exact);
    }

    private void clearAllDataFlow() {
        boolean confirmed = UiDialogs.confirmClearAllData(
                stage,
                AppPaths.dataDir()
        );
        if (!confirmed) return;

        try {
            watcherController.disable();
        } catch (Throwable ignored) {
        }

        try {
            dataOwnershipService.clearAllData();
        } catch (Throwable failure) {
            try {
                if (current.watcherEnabled()) watcherController.enable();
            } catch (Throwable ignored) {
            }

            UiDialogs.showError(
                    stage,
                    "Failed to clear data",
                    "XClip couldn't delete its local data",
                    "Close other XClip instances and try again.\n\nData folder: "
                            + AppPaths.dataDir().toAbsolutePath()
            );
            showStatus("Clear data failed");
            return;
        }

        UiDialogs.showInformation(
                stage,
                "Data cleared",
                "All local XClip data was removed",
                "XClip will exit now. Restart it to continue."
        );

        Platform.exit();
        System.exit(0);
    }

    private void markDirtyUnlessSyncing() {
        if (!internalSync) markDirty();
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
        statusLabel.setAccessibleText("Settings status: " + text);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);

        statusHide = new PauseTransition(Duration.seconds(2));
        statusHide.setOnFinished(event -> {
            statusLabel.setVisible(false);
            statusLabel.setManaged(false);
        });
        statusHide.playFromStart();
    }

    private void syncAutostartCheckbox() {
        try {
            startOnBoot.setSelected(WindowsAutoStartService.isEnabled());
        } catch (Throwable ignored) {
            // Keep config state when the Registry cannot be queried.
        }
    }

    private void resetUiToCurrentSilently() {
        internalSync = true;
        syncUiFromCurrent();
        internalSync = false;
        clearDirty();
    }

    private void wireDirtyForIntSpinner(Spinner<Integer> spinner) {
        spinner.valueProperty().addListener(
                (observable, oldValue, newValue) -> markDirtyUnlessSyncing()
        );

        TextField editor = spinner.getEditor();
        editor.textProperty().addListener(
                (observable, oldValue, newValue) -> markDirtyUnlessSyncing()
        );
        editor.focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (wasFocused && !focused) commitSpinnerEditor(spinner);
        });
        editor.setOnAction(event -> commitSpinnerEditor(spinner));
    }

    private void commitSpinnerEditor(Spinner<Integer> spinner) {
        try {
            String text = spinner.getEditor().getText();
            if (text == null || text.trim().isEmpty()) return;
            spinner.getValueFactory().setValue(Integer.parseInt(text.trim()));
        } catch (Exception ignored) {
        }
    }

    private boolean validateIntSpinner(
            Spinner<Integer> spinner,
            int min,
            int max,
            String name
    ) {
        TextField editor = spinner.getEditor();
        editor.getStyleClass().remove("input-error");

        String text = Objects.requireNonNullElse(editor.getText(), "").trim();
        if (text.isEmpty()) {
            editor.getStyleClass().add("input-error");
            showStatus(name + ": required");
            return false;
        }

        int value;
        try {
            value = Integer.parseInt(text);
        } catch (Exception error) {
            editor.getStyleClass().add("input-error");
            showStatus(name + ": invalid number");
            return false;
        }

        if (value < min) {
            editor.setText(Integer.toString(min));
            commitSpinnerEditor(spinner);
            showStatus(name + ": clamped to " + min);
        } else if (value > max) {
            editor.setText(Integer.toString(max));
            commitSpinnerEditor(spinner);
            showStatus(name + ": clamped to " + max);
        }

        return true;
    }

    private void forceSyncSpinnerEditors() {
        syncSpinnerEditor(maxHistory);
        syncSpinnerEditor(minClipLength);
        syncSpinnerEditor(maxClipChars);
        syncSpinnerEditor(uiClipLimit);
    }

    private void syncSpinnerEditor(Spinner<Integer> spinner) {
        spinner.getEditor().setText(String.valueOf(spinner.getValue()));
        spinner.getEditor().getStyleClass().remove("input-error");
    }

    private static Spinner<Integer> intSpinner(
            int min,
            int max,
            int initial,
            int step,
            String accessibleText,
            String accessibleHelp
    ) {
        Spinner<Integer> spinner = new Spinner<>(min, max, initial, step);
        spinner.setEditable(true);
        spinner.setAccessibleText(accessibleText);
        spinner.setAccessibleHelp(accessibleHelp);
        spinner.getStyleClass().add("settings-control-wide");
        spinner.getEditor().setTextFormatter(
                new TextFormatter<>(change ->
                        change.getControlNewText().matches("\\d*") ? change : null
                )
        );
        spinner.getValueFactory().setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : value.toString();
            }

            @Override
            public Integer fromString(String text) {
                try {
                    spinner.getEditor().getStyleClass().remove("input-error");
                    return Integer.parseInt(text.trim());
                } catch (Exception error) {
                    spinner.getEditor().getStyleClass().add("input-error");
                    return spinner.getValue();
                }
            }
        });
        return spinner;
    }

    private static <T> ComboBox<T> enumCombo(
            T[] values,
            Function<T, String> labels,
            String accessibleText,
            String accessibleHelp
    ) {
        ComboBox<T> combo = new ComboBox<>();
        combo.getItems().setAll(values);
        combo.setConverter(labelConverter(labels));
        combo.setAccessibleText(accessibleText);
        combo.setAccessibleHelp(accessibleHelp);
        combo.getStyleClass().add("settings-control-wide");
        return combo;
    }

    private static <T> StringConverter<T> labelConverter(
            Function<T, String> labels
    ) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : labels.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    private static GridPane settingsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(14);

        ColumnConstraints textColumn = new ColumnConstraints();
        textColumn.setMinWidth(250);
        textColumn.setHgrow(Priority.ALWAYS);

        ColumnConstraints controlColumn = new ColumnConstraints();
        controlColumn.setMinWidth(250);

        grid.getColumnConstraints().addAll(textColumn, controlColumn);
        return grid;
    }

    private static int addSettingRow(
            GridPane grid,
            int row,
            String title,
            String description,
            Node control
    ) {
        grid.add(settingText(title, description), 0, row);
        grid.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
        return row + 1;
    }

    private static VBox settingText(String title, String description) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("settings-field-title");

        Label descriptionLabel = new Label(description);
        descriptionLabel.setWrapText(true);
        descriptionLabel.getStyleClass().add("settings-field-description");

        return new VBox(3, titleLabel, descriptionLabel);
    }

    private static VBox section(
            String title,
            String description,
            Node... content
    ) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("settings-section-title");

        Label descriptionLabel = new Label(description);
        descriptionLabel.setWrapText(true);
        descriptionLabel.getStyleClass().add("settings-section-description");

        VBox section = new VBox(12);
        section.getChildren().addAll(titleLabel, descriptionLabel);
        section.getChildren().addAll(content);
        section.getStyleClass().add("settings-section");
        return section;
    }
}
