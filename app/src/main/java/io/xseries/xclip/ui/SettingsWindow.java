
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui;

import io.xseries.xclip.AppVersion;
import io.xseries.xclip.config.AppPaths;
import io.xseries.xclip.config.Config;
import io.xseries.xclip.config.ConfigService;
import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;
import io.xseries.xclip.domain.privacy.ExcludedApplicationPolicy;
import io.xseries.xclip.domain.privacy.SensitiveContentPolicy;
import io.xseries.xclip.domain.retention.HistoryRetentionPolicy;
import io.xseries.xclip.domain.service.ClipService;
import io.xseries.xclip.domain.service.HistoryCleanupService;
import io.xseries.xclip.system.DataOwnershipService;
import io.xseries.xclip.system.ExternalOpenService;
import io.xseries.xclip.system.WindowsAutoStartService;
import io.xseries.xclip.system.clipboard.WatcherController;
import io.xseries.xclip.system.tray.TrayController;
import io.xseries.xclip.system.window.WindowChromeController;
import io.xseries.xclip.ui.settings.AboutSettingsContent;
import io.xseries.xclip.ui.settings.AboutSettingsPage;
import io.xseries.xclip.ui.settings.AppearanceSettingsPage;
import io.xseries.xclip.ui.settings.CaptureSettingsPage;
import io.xseries.xclip.ui.settings.DataSettingsPage;
import io.xseries.xclip.ui.settings.DatabaseMaintenanceText;
import io.xseries.xclip.ui.settings.DuplicateBehaviorSettingsPage;
import io.xseries.xclip.ui.settings.DuplicateSettingsModel;
import io.xseries.xclip.ui.settings.DuplicateSettingsModel.WindowPreset;
import io.xseries.xclip.ui.settings.GeneralSettingsPage;
import io.xseries.xclip.ui.settings.HistorySettingsPage;
import io.xseries.xclip.ui.settings.PrivacySettingsPage;
import io.xseries.xclip.ui.settings.SettingsAccessibilityText;
import io.xseries.xclip.ui.settings.SettingsDraft;
import io.xseries.xclip.ui.settings.SettingsDraftSession;
import io.xseries.xclip.ui.settings.SettingsDraftValidation;
import io.xseries.xclip.ui.settings.SettingsField;
import io.xseries.xclip.ui.settings.SettingsPage;
import io.xseries.xclip.ui.settings.SettingsResponsivePolicy;
import io.xseries.xclip.ui.settings.SettingsValidationIssue;
import io.xseries.xclip.ui.settings.ShortcutsSettingsPage;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.AccessibleRole;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Product Settings window.
 *
 * Apply persists config.json and immediately updates runtime behavior. Closing
 * the window discards unapplied edits and restores controls from the last saved
 * configuration snapshot.
 */
public final class SettingsWindow {

    private static final double RESIZE_EDGE = 6;
    private static final DateTimeFormatter CLEANUP_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter BACKUP_FILE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .withZone(ZoneId.systemDefault());

    private final Stage stage;
    private final WindowChromeController chromeController;
    private final Label pageTitleLabel = new Label();
    private final Label pageDescriptionLabel = new Label();
    private final StackPane pageHost = new StackPane();
    private final EnumMap<SettingsPage, ScrollPane> pageViews =
            new EnumMap<>(SettingsPage.class);
    private final EnumMap<SettingsPage, ToggleButton> navigationButtons =
            new EnumMap<>(SettingsPage.class);
    private SettingsPage selectedPage = SettingsPage.GENERAL;
    private Button maximizeWindowBtn;

    private final ConfigService configService;
    private final ClipService clipService;
    private final WatcherController watcherController;
    private final TrayController trayController;
    private final DataOwnershipService dataOwnershipService;
    private final ExternalOpenService externalOpenService = new ExternalOpenService();
    private final HistoryCleanupService historyCleanupService;

    private Config current;
    private final SettingsDraftSession draftSession;

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
    private final ComboBox<SensitiveContentPolicy.RuleAction> paymentCardAction;
    private final ComboBox<SensitiveContentPolicy.RuleAction> oneTimeCodeAction;
    private final Button resetSensitiveRulesBtn;

    private final CheckBox retentionRecentEnabled;
    private final Spinner<Integer> retentionRecentDays;
    private final Spinner<Integer> retentionTextDays;
    private final Spinner<Integer> retentionCodeDays;
    private final Spinner<Integer> retentionUrlDays;
    private final Spinner<Integer> retentionPathDays;
    private final Spinner<Integer> retentionJsonDays;
    private final Spinner<Integer> retentionCommandDays;
    private final CheckBox clearRecentOnExit;
    private final Label cleanupStatusLabel;
    private final Button runCleanupNowBtn;
    private final Button resetRetentionDefaultsBtn;

    private final Button applyBtn = new Button("Apply");
    private final Label validationLabel = new Label();
    private final EnumMap<SettingsField, Control> validationControls =
            new EnumMap<>(SettingsField.class);
    private boolean internalSync = false;

    private final Label statusLabel = new Label();
    private final AtomicBoolean dataOperationRunning = new AtomicBoolean(false);
    private ShortcutsSettingsPage.View shortcutsPageView;
    private DataSettingsPage.View dataPageView;
    private PauseTransition statusHide;
    private final java.util.function.Consumer<Config> onConfigApplied;

    public SettingsWindow(
            ConfigService configService,
            ClipService clipService,
            WatcherController watcherController,
            TrayController trayController,
            DataOwnershipService dataOwnershipService,
            HistoryCleanupService historyCleanupService,
            Config initial,
            java.util.function.Consumer<Config> onConfigApplied
    ) {
        this.configService = Objects.requireNonNull(configService);
        this.clipService = Objects.requireNonNull(clipService);
        this.watcherController = Objects.requireNonNull(watcherController);
        this.trayController = Objects.requireNonNull(trayController);
        this.dataOwnershipService = Objects.requireNonNull(dataOwnershipService);
        this.historyCleanupService = Objects.requireNonNull(historyCleanupService);
        this.current = (initial == null ? Config.defaults() : initial).normalized();
        this.draftSession = new SettingsDraftSession(this.current);
        this.onConfigApplied = onConfigApplied != null ? onConfigApplied : cfg -> {};

        stage = new Stage(StageStyle.UNDECORATED);
        stage.setTitle("XClip Settings");
        stage.getIcons().add(new javafx.scene.image.Image(
                SettingsWindow.class.getResourceAsStream("/icons/icon.png")
        ));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(true);
        stage.setMinWidth(SettingsResponsivePolicy.MIN_WIDTH);
        stage.setMinHeight(SettingsResponsivePolicy.MIN_HEIGHT);
        chromeController = WindowChromeController.forStage(
                stage,
                this::closeAndDiscard
        );

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

        paymentCardAction = enumCombo(
                SensitiveContentPolicy.RuleAction.values(),
                SettingsWindow::sensitiveRuleActionLabel,
                "Payment card capture rule",
                "Choose whether Luhn-valid payment-card-like values are captured or skipped."
        );
        oneTimeCodeAction = enumCombo(
                SensitiveContentPolicy.RuleAction.values(),
                SettingsWindow::sensitiveRuleActionLabel,
                "One-time code capture rule",
                "Choose whether contextual OTP and verification-code messages are captured or skipped."
        );
        resetSensitiveRulesBtn = new Button("Reset sensitive rules");
        resetSensitiveRulesBtn.setAccessibleHelp(
                "Restore normal capture for every sensitive-content rule."
        );
        resetSensitiveRulesBtn.getStyleClass().add("btn-subtle");

        HistoryRetentionPolicy initialRetention = current.historyRetentionPolicy();
        retentionRecentEnabled = new CheckBox("Auto-delete old RECENT clips");
        retentionRecentEnabled.setAccessibleHelp(
                "Enable the general age limit for unpinned clipboard history."
        );
        retentionRecentDays = retentionSpinner(
                initialRetention.recentMaxAgeDays(),
                "General RECENT retention in days",
                "Unpinned clips older than this value are deleted when the general rule is enabled."
        );
        retentionTextDays = typeRetentionSpinner(initialRetention, io.xseries.xclip.domain.model.ClipContentType.TEXT);
        retentionCodeDays = typeRetentionSpinner(initialRetention, io.xseries.xclip.domain.model.ClipContentType.CODE);
        retentionUrlDays = typeRetentionSpinner(initialRetention, io.xseries.xclip.domain.model.ClipContentType.URL);
        retentionPathDays = typeRetentionSpinner(initialRetention, io.xseries.xclip.domain.model.ClipContentType.PATH);
        retentionJsonDays = typeRetentionSpinner(initialRetention, io.xseries.xclip.domain.model.ClipContentType.JSON);
        retentionCommandDays = typeRetentionSpinner(initialRetention, io.xseries.xclip.domain.model.ClipContentType.COMMAND);

        clearRecentOnExit = new CheckBox("Clear all RECENT clips when XClip exits");
        clearRecentOnExit.setAccessibleHelp(
                "Delete every unpinned clipboard entry during a normal XClip shutdown."
        );

        cleanupStatusLabel = new Label();
        cleanupStatusLabel.setWrapText(true);
        cleanupStatusLabel.getStyleClass().add("settings-cleanup-status");
        cleanupStatusLabel.setAccessibleText("History cleanup status");

        runCleanupNowBtn = new Button("Run cleanup now");
        runCleanupNowBtn.setAccessibleHelp(
                "Apply the currently saved age-based retention rules immediately."
        );
        runCleanupNowBtn.getStyleClass().add("btn-subtle");

        resetRetentionDefaultsBtn = new Button("Reset retention defaults");
        resetRetentionDefaultsBtn.setAccessibleHelp(
                "Disable automatic age cleanup, type overrides, and clear on exit."
        );
        resetRetentionDefaultsBtn.getStyleClass().add("btn-subtle");

        validationLabel.getStyleClass().add("settings-validation-status");
        validationLabel.setAccessibleRole(AccessibleRole.BUTTON);
        validationLabel.setAccessibleText(
                SettingsAccessibilityText.validationAction("")
        );
        validationLabel.setAccessibleHelp(
                "Activate this message to open and focus the first invalid field."
        );
        validationLabel.setFocusTraversable(true);
        validationLabel.setWrapText(true);
        validationLabel.setMaxWidth(Double.MAX_VALUE);
        validationLabel.setMinWidth(0);
        validationLabel.setOnMouseClicked(event -> focusFirstValidationIssue());
        validationLabel.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER, SPACE -> {
                    focusFirstValidationIssue();
                    event.consume();
                }
                default -> {
                }
            }
        });
        validationLabel.setManaged(false);
        validationLabel.setVisible(false);

        statusLabel.getStyleClass().add("status-text");
        statusLabel.setAccessibleText(
                SettingsAccessibilityText.operationStatus("")
        );
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setMinWidth(0);
        statusLabel.setManaged(false);
        statusLabel.setVisible(false);

        registerValidationControls();

        pageViews.put(
                SettingsPage.GENERAL,
                GeneralSettingsPage.create(
                        watcherEnabled,
                        startMinimized,
                        startOnBoot
                )
        );
        pageViews.put(
                SettingsPage.CAPTURE,
                CaptureSettingsPage.create(
                        minClipLength,
                        maxClipChars,
                        uiClipLimit
                )
        );
        pageViews.put(
                SettingsPage.HISTORY,
                HistorySettingsPage.create(new HistorySettingsPage.Controls(
                        maxHistory,
                        retentionRecentEnabled,
                        retentionRecentDays,
                        retentionTextDays,
                        retentionCodeDays,
                        retentionUrlDays,
                        retentionPathDays,
                        retentionJsonDays,
                        retentionCommandDays,
                        clearRecentOnExit,
                        cleanupStatusLabel,
                        runCleanupNowBtn,
                        resetRetentionDefaultsBtn
                ))
        );
        pageViews.put(
                SettingsPage.DUPLICATE_BEHAVIOR,
                DuplicateBehaviorSettingsPage.create(
                        new DuplicateBehaviorSettingsPage.Controls(
                                duplicateRecentPosition,
                                duplicatePinnedPosition,
                                duplicateWhitespaceMode,
                                duplicateCaseSensitivity,
                                duplicateWindowPreset,
                                duplicateCustomWindowMillis,
                                duplicateExactContentMode,
                                duplicateExactOverrideHint,
                                resetDuplicateDefaultsBtn
                        )
                )
        );
        pageViews.put(
                SettingsPage.PRIVACY,
                PrivacySettingsPage.create(new PrivacySettingsPage.Controls(
                        excludedApplications,
                        clearExcludedApplicationsBtn,
                        paymentCardAction,
                        oneTimeCodeAction,
                        resetSensitiveRulesBtn
                ))
        );
        pageViews.put(
                SettingsPage.APPEARANCE,
                AppearanceSettingsPage.create()
        );
        shortcutsPageView = ShortcutsSettingsPage.create(
                trayController.hotkeyStatus()
        );
        pageViews.put(SettingsPage.SHORTCUTS, shortcutsPageView.root());

        dataPageView = DataSettingsPage.create(
                AppPaths.dataDir(),
                AppPaths.dbPath(),
                AppPaths.configPath(),
                dataOwnershipService::openDataFolder,
                this::refreshDatabaseStatusFlow,
                this::checkDatabaseIntegrityFlow,
                this::checkpointWalFlow,
                this::optimizeDatabaseFlow,
                this::createBackupFlow,
                this::restoreBackupFlow,
                this::scheduleRetentionCleanup,
                this::clearRecentFlow,
                this::clearAllDataFlow,
                this::showStatus
        );
        pageViews.put(SettingsPage.DATA, dataPageView.root());

        pageViews.put(
                SettingsPage.ABOUT,
                AboutSettingsPage.create(
                        AppVersion.VERSION,
                        this::openProductLink,
                        this::showThirdPartyNotices
                )
        );

        configurePageAccessibility();

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setAccessibleText("Cancel Settings changes");
        cancelBtn.setAccessibleHelp(
                "Close Settings and discard unapplied changes."
        );
        cancelBtn.getStyleClass().add("btn-subtle");
        cancelBtn.setCancelButton(true);

        applyBtn.setDefaultButton(true);
        applyBtn.setAccessibleText("Apply Settings changes");
        applyBtn.setAccessibleHelp("Save and apply the current settings.");
        applyBtn.getStyleClass().add("btn-apply");
        applyBtn.setDisable(true);

        applyBtn.setOnAction(event -> apply());
        cancelBtn.setOnAction(event -> closeAndDiscard());

        VBox feedback = new VBox(2, validationLabel, statusLabel);
        feedback.setMinWidth(0);
        HBox.setHgrow(feedback, Priority.ALWAYS);
        feedback.getStyleClass().add("settings-feedback");

        HBox footerActions = new HBox(10, applyBtn, cancelBtn);
        footerActions.setAlignment(Pos.CENTER_RIGHT);
        footerActions.getStyleClass().add("settings-footer-actions");

        HBox bottomBar = new HBox(12, feedback, footerActions);
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        bottomBar.getStyleClass().add("settings-bottom-bar");

        VBox sidebar = buildNavigation();
        VBox pagePane = buildPagePane();
        HBox body = new HBox(sidebar, pagePane);
        HBox.setHgrow(pagePane, Priority.ALWAYS);
        body.getStyleClass().add("settings-body");

        BorderPane root = new BorderPane();
        root.setTop(buildTitleBar());
        root.setCenter(body);
        root.setBottom(bottomBar);
        root.getStyleClass().add("settings-root");

        SettingsResponsivePolicy.WindowSize initialSize =
                initialWindowSize();
        Scene scene = new Scene(
                root,
                initialSize.width(),
                initialSize.height()
        );
        UiStyles.applySettings(scene);
        stage.setScene(scene);

        applyResponsiveMode(root, scene.getWidth());
        scene.widthProperty().addListener(
                (observable, oldValue, newValue) ->
                        applyResponsiveMode(root, newValue.doubleValue())
        );

        chromeController.installResizeSupport(
                scene,
                RESIZE_EDGE,
                SettingsResponsivePolicy.MIN_WIDTH,
                SettingsResponsivePolicy.MIN_HEIGHT
        );
        stage.maximizedProperty().addListener(
                (observable, oldValue, newValue) -> syncMaximizeButton()
        );

        selectPage(selectedPage);

        stage.setOnHiding(event -> {
            if (draftSession.dirty()) {
                discardDraftSilently();
            } else {
                internalSync = true;
                syncUiFromDraft(draftSession.baseline());
                internalSync = false;
                renderDraftState();
            }
        });

        stage.setOnCloseRequest(event -> {
            closeAndDiscard();
            event.consume();
        });

        wireDirtyForIntSpinner(maxHistory);
        wireDirtyForIntSpinner(minClipLength);
        wireDirtyForIntSpinner(maxClipChars);
        wireDirtyForIntSpinner(uiClipLimit);
        wireDirtyForIntSpinner(retentionRecentDays);
        wireDirtyForIntSpinner(retentionTextDays);
        wireDirtyForIntSpinner(retentionCodeDays);
        wireDirtyForIntSpinner(retentionUrlDays);
        wireDirtyForIntSpinner(retentionPathDays);
        wireDirtyForIntSpinner(retentionJsonDays);
        wireDirtyForIntSpinner(retentionCommandDays);

        watcherEnabled.selectedProperty().addListener(
                (observable, oldValue, newValue) -> refreshDraftStateUnlessSyncing()
        );
        startMinimized.selectedProperty().addListener(
                (observable, oldValue, newValue) -> refreshDraftStateUnlessSyncing()
        );
        startOnBoot.selectedProperty().addListener(
                (observable, oldValue, newValue) -> refreshDraftStateUnlessSyncing()
        );

        duplicateRecentPosition.valueProperty().addListener(
                (observable, oldValue, newValue) -> refreshDraftStateUnlessSyncing()
        );
        duplicatePinnedPosition.valueProperty().addListener(
                (observable, oldValue, newValue) -> refreshDraftStateUnlessSyncing()
        );
        duplicateWhitespaceMode.valueProperty().addListener(
                (observable, oldValue, newValue) -> refreshDraftStateUnlessSyncing()
        );
        duplicateCaseSensitivity.valueProperty().addListener(
                (observable, oldValue, newValue) -> refreshDraftStateUnlessSyncing()
        );
        duplicateWindowPreset.valueProperty().addListener(
                (observable, oldValue, newValue) -> {
                    syncDuplicateWindowEditor();
                    refreshDraftStateUnlessSyncing();
                }
        );
        duplicateCustomWindowMillis.textProperty().addListener(
                (observable, oldValue, newValue) -> refreshDraftStateUnlessSyncing()
        );
        duplicateExactContentMode.selectedProperty().addListener(
                (observable, oldValue, newValue) -> {
                    syncDuplicateMatchingAvailability();
                    refreshDraftStateUnlessSyncing();
                }
        );

        resetDuplicateDefaultsBtn.setOnAction(
                event -> resetDuplicateControlsToDefaults()
        );

        excludedApplications.textProperty().addListener(
                (observable, oldValue, newValue) -> refreshDraftStateUnlessSyncing()
        );
        clearExcludedApplicationsBtn.setOnAction(event -> {
            excludedApplications.clear();
            showStatus("Application exclusions cleared • Apply to save");
        });

        paymentCardAction.valueProperty().addListener(
                (observable, oldValue, newValue) -> refreshDraftStateUnlessSyncing()
        );
        oneTimeCodeAction.valueProperty().addListener(
                (observable, oldValue, newValue) -> refreshDraftStateUnlessSyncing()
        );
        resetSensitiveRulesBtn.setOnAction(event -> resetSensitiveControlsToDefaults());

        retentionRecentEnabled.selectedProperty().addListener(
                (observable, oldValue, newValue) -> {
                    syncRetentionAvailability();
                    refreshDraftStateUnlessSyncing();
                }
        );
        clearRecentOnExit.selectedProperty().addListener(
                (observable, oldValue, newValue) -> refreshDraftStateUnlessSyncing()
        );
        runCleanupNowBtn.setOnAction(event -> scheduleRetentionCleanup());
        resetRetentionDefaultsBtn.setOnAction(
                event -> resetRetentionControlsToDefaults()
        );
        historyCleanupService.addStatusListener(status -> Platform.runLater(() -> {
            updateCleanupStatus(status);
            if (status != null
                    && status.trigger()
                    == HistoryCleanupService.CleanupTrigger.MANUAL_CLEAR_RECENT) {
                setDataMaintenanceBusy(false);
                showStatus(
                        status.outcome() == HistoryCleanupService.CleanupOutcome.FAILED
                                ? "Clear RECENT failed"
                                : status.detail()
                );
            }
        }));
        trayController.addHotkeyStatusListener(status -> Platform.runLater(
                () -> shortcutsPageView.updateHotkeyStatus(status)
        ));

        internalSync = true;
        syncUiFromDraft(draftSession.current());
        internalSync = false;
        renderDraftState();
    }

    public void show() {
        if (!stage.isShowing()) {
            internalSync = true;
            syncUiFromDraft(draftSession.current());
            syncAutostartCheckbox();
            updateCleanupStatus(historyCleanupService.status());
            internalSync = false;
            refreshDraftStateFromUi();

            stage.centerOnScreen();
            stage.show();
        } else {
            updateCleanupStatus(historyCleanupService.status());
        }

        stage.toFront();
        stage.requestFocus();
        selectPage(selectedPage);
        Platform.runLater(this::focusSelectedNavigation);
        if (!dataOperationRunning.get()) refreshDatabaseStatusFlow();
    }

    private void apply() {
        refreshDraftStateFromUi();
        if (!draftSession.dirty()) {
            showStatus("No changes");
            return;
        }

        SettingsDraftValidation validation = draftSession.validation();
        if (!validation.valid()) {
            focusFirstValidationIssue();
            showStatus(validation.firstIssue()
                    .map(SettingsValidationIssue::displayMessage)
                    .orElse("Invalid settings"));
            return;
        }

        Config next = validation.toConfig(current);
        boolean autoStartChanged = current.startOnBoot() != next.startOnBoot();

        configService.save(next);
        current = next;

        try {
            clipService.applyConfig(next);
        } catch (Throwable ignored) {
        }
        try {
            historyCleanupService.applyConfig(next);
        } catch (Throwable ignored) {
        }

        if (next.watcherEnabled()) watcherController.enable();
        else watcherController.disable();

        try {
            onConfigApplied.accept(next);
        } catch (Throwable ignored) {
        }

        draftSession.commit(next);
        internalSync = true;
        syncUiFromDraft(draftSession.current());
        internalSync = false;
        renderDraftState();

        if (autoStartChanged) {
            showStatus(next.startOnBoot()
                    ? "Saved • Autostart enabled"
                    : "Saved • Autostart disabled");
        } else {
            showStatus("Saved");
        }
    }

    private SettingsDraft captureDraftFromUi() {
        return new SettingsDraft(
                new SettingsDraft.General(
                        watcherEnabled.isSelected(),
                        startMinimized.isSelected(),
                        startOnBoot.isSelected()
                ),
                new SettingsDraft.Capture(
                        spinnerText(minClipLength),
                        spinnerText(maxClipChars),
                        spinnerText(uiClipLimit)
                ),
                new SettingsDraft.History(spinnerText(maxHistory)),
                new SettingsDraft.Duplicate(
                        duplicateRecentPosition.getValue(),
                        duplicatePinnedPosition.getValue(),
                        duplicateWhitespaceMode.getValue(),
                        duplicateCaseSensitivity.getValue(),
                        duplicateWindowPreset.getValue(),
                        duplicateCustomWindowMillis.getText(),
                        duplicateExactContentMode.isSelected()
                ),
                new SettingsDraft.Privacy(
                        excludedApplications.getText(),
                        paymentCardAction.getValue(),
                        oneTimeCodeAction.getValue()
                ),
                new SettingsDraft.Retention(
                        retentionRecentEnabled.isSelected(),
                        spinnerText(retentionRecentDays),
                        spinnerText(retentionTextDays),
                        spinnerText(retentionCodeDays),
                        spinnerText(retentionUrlDays),
                        spinnerText(retentionPathDays),
                        spinnerText(retentionJsonDays),
                        spinnerText(retentionCommandDays),
                        clearRecentOnExit.isSelected()
                )
        );
    }

    private void resetDuplicateControlsToDefaults() {
        SettingsDraft next = captureDraftFromUi().withDuplicateDefaults();
        internalSync = true;
        syncDuplicateControls(next.duplicate());
        internalSync = false;
        draftSession.replaceCurrent(next);
        renderDraftState();
        showStatus("Duplicate defaults restored • Apply to save");
    }

    private void resetSensitiveControlsToDefaults() {
        SettingsDraft next = captureDraftFromUi().withSensitiveDefaults();
        internalSync = true;
        syncSensitiveControls(next.privacy());
        internalSync = false;
        draftSession.replaceCurrent(next);
        renderDraftState();
        showStatus("Sensitive rules reset • Apply to save");
    }

    private void resetRetentionControlsToDefaults() {
        SettingsDraft next = captureDraftFromUi().withRetentionDefaults();
        internalSync = true;
        syncRetentionControls(next.retention());
        internalSync = false;
        draftSession.replaceCurrent(next);
        renderDraftState();
        showStatus("Retention defaults restored • Apply to save");
    }

    private void syncUiFromDraft(SettingsDraft draft) {
        SettingsDraft value = Objects.requireNonNull(draft, "draft");
        setSpinnerText(maxHistory, value.history().maxHistory());
        setSpinnerText(minClipLength, value.capture().minClipLength());
        setSpinnerText(maxClipChars, value.capture().maxClipChars());
        setSpinnerText(uiClipLimit, value.capture().uiClipLimit());
        watcherEnabled.setSelected(value.general().watcherEnabled());
        startMinimized.setSelected(value.general().startMinimized());
        startOnBoot.setSelected(value.general().startOnBoot());
        syncDuplicateControls(value.duplicate());
        excludedApplications.setText(value.privacy().excludedApplications());
        syncSensitiveControls(value.privacy());
        syncRetentionControls(value.retention());
    }

    private void syncSensitiveControls(SettingsDraft.Privacy privacy) {
        SettingsDraft.Privacy value = Objects.requireNonNull(privacy, "privacy");
        paymentCardAction.setValue(value.paymentCardAction());
        oneTimeCodeAction.setValue(value.oneTimeCodeAction());
    }

    private void syncRetentionControls(SettingsDraft.Retention retention) {
        SettingsDraft.Retention value = Objects.requireNonNull(retention, "retention");
        retentionRecentEnabled.setSelected(value.recentEnabled());
        setSpinnerText(retentionRecentDays, value.recentDays());
        setSpinnerText(retentionTextDays, value.textDays());
        setSpinnerText(retentionCodeDays, value.codeDays());
        setSpinnerText(retentionUrlDays, value.urlDays());
        setSpinnerText(retentionPathDays, value.pathDays());
        setSpinnerText(retentionJsonDays, value.jsonDays());
        setSpinnerText(retentionCommandDays, value.commandDays());
        clearRecentOnExit.setSelected(value.clearRecentOnExit());
        syncRetentionAvailability();
    }

    private void syncRetentionAvailability() {
        retentionRecentDays.setDisable(!retentionRecentEnabled.isSelected());
    }

    private void updateCleanupStatus(HistoryCleanupService.CleanupStatus status) {
        if (status == null || status.outcome() == HistoryCleanupService.CleanupOutcome.NOT_RUN) {
            String text = "Last cleanup: not run yet";
            cleanupStatusLabel.setText(text);
            if (dataPageView != null) dataPageView.updateCleanupStatus(text);
            return;
        }
        String time = status.completedAt() <= 0
                ? "unknown time"
                : CLEANUP_TIME_FORMAT.format(Instant.ofEpochMilli(status.completedAt()));
        String result = switch (status.outcome()) {
            case SUCCESS -> "success";
            case SKIPPED -> "skipped";
            case FAILED -> "failed";
            case TIMED_OUT -> "timed out";
            case NOT_RUN -> "not run";
        };
        String text = "Last cleanup: " + result + " • " + status.deletedCount()
                + " deleted • " + time + " • " + status.detail();
        cleanupStatusLabel.setText(text);
        if (dataPageView != null) dataPageView.updateCleanupStatus(text);
    }

    private void syncDuplicateControls(SettingsDraft.Duplicate duplicate) {
        SettingsDraft.Duplicate value = Objects.requireNonNull(
                duplicate,
                "duplicate"
        );

        duplicateRecentPosition.setValue(value.recentPosition());
        duplicatePinnedPosition.setValue(value.pinnedPosition());
        duplicateWhitespaceMode.setValue(value.whitespaceMode());
        duplicateCaseSensitivity.setValue(value.caseSensitivity());
        duplicateWindowPreset.setValue(value.windowPreset());
        duplicateCustomWindowMillis.setText(value.customWindowMillis());
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


    private void refreshDatabaseStatusFlow() {
        if (dataOperationRunning.get()) return;

        runDatabaseReadOperation(
                "Reading database status…",
                dataOwnershipService::databaseStatus,
                status -> {
                    dataPageView.updateDatabaseStatus(
                            DatabaseMaintenanceText.status(status)
                    );
                    showStatus("Database status refreshed");
                },
                "Failed to read database status"
        );
    }

    private void checkDatabaseIntegrityFlow() {
        if (dataOperationRunning.get()) {
            showStatus("Data maintenance is already running");
            return;
        }

        runDatabaseReadOperation(
                "Running integrity_check…",
                dataOwnershipService::checkDatabaseIntegrity,
                report -> {
                    String text = DatabaseMaintenanceText.integrity(report);
                    dataPageView.updateDatabaseStatus(text);
                    showStatus(report.ok()
                            ? "Database integrity OK"
                            : "Database integrity check failed");
                },
                "Failed to check database integrity"
        );
    }

    private void checkpointWalFlow() {
        runExclusiveDatabaseOperation(
                "Checkpointing SQLite WAL…",
                dataOwnershipService::checkpointWal,
                result -> {
                    dataPageView.updateDatabaseStatus(
                            DatabaseMaintenanceText.checkpoint(result)
                    );
                    showStatus(result.complete()
                            ? "WAL checkpoint completed"
                            : "WAL checkpoint is busy");
                },
                "Failed to checkpoint SQLite WAL",
                true
        );
    }

    private void optimizeDatabaseFlow() {
        if (dataOperationRunning.get()) {
            showStatus("Data maintenance is already running");
            return;
        }
        if (!UiDialogs.confirmOptimizeDatabase(stage)) return;

        runExclusiveDatabaseOperation(
                "Optimizing database…",
                dataOwnershipService::optimizeDatabase,
                result -> {
                    dataPageView.updateDatabaseStatus(
                            DatabaseMaintenanceText.vacuum(result)
                    );
                    showStatus("Database optimization completed");
                },
                "Failed to optimize database",
                true
        );
    }

    private void createBackupFlow() {
        if (dataOperationRunning.get()) {
            showStatus("Data maintenance is already running");
            return;
        }

        FileChooser chooser = backupFileChooser(false);
        chooser.setInitialFileName(
                "XClip-backup-"
                        + BACKUP_FILE_TIME_FORMAT.format(Instant.now())
                        + ".xclip-backup"
        );
        File selected = chooser.showSaveDialog(stage);
        if (selected == null) return;

        Path destination = selected.toPath();
        runExclusiveDatabaseOperation(
                "Creating XClip backup…",
                () -> dataOwnershipService.createBackup(
                        destination,
                        AppVersion.VERSION
                ),
                result -> {
                    String text = DatabaseMaintenanceText.backup(result);
                    dataPageView.updateBackupStatus(text);
                    UiDialogs.showInformation(
                            stage,
                            "Backup created",
                            "XClip backup was created",
                            text + "\n\n" + result.path().toAbsolutePath()
                    );
                    showStatus("Backup created");
                },
                "Failed to create backup",
                true
        );
    }

    private void restoreBackupFlow() {
        if (dataOperationRunning.get()) {
            showStatus("Data maintenance is already running");
            return;
        }

        FileChooser chooser = backupFileChooser(true);
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) return;
        Path source = selected.toPath();

        runDatabaseReadOperation(
                "Validating XClip backup…",
                () -> dataOwnershipService.inspectBackup(source),
                descriptor -> {
                    String summary = DatabaseMaintenanceText.backupDescriptor(
                            descriptor
                    );
                    dataPageView.updateBackupStatus(summary);

                    if (!UiDialogs.confirmRestoreBackup(
                            stage,
                            source,
                            summary
                    )) {
                        showStatus("Restore cancelled");
                        return;
                    }
                    restoreValidatedBackup(source);
                },
                "Backup validation failed"
        );
    }

    private void restoreValidatedBackup(Path source) {
        runExclusiveDatabaseOperation(
                "Restoring XClip backup…",
                () -> dataOwnershipService.restoreBackup(source),
                result -> {
                    String text = DatabaseMaintenanceText.restore(result);
                    dataPageView.updateBackupStatus(text);
                    UiDialogs.showInformation(
                            stage,
                            "Backup restored",
                            "Local XClip data was restored",
                            text + "\n\nXClip will exit now. Restart it to load the restored data."
                    );
                    Platform.exit();
                    System.exit(0);
                },
                "Failed to restore backup",
                false
        );
    }

    private FileChooser backupFileChooser(boolean restore) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(restore
                ? "Restore XClip backup"
                : "Create XClip backup");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "XClip backup (*.xclip-backup)",
                        "*.xclip-backup"
                )
        );

        try {
            Path home = Path.of(System.getProperty("user.home"));
            if (Files.isDirectory(home)) {
                chooser.setInitialDirectory(home.toFile());
            }
        } catch (Exception ignored) {
        }
        return chooser;
    }

    private <T> void runDatabaseReadOperation(
            String runningMessage,
            Supplier<T> operation,
            Consumer<T> onSuccess,
            String failureHeading
    ) {
        if (dataOperationRunning.get()) {
            showStatus("Data maintenance is already running");
            return;
        }

        setDataMaintenanceBusy(true);
        showStatus(runningMessage);

        CompletableFuture.supplyAsync(operation)
                .whenComplete((result, failure) -> Platform.runLater(() -> {
                    setDataMaintenanceBusy(false);
                    if (failure != null) {
                        showDatabaseOperationError(
                                failureHeading,
                                unwrapAsyncFailure(failure)
                        );
                        return;
                    }
                    onSuccess.accept(result);
                }));
    }

    private <T> void runExclusiveDatabaseOperation(
            String runningMessage,
            Supplier<T> operation,
            Consumer<T> onSuccess,
            String failureHeading,
            boolean resumeOnSuccess
    ) {
        if (dataOperationRunning.get()) {
            showStatus("Data maintenance is already running");
            return;
        }

        setDataMaintenanceBusy(true);
        showStatus(runningMessage);

        CompletableFuture.supplyAsync(() -> {
            boolean paused = false;
            try {
                watcherController.disable();
                historyCleanupService.pauseForMaintenance();
                paused = true;

                T result = operation.get();
                if (resumeOnSuccess) {
                    resumeRuntimeAfterDatabaseMaintenance();
                    paused = false;
                }
                return result;
            } catch (Throwable failure) {
                if (paused) {
                    try {
                        resumeRuntimeAfterDatabaseMaintenance();
                    } catch (Throwable resumeFailure) {
                        failure.addSuppressed(resumeFailure);
                    }
                }
                throw new CompletionException(failure);
            }
        }).whenComplete((result, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                setDataMaintenanceBusy(false);
                showDatabaseOperationError(
                        failureHeading,
                        unwrapAsyncFailure(failure)
                );
                return;
            }

            if (resumeOnSuccess) setDataMaintenanceBusy(false);
            onSuccess.accept(result);
        }));
    }

    private void resumeRuntimeAfterDatabaseMaintenance() {
        historyCleanupService.resumeAfterMaintenance();
        if (current.watcherEnabled()) watcherController.enable();
        else watcherController.disable();
    }

    private void showDatabaseOperationError(
            String heading,
            Throwable failure
    ) {
        String detail;
        if (failure == null) {
            detail = "Unknown database maintenance error";
        } else if (failure.getMessage() == null
                || failure.getMessage().isBlank()) {
            detail = failure.getClass().getSimpleName();
        } else {
            detail = failure.getMessage();
        }

        UiDialogs.showError(
                stage,
                "Database maintenance failed",
                heading,
                detail
        );
        showStatus(heading);
    }

    private Throwable unwrapAsyncFailure(Throwable failure) {
        Throwable currentFailure = failure;
        while ((currentFailure instanceof CompletionException
                || currentFailure instanceof java.util.concurrent.ExecutionException)
                && currentFailure.getCause() != null) {
            currentFailure = currentFailure.getCause();
        }
        return currentFailure;
    }

    private void setDataMaintenanceBusy(boolean busy) {
        dataOperationRunning.set(busy);
        if (dataPageView != null) dataPageView.setMaintenanceBusy(busy);
        if (busy) applyBtn.setDisable(true);
        else renderDraftState();
    }

    private void scheduleRetentionCleanup() {
        if (dataOperationRunning.get()) {
            showStatus("Data maintenance is already running");
            return;
        }
        historyCleanupService.requestCleanup(
                HistoryCleanupService.CleanupTrigger.MANUAL
        );
        showStatus("Cleanup scheduled");
    }

    private void clearRecentFlow() {
        if (dataOperationRunning.get()) {
            showStatus("Data maintenance is already running");
            return;
        }
        if (!UiDialogs.confirmClearRecent(stage)) return;

        setDataMaintenanceBusy(true);
        if (!historyCleanupService.requestClearRecent()) {
            setDataMaintenanceBusy(false);
            showStatus("Clear RECENT is unavailable");
            return;
        }
        showStatus("Clear RECENT scheduled");
    }

    private void clearAllDataFlow() {
        if (dataOperationRunning.get()) {
            showStatus("Data maintenance is already running");
            return;
        }
        boolean confirmed = UiDialogs.confirmClearAllData(
                stage,
                AppPaths.dataDir()
        );
        if (!confirmed) return;

        setDataMaintenanceBusy(true);
        showStatus("Clearing local data…");

        CompletableFuture.supplyAsync(this::clearAllDataInBackground)
                .whenComplete((failure, asyncFailure) -> Platform.runLater(() -> {
                    Throwable effectiveFailure = failure != null
                            ? failure
                            : asyncFailure;
                    if (effectiveFailure != null) {
                        setDataMaintenanceBusy(false);
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
                }));
    }

    private Throwable clearAllDataInBackground() {
        boolean cleanupPaused = false;
        try {
            watcherController.disable();
            historyCleanupService.pauseForMaintenance();
            cleanupPaused = true;

            dataOwnershipService.clearAllData();

            historyCleanupService.close();
            cleanupPaused = false;
            return null;
        } catch (Throwable failure) {
            if (cleanupPaused) {
                try {
                    historyCleanupService.resumeAfterMaintenance();
                } catch (Throwable ignored) {
                }
            }
            try {
                if (current.watcherEnabled()) watcherController.enable();
            } catch (Throwable ignored) {
            }
            return failure;
        }
    }

    private void openProductLink(String url) {
        ExternalOpenService.OpenResult result = externalOpenService.openUrl(url);
        switch (result) {
            case OPENED -> showStatus("Opened in browser");
            case INVALID_INPUT -> showStatus("Invalid project link");
            case UNSUPPORTED -> showStatus("Browser opening is unavailable");
            case NOT_FOUND, FAILED -> showStatus("Couldn't open project link");
        }
    }

    private void showThirdPartyNotices() {
        UiDialogs.showInformation(
                stage,
                "Third-party notices",
                "Bundled open-source components",
                AboutSettingsContent.thirdPartyNotices()
        );
    }

    private void refreshDraftStateUnlessSyncing() {
        if (!internalSync) refreshDraftStateFromUi();
    }

    private void refreshDraftStateFromUi() {
        draftSession.replaceCurrent(captureDraftFromUi());
        renderDraftState();
    }

    private void renderDraftState() {
        SettingsDraftValidation validation = draftSession.validation();
        applyBtn.setDisable(!draftSession.canApply());

        for (Control control : validationControls.values()) {
            control.getStyleClass().remove("input-error");
        }
        duplicateWindowPreset.getStyleClass().remove("input-error");
        duplicateCustomWindowMillis.getStyleClass().remove("input-error");
        for (ToggleButton button : navigationButtons.values()) {
            button.getStyleClass().remove("validation-error");
        }

        for (SettingsValidationIssue issue : validation.issues()) {
            Control control = validationControlFor(issue.field());
            if (control != null
                    && !control.getStyleClass().contains("input-error")) {
                control.getStyleClass().add("input-error");
            }
            ToggleButton navigation = navigationButtons.get(issue.page());
            if (navigation != null
                    && !navigation.getStyleClass().contains("validation-error")) {
                navigation.getStyleClass().add("validation-error");
            }
        }

        if (validation.valid()) {
            validationLabel.setText("");
            validationLabel.setVisible(false);
            validationLabel.setManaged(false);
        } else {
            String message = validation.firstIssue()
                    .map(issue -> issue.page().title() + " • " + issue.displayMessage())
                    .orElse("Invalid settings");
            validationLabel.setText(message);
            validationLabel.setAccessibleText(
                    SettingsAccessibilityText.validationAction(message)
            );
            validationLabel.setVisible(true);
            validationLabel.setManaged(true);
        }
    }

    private Control validationControlFor(SettingsField field) {
        if (field == SettingsField.DUPLICATE_WINDOW) {
            WindowPreset preset = duplicateWindowPreset.getValue();
            return preset != null && preset.custom()
                    ? duplicateCustomWindowMillis
                    : duplicateWindowPreset;
        }
        return validationControls.get(field);
    }

    private void focusFirstValidationIssue() {
        draftSession.validation().firstIssue().ifPresent(issue -> {
            selectPage(issue.page());
            Control control = validationControlFor(issue.field());
            if (control instanceof TextField textField) {
                textField.requestFocus();
                textField.selectAll();
            } else if (control != null) {
                control.requestFocus();
            }
        });
    }

    private void showStatus(String text) {
        if (statusHide != null) statusHide.stop();

        statusLabel.setText(text);
        statusLabel.setAccessibleText(SettingsAccessibilityText.operationStatus(text));
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

    private void discardDraftSilently() {
        draftSession.discard();
        internalSync = true;
        syncUiFromDraft(draftSession.current());
        internalSync = false;
        renderDraftState();
    }

    private void registerValidationControls() {
        validationControls.put(SettingsField.MIN_CLIP_LENGTH, minClipLength.getEditor());
        validationControls.put(SettingsField.MAX_CLIP_CHARS, maxClipChars.getEditor());
        validationControls.put(SettingsField.UI_CLIP_LIMIT, uiClipLimit.getEditor());
        validationControls.put(SettingsField.MAX_HISTORY, maxHistory.getEditor());
        validationControls.put(
                SettingsField.RETENTION_RECENT_DAYS,
                retentionRecentDays.getEditor()
        );
        validationControls.put(
                SettingsField.RETENTION_TEXT_DAYS,
                retentionTextDays.getEditor()
        );
        validationControls.put(
                SettingsField.RETENTION_CODE_DAYS,
                retentionCodeDays.getEditor()
        );
        validationControls.put(
                SettingsField.RETENTION_URL_DAYS,
                retentionUrlDays.getEditor()
        );
        validationControls.put(
                SettingsField.RETENTION_PATH_DAYS,
                retentionPathDays.getEditor()
        );
        validationControls.put(
                SettingsField.RETENTION_JSON_DAYS,
                retentionJsonDays.getEditor()
        );
        validationControls.put(
                SettingsField.RETENTION_COMMAND_DAYS,
                retentionCommandDays.getEditor()
        );
        validationControls.put(
                SettingsField.DUPLICATE_RECENT_POSITION,
                duplicateRecentPosition
        );
        validationControls.put(
                SettingsField.DUPLICATE_PINNED_POSITION,
                duplicatePinnedPosition
        );
        validationControls.put(
                SettingsField.DUPLICATE_WHITESPACE_MODE,
                duplicateWhitespaceMode
        );
        validationControls.put(
                SettingsField.DUPLICATE_CASE_SENSITIVITY,
                duplicateCaseSensitivity
        );
        validationControls.put(
                SettingsField.DUPLICATE_WINDOW,
                duplicateCustomWindowMillis
        );
        validationControls.put(
                SettingsField.EXCLUDED_APPLICATIONS,
                excludedApplications
        );
        validationControls.put(
                SettingsField.PAYMENT_CARD_ACTION,
                paymentCardAction
        );
        validationControls.put(
                SettingsField.ONE_TIME_CODE_ACTION,
                oneTimeCodeAction
        );
    }

    private void wireDirtyForIntSpinner(Spinner<Integer> spinner) {
        spinner.valueProperty().addListener(
                (observable, oldValue, newValue) -> refreshDraftStateUnlessSyncing()
        );

        TextField editor = spinner.getEditor();
        editor.textProperty().addListener(
                (observable, oldValue, newValue) -> refreshDraftStateUnlessSyncing()
        );
        editor.focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (wasFocused && !focused) refreshDraftStateUnlessSyncing();
        });
        editor.setOnAction(event -> refreshDraftStateUnlessSyncing());
    }

    private static String spinnerText(Spinner<Integer> spinner) {
        return Objects.requireNonNullElse(spinner.getEditor().getText(), "");
    }

    private static void setSpinnerText(Spinner<Integer> spinner, String text) {
        String value = Objects.requireNonNullElse(text, "");
        try {
            spinner.getValueFactory().setValue(Integer.parseInt(value.trim()));
        } catch (RuntimeException ignored) {
            // Raw invalid values remain representable in the editor draft.
        }
        spinner.getEditor().setText(value);
    }

    private static String sensitiveRuleActionLabel(
            SensitiveContentPolicy.RuleAction action
    ) {
        if (action == null) return "";
        return switch (action) {
            case CAPTURE -> "Capture normally";
            case SKIP -> "Skip capture";
        };
    }

    private static Spinner<Integer> retentionSpinner(
            int initial,
            String accessibleText,
            String accessibleHelp
    ) {
        return intSpinner(
                HistoryRetentionPolicy.MIN_MAX_AGE_DAYS,
                HistoryRetentionPolicy.MAX_MAX_AGE_DAYS,
                initial,
                1,
                accessibleText,
                accessibleHelp
        );
    }

    private static Spinner<Integer> typeRetentionSpinner(
            HistoryRetentionPolicy policy,
            io.xseries.xclip.domain.model.ClipContentType type
    ) {
        return intSpinner(
                HistoryRetentionPolicy.TYPE_RULE_DISABLED,
                HistoryRetentionPolicy.MAX_MAX_AGE_DAYS,
                policy.maxAgeDaysFor(type),
                1,
                type.name() + " retention in days",
                "Zero disables the type-specific age rule."
        );
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

    private HBox buildTitleBar() {
        ImageView icon = new ImageView(new Image(
                SettingsWindow.class.getResourceAsStream("/icons/icon.png")
        ));
        icon.setFitWidth(18);
        icon.setFitHeight(18);
        icon.setPreserveRatio(true);

        Label product = new Label("XClip");
        product.getStyleClass().add("settings-title-product");

        Label context = new Label("Settings");
        context.getStyleClass().add("settings-title-context");

        HBox dragRegion = new HBox(9, icon, product, context);
        dragRegion.setAlignment(Pos.CENTER_LEFT);
        dragRegion.getStyleClass().add("settings-title-drag-region");
        HBox.setHgrow(dragRegion, Priority.ALWAYS);

        dragRegion.setOnMousePressed(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY
                    && event.getClickCount() == 1) {
                chromeController.beginDrag(
                        event.getScreenX(),
                        event.getScreenY()
                );
            }
        });
        dragRegion.setOnMouseDragged(event -> chromeController.dragTo(
                event.getScreenX(),
                event.getScreenY()
        ));
        dragRegion.setOnMouseReleased(event -> chromeController.endDrag());
        dragRegion.setOnMouseClicked(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY
                    && event.getClickCount() == 2) {
                chromeController.handleTitleBarDoubleClick();
                syncMaximizeButton();
            }
        });

        Button minimize = windowButton("—", "Minimize Settings");
        minimize.setOnAction(event -> chromeController.minimize());

        maximizeWindowBtn = windowButton("□", "Maximize Settings");
        maximizeWindowBtn.setOnAction(event -> {
            chromeController.toggleMaximized();
            syncMaximizeButton();
        });

        Button close = windowButton("×", "Close Settings");
        close.getStyleClass().add("close");
        close.setOnAction(event -> chromeController.closeToBackground());

        HBox windowControls = new HBox(minimize, maximizeWindowBtn, close);
        windowControls.getStyleClass().add("settings-window-controls");

        HBox titleBar = new HBox(dragRegion, windowControls);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.getStyleClass().add("settings-title-bar");
        return titleBar;
    }

    private Button windowButton(String text, String accessibleText) {
        Button button = new Button(text);
        button.setAccessibleText(accessibleText);
        button.getStyleClass().addAll(
                "settings-window-control",
                "window-control-hit-target"
        );
        return button;
    }

    private void syncMaximizeButton() {
        if (maximizeWindowBtn == null) return;
        boolean maximized = chromeController.isMaximized();
        maximizeWindowBtn.setText(maximized ? "❐" : "□");
        maximizeWindowBtn.setAccessibleText(
                maximized ? "Restore Settings" : "Maximize Settings"
        );
    }

    private VBox buildNavigation() {
        Label title = new Label("SETTINGS");
        title.getStyleClass().add("settings-navigation-eyebrow");

        ToggleGroup group = new ToggleGroup();
        VBox buttons = new VBox(4);
        SettingsPage[] pages = SettingsPage.values();

        for (int index = 0; index < pages.length; index++) {
            SettingsPage page = pages[index];
            ToggleButton button = new ToggleButton(page.title());
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setToggleGroup(group);
            button.setAccessibleText(
                    SettingsAccessibilityText.navigationLabel(
                            page,
                            index,
                            pages.length
                    )
            );
            button.setAccessibleHelp(
                    SettingsAccessibilityText.navigationHelp(page)
            );
            button.getStyleClass().add("settings-nav-button");
            button.setOnAction(event -> selectPage(page));

            int buttonIndex = index;
            button.setOnKeyPressed(event -> {
                switch (event.getCode()) {
                    case UP -> {
                        focusNavigationButton(pages, buttonIndex - 1);
                        event.consume();
                    }
                    case DOWN -> {
                        focusNavigationButton(pages, buttonIndex + 1);
                        event.consume();
                    }
                    case HOME -> {
                        focusNavigationButton(pages, 0);
                        event.consume();
                    }
                    case END -> {
                        focusNavigationButton(pages, pages.length - 1);
                        event.consume();
                    }
                    default -> {
                    }
                }
            });

            navigationButtons.put(page, button);
            buttons.getChildren().add(button);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label localOnly = new Label("LOCAL DATA ONLY");
        localOnly.setWrapText(true);
        localOnly.getStyleClass().add("settings-navigation-footnote");

        VBox sidebar = new VBox(14, title, buttons, spacer, localOnly);
        sidebar.getStyleClass().add("settings-navigation");
        return sidebar;
    }

    private void focusNavigationButton(
            SettingsPage[] pages,
            int requestedIndex
    ) {
        int index = Math.max(0, Math.min(pages.length - 1, requestedIndex));
        ToggleButton button = navigationButtons.get(pages[index]);
        if (button != null) {
            button.requestFocus();
            button.fire();
        }
    }

    private VBox buildPagePane() {
        pageTitleLabel.getStyleClass().add("settings-page-title");
        pageTitleLabel.setAccessibleText("Settings page heading");
        pageDescriptionLabel.setWrapText(true);
        pageDescriptionLabel.setAccessibleText("Settings page description");
        pageDescriptionLabel.getStyleClass().add("settings-page-description");

        VBox pageHeader = new VBox(4, pageTitleLabel, pageDescriptionLabel);
        pageHeader.getStyleClass().add("settings-page-header");

        pageHost.getStyleClass().add("settings-page-host");
        pageHost.setAccessibleText("Settings page content");
        VBox.setVgrow(pageHost, Priority.ALWAYS);

        VBox pane = new VBox(pageHeader, pageHost);
        pane.getStyleClass().add("settings-page-pane");
        HBox.setHgrow(pane, Priority.ALWAYS);
        return pane;
    }

    private void selectPage(SettingsPage page) {
        SettingsPage next = page == null ? SettingsPage.GENERAL : page;
        ScrollPane view = pageViews.get(next);
        if (view == null) {
            throw new IllegalStateException(
                    "Missing Settings page view: " + next
            );
        }

        selectedPage = next;
        pageTitleLabel.setText(next.title());
        pageTitleLabel.setAccessibleText(
                SettingsAccessibilityText.pageHeading(next)
        );
        pageDescriptionLabel.setText(next.description());
        pageHost.setAccessibleText(
                SettingsAccessibilityText.pageContentLabel(next)
        );
        pageHost.getChildren().setAll(view);

        ToggleButton navigation = navigationButtons.get(next);
        if (navigation != null && !navigation.isSelected()) {
            navigation.setSelected(true);
        }
    }

    private void configurePageAccessibility() {
        for (SettingsPage page : SettingsPage.values()) {
            ScrollPane view = pageViews.get(page);
            if (view == null) continue;

            view.setAccessibleText(
                    SettingsAccessibilityText.pageContentLabel(page)
            );
            view.setAccessibleHelp(
                    SettingsAccessibilityText.pageContentHelp(page)
            );
            view.setPannable(true);
        }
    }

    private static SettingsResponsivePolicy.WindowSize initialWindowSize() {
        try {
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            return SettingsResponsivePolicy.initialSize(
                    bounds.getWidth(),
                    bounds.getHeight()
            );
        } catch (Throwable ignored) {
            return SettingsResponsivePolicy.defaultSize();
        }
    }

    private static void applyResponsiveMode(
            Region root,
            double sceneWidth
    ) {
        SettingsResponsivePolicy.LayoutMode mode =
                SettingsResponsivePolicy.modeFor(sceneWidth);
        root.getStyleClass().removeAll(
                SettingsResponsivePolicy.modeStyleClasses()
        );
        root.getStyleClass().add(mode.styleClass());
    }

    private void focusSelectedNavigation() {
        ToggleButton button = navigationButtons.get(selectedPage);
        if (button != null && stage.isShowing()) {
            button.requestFocus();
        }
    }

    private void closeAndDiscard() {
        if (dataOperationRunning.get()) {
            showStatus("Data maintenance is still running");
            return;
        }
        if (draftSession.dirty()) discardDraftSilently();
        stage.hide();
    }


}
