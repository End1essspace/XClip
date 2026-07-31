/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui;

import io.xseries.xclip.ui.popup.ClipRowCell;
import io.xseries.xclip.ui.popup.ClipRowCell.PreviewData;
import io.xseries.xclip.ui.popup.PopupActionBar;
import io.xseries.xclip.ui.popup.PopupActionBar.StatusTone;
import io.xseries.xclip.ui.popup.PopupActionsMenu;
import io.xseries.xclip.ui.popup.PopupFilterBar;
import io.xseries.xclip.ui.popup.QuickHelpPopover;
import io.xseries.xclip.ui.popup.ClipPreviewPolicy;
import io.xseries.xclip.ui.popup.PopupHeader;
import io.xseries.xclip.ui.popup.PopupKeyBindings;
import io.xseries.xclip.ui.popup.PopupPerformancePolicy;
import io.xseries.xclip.ui.popup.PopupRows;
import io.xseries.xclip.ui.popup.ReloadRequestGate;
import io.xseries.xclip.ui.popup.TagEditorModel.EditPlan;
import io.xseries.xclip.ui.popup.BoundedLruCache;
import io.xseries.xclip.ui.popup.PopupTitleBar;
import io.xseries.xclip.ui.popup.PopupRow;
import io.xseries.xclip.ui.popup.PopupRow.ClipRow;
import io.xseries.xclip.ui.popup.PopupRow.SectionRow;
import io.xseries.xclip.ui.popup.PopupViewState;
import io.xseries.xclip.ui.components.SplitActionButton;
import io.xseries.xclip.ui.components.SvgIcon;
import io.xseries.xclip.ui.components.UiIcon;
import io.xseries.xclip.system.window.WindowChromeController;
import io.xseries.xclip.system.window.WindowChromeController.WindowBounds;
import io.xseries.xclip.data.dao.ClipEntryDao;
import io.xseries.xclip.data.dao.TagDao;
import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.data.model.ClipTag;
import io.xseries.xclip.data.model.TagSummary;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipPrimaryAction;
import io.xseries.xclip.domain.model.ClipViewScope;
import io.xseries.xclip.domain.service.ClipContentActionService;
import io.xseries.xclip.domain.service.ClipContentClassifier;
import io.xseries.xclip.domain.service.ClipFilterEngine;
import io.xseries.xclip.domain.service.ClipService;
import io.xseries.xclip.domain.service.PasteService;
import io.xseries.xclip.system.ExternalOpenService;
import io.xseries.xclip.system.clipboard.ClipboardAccess;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.robot.Robot;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class PopupWindow {

    private static final int WIDTH = 520;
    private static final int HEIGHT = 420;
    private static final double WINDOW_EDGE_MARGIN = 12.0;
    private volatile int uiClipLimit = io.xseries.xclip.config.Config.DEFAULT_UI_CLIP_LIMIT;

    private int selectionAnchorIndex = -1;

    private io.xseries.xclip.config.ConfigService configService;
    private io.xseries.xclip.config.Config config;

    private final PauseTransition windowSaveDebounce = new PauseTransition(Duration.millis(650));
    private boolean windowStateAppliedOnce = false;

    private final Label selectedLabel = new Label(); // "Selected: N"
    private Button pasteBtnRef;
    private Button copyBtnRef;
    private Button favBtnRef;
    private Button delBtnRef;
    private Button pauseBtnRef;
    private Button actionsBtnRef;

    private final List<Node> keyboardFocusOrder = new ArrayList<>();
    private final List<Node> keyboardFocusZones = new ArrayList<>();

    // Preview behavior (prevents "text wall" in list)
    private static final int PREVIEW_LINES = 3;
    private static final int PREVIEW_CHAR_LIMIT = 320;

    // Pinned clips remain intentionally compact even when their content is large.
    private static final int PINNED_TITLE_MAX_LENGTH = 120;

    private final Stage stage;
    private final WindowChromeController windowChrome;
    private final TextField searchField = new TextField();
    private final ListView<PopupRow> listView = new ListView<>();
    private final ObservableList<PopupRow> items = FXCollections.observableArrayList();

    private final ToggleGroup scopeFilterGroup = new ToggleGroup();
    private final ToggleButton filterAllBtn = new ToggleButton("All");
    private final ToggleButton filterPinnedBtn = new ToggleButton("Pinned");
    private final ToggleButton filterRecentBtn = new ToggleButton("Recent");
    private final ComboBox<ContentTypeOption> typeFilterCombo = new ComboBox<>();
    private final ComboBox<TagFilterOption> tagFilterCombo = new ComboBox<>();
    private final Button resetFiltersBtn = new Button("Reset filters");

    private volatile PopupViewState viewState = PopupViewState.defaults();
    private boolean filterUiSync = false;

    private final ClipEntryDao dao;
    private final TagDao tagDao;
    private final ClipboardAccess clipboard;
    private final ClipService clipService;
    private final PasteService pasteService;
    private final ExternalOpenService externalOpenService = new ExternalOpenService();

    private final Runnable onOpenSettings;
    private final Runnable onTogglePaused;

    // per-entry expand state for "More/Less"
    private final Map<Long, Boolean> expandedById = new HashMap<>();
    // Strictly bounded caches keep memory stable with 10k/50k histories.
    private final BoundedLruCache<Long, PreviewData> previewCache =
            new BoundedLruCache<>(PopupPerformancePolicy.PREVIEW_CACHE_CAPACITY);

    private record ContentTypeCache(
            PopupPerformancePolicy.ContentFingerprint fingerprint,
            ClipContentType type
    ) {}
    private final BoundedLruCache<Long, ContentTypeCache> contentTypeCache =
            new BoundedLruCache<>(PopupPerformancePolicy.CONTENT_TYPE_CACHE_CAPACITY);

    // v1.1 UX state
    private final Label countLabel = new Label();
    private final Label emptyStateLabel = new Label();
    private volatile boolean paused = false;

    // v1.2: current query (lowercased) for highlighting in cells
    private volatile String currentQueryLower = "";
    private volatile String currentQueryRaw = "";

    // prevent auto-hide while modal dialog is shown (Clear confirmation)
    private volatile boolean suppressAutoHide = false;

    // Footer status zone: keyboard hints by default, transient operation feedback on demand.
    private PopupActionBar actionBar;
    private final PauseTransition statusReset = new PauseTransition(Duration.millis(2200));

    private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "xclip-db");
        t.setDaemon(true);
        return t;
    });

    private final ScheduledExecutorService debounceExec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "xclip-search-debounce");
        t.setDaemon(true);
        return t;
    });

    private volatile ScheduledFuture<?> pendingSearch;
    private final ReloadRequestGate reloadGate = new ReloadRequestGate();

    private final PauseTransition autoHideDelay = new PauseTransition(Duration.millis(160));

    private record ContentTypeOption(ClipContentType type, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record TagFilterOption(Long tagId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private enum PinnedMoveAction {
        UP,
        DOWN,
        TOP,
        BOTTOM
    }

    private record MultiSelectionSnapshot(java.util.Set<Long> ids, long anchorId) {

        static MultiSelectionSnapshot capture(ListView<PopupRow> lv, ObservableList<PopupRow> items, int anchorIndex) {
            java.util.Set<Long> ids = new java.util.HashSet<>();

            for (PopupRow r : lv.getSelectionModel().getSelectedItems()) {
                if (r instanceof ClipRow cr) ids.add(cr.entry().id());
            }

            long anchorId = -1L;
            if (anchorIndex >= 0 && anchorIndex < items.size()) {
                PopupRow ar = items.get(anchorIndex);
                if (ar instanceof ClipRow cr) anchorId = cr.entry().id();
            }

            return new MultiSelectionSnapshot(ids, anchorId);
        }
    }

    // Shared popup controllers (created once).
    private final PopupActionsMenu actionsMenu;
    private final QuickHelpPopover quickHelp;

    public PopupWindow(ClipEntryDao dao, ClipboardAccess clipboard, ClipService clipService) {
        this(dao, clipboard, clipService, () -> {});
    }

    public PopupWindow(
            ClipEntryDao dao,
            ClipboardAccess clipboard,
            ClipService clipService,
            Runnable onOpenSettings
    ) {
        this(
                dao,
                null,
                clipboard,
                clipService,
                onOpenSettings,
                () -> {},
                PasteService.createDefault(clipboard, clipService)
        );
    }

    public PopupWindow(
            ClipEntryDao dao,
            ClipboardAccess clipboard,
            ClipService clipService,
            Runnable onOpenSettings,
            PasteService pasteService
    ) {
        this(dao, null, clipboard, clipService, onOpenSettings, () -> {}, pasteService);
    }

    public PopupWindow(
            ClipEntryDao dao,
            ClipboardAccess clipboard,
            ClipService clipService,
            Runnable onOpenSettings,
            Runnable onTogglePaused,
            PasteService pasteService
    ) {
        this(
                dao,
                null,
                clipboard,
                clipService,
                onOpenSettings,
                onTogglePaused,
                pasteService
        );
    }

    public PopupWindow(
            ClipEntryDao dao,
            TagDao tagDao,
            ClipboardAccess clipboard,
            ClipService clipService,
            Runnable onOpenSettings,
            Runnable onTogglePaused,
            PasteService pasteService
    ) {
        this.dao = java.util.Objects.requireNonNull(dao, "dao");
        this.tagDao = tagDao;
        this.clipboard = java.util.Objects.requireNonNull(clipboard, "clipboard");
        this.clipService = clipService;
        this.pasteService = java.util.Objects.requireNonNull(pasteService);
        this.onOpenSettings = (onOpenSettings != null) ? onOpenSettings : (() -> {});
        this.onTogglePaused = (onTogglePaused != null) ? onTogglePaused : (() -> {});
        this.actionsMenu = createPopupActionsMenu();
        this.quickHelp = new QuickHelpPopover();

        stage = new Stage(StageStyle.UNDECORATED);
        stage.setTitle("XClip");
        stage.getIcons().add(new javafx.scene.image.Image(
                PopupWindow.class.getResourceAsStream("/icons/icon.png")
        ));
        stage.setAlwaysOnTop(true);
        stage.setResizable(true);
        stage.setMinWidth(io.xseries.xclip.config.Config.MIN_WINDOW_W);
        stage.setMinHeight(io.xseries.xclip.config.Config.MIN_WINDOW_H);

        // R2.2 custom chrome: the undecorated stage is controlled entirely
        // through one window-state controller and a JavaFX title bar.
        windowChrome = WindowChromeController.forStage(stage, this::hide);

        listView.setItems(items);
        ClipRowCell.Controller rowCellController = createRowCellController();
        listView.setCellFactory(lv -> new ClipRowCell(rowCellController));
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.getSelectionModel().getSelectedIndices().addListener((javafx.collections.ListChangeListener<? super Integer>) c ->
                Platform.runLater(this::updateSelectionUi)
        );

        listView.getStyleClass().add("clip-list");
        listView.setAccessibleText("Clipboard history");
        listView.setAccessibleHelp(
                "Use Up and Down to navigate clips, Enter to paste, "
                        + "Ctrl+C to copy, and Shift+F10 for actions."
        );

        // Empty state placeholder
        emptyStateLabel.setWrapText(true);
        emptyStateLabel.setMaxWidth(360);
        emptyStateLabel.setPadding(Insets.EMPTY);
        emptyStateLabel.getStyleClass().add("empty-state");
        listView.setPlaceholder(emptyStateLabel);
        updateEmptyStateText();

        // Clip count indicator (counts only real clips, not section rows)
        countLabel.getStyleClass().add("topbar-status");
        countLabel.setAccessibleText("Visible clipboard clip count");
        countLabel.setText("Clips 0");

        selectedLabel.getStyleClass().add("topbar-status");
        selectedLabel.setAccessibleText("Selected clipboard clip count");
        selectedLabel.setVisible(false);
        selectedLabel.setManaged(false);

        searchField.setPromptText("Search clips...");
        searchField.setAccessibleText("Search clipboard history");
        searchField.setAccessibleHelp(
                "Type to filter clips. Press F6 to move between popup regions."
        );
        searchField.setMaxWidth(Double.MAX_VALUE);
        searchField.getStyleClass().add("search-field");

        Button clearSearchBtn = new Button();
        clearSearchBtn.setGraphic(SvgIcon.of(UiIcon.X, 12, "search-clear-icon"));
        clearSearchBtn.setFocusTraversable(true);
        clearSearchBtn.setAccessibleText("Clear search");
        clearSearchBtn.setAccessibleHelp("Remove the current search query.");
        clearSearchBtn.setTooltip(new Tooltip("Clear search"));
        clearSearchBtn.getStyleClass().add("search-clear");
        clearSearchBtn.setVisible(false);
        clearSearchBtn.setManaged(false);

        SvgIcon searchIcon = SvgIcon.of(UiIcon.SEARCH, 16, "search-leading-icon");
        Label searchShortcut = new Label("Ctrl + K");
        searchShortcut.getStyleClass().add("search-shortcut");
        searchShortcut.setMouseTransparent(true);

        clearSearchBtn.setOnAction(e -> {
            searchField.clear();
            searchField.requestFocus();
        });

        searchField.textProperty().addListener((obs, o, n) -> {
            boolean has = n != null && !n.isBlank();
            clearSearchBtn.setVisible(has);
            clearSearchBtn.setManaged(has);
            searchShortcut.setVisible(!has);
            searchShortcut.setManaged(!has);
            debounceReload();
        });

        searchField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                int firstClip = findFirstClipIndex();
                if (firstClip >= 0) {
                    selectAndReveal(firstClip);
                    listView.requestFocus();
                }
                return;
            }

            if (e.getCode() == KeyCode.ESCAPE) {
                e.consume();
                if (!searchField.getText().isBlank()) {
                    searchField.clear();
                    searchField.requestFocus();
                } else {
                    hide();
                }
                return;
            }

        });

        // Help is a real, scroll-safe popover instead of a long tooltip that can be clipped.
        Button help = iconButton(UiIcon.CIRCLE_QUESTION_MARK, "Quick help", "topbar-help");
        help.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && quickHelp.isShowing()) {
                quickHelp.hide();
                event.consume();
            }
        });
        help.setOnAction(event -> quickHelp.toggle(help));

        Button pauseBtn = new Button("Pause");
        pauseBtn.setGraphic(SvgIcon.of(UiIcon.PAUSE, 15, "toolbar-icon", "pause-icon"));
        pauseBtn.setContentDisplay(ContentDisplay.LEFT);
        pauseBtn.setFocusTraversable(true);
        pauseBtn.setAccessibleText("Pause clipboard capture");
        pauseBtn.setAccessibleHelp("Pause or resume background clipboard monitoring.");
        pauseBtn.setOnAction(e -> onTogglePaused.run());
        pauseBtn.getStyleClass().addAll("topbar-btn", "pause-button");
        this.pauseBtnRef = pauseBtn;

        Button settingsBtn = iconButton(UiIcon.SETTINGS, "Open settings", "topbar-settings");
        settingsBtn.setOnAction(e -> openSettings());

        Button clearBtn = new Button("Clear");
        clearBtn.setGraphic(SvgIcon.of(UiIcon.TRASH_2, 15, "toolbar-icon", "clear-icon"));
        clearBtn.setContentDisplay(ContentDisplay.LEFT);
        clearBtn.setFocusTraversable(true);
        clearBtn.setAccessibleText("Clear visible non-pinned clips");
        clearBtn.setAccessibleHelp(
                "Delete visible recent clips while keeping pinned clips."
        );
        clearBtn.setTooltip(new Tooltip("Clear visible non-pinned clips"));
        clearBtn.setOnAction(e -> clearHistoryNonFavorites());
        clearBtn.getStyleClass().addAll("topbar-btn", "clear-history-button");

        StackPane searchWrap = new StackPane(searchField, searchIcon, searchShortcut, clearSearchBtn);
        searchWrap.getStyleClass().add("search-wrap");

        StackPane.setAlignment(searchIcon, Pos.CENTER_LEFT);
        StackPane.setMargin(searchIcon, new Insets(0, 0, 0, 13));
        StackPane.setAlignment(searchShortcut, Pos.CENTER_RIGHT);
        StackPane.setMargin(searchShortcut, new Insets(0, 12, 0, 0));
        StackPane.setAlignment(clearSearchBtn, Pos.CENTER_RIGHT);
        StackPane.setMargin(clearSearchBtn, new Insets(0, 8, 0, 0));

        searchWrap.setMinWidth(0);
        searchWrap.setPrefWidth(820);
        searchWrap.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(searchWrap, Priority.ALWAYS);

        HBox statusGroup = new HBox(10, countLabel, selectedLabel);
        statusGroup.setAlignment(Pos.CENTER_RIGHT);
        statusGroup.getStyleClass().add("popup-status-group");

        HBox controlGroup = new HBox(7, pauseBtn, settingsBtn, help, clearBtn);
        controlGroup.setAlignment(Pos.CENTER_RIGHT);
        controlGroup.getStyleClass().add("popup-control-group");

        configureFilterControls();

        PopupFilterBar filterBar = new PopupFilterBar(
                filterAllBtn,
                filterPinnedBtn,
                filterRecentBtn,
                typeFilterCombo,
                tagFilterCombo,
                resetFiltersBtn
        );
        PopupHeader popupHeader = new PopupHeader(
                searchWrap,
                statusGroup,
                controlGroup,
                filterBar
        );
        PopupTitleBar popupTitleBar = new PopupTitleBar(stage, windowChrome);

        Button pasteBtn = new Button("Paste");
        pasteBtn.setGraphic(SvgIcon.of(UiIcon.CLIPBOARD_PASTE, 15, "action-icon"));
        pasteBtn.setContentDisplay(ContentDisplay.LEFT);
        pasteBtn.setAccessibleText("Paste selected clips");
        pasteBtn.setAccessibleHelp("Paste the current selection into the previously active application.");
        pasteBtn.setOnAction(e -> pasteSelectedOrFirst());
        pasteBtn.getStyleClass().addAll("action-btn", "action-primary");

        Button copyBtn = new Button("Copy");
        copyBtn.setGraphic(SvgIcon.of(UiIcon.COPY, 15, "action-icon"));
        copyBtn.setContentDisplay(ContentDisplay.LEFT);
        copyBtn.setAccessibleText("Copy selected clips");
        copyBtn.setAccessibleHelp("Copy the current selection without sending Ctrl+V.");
        copyBtn.setOnAction(e -> copySelectedOrFirst());
        copyBtn.getStyleClass().addAll("action-btn", "action-neutral");

        Button actionsBtn = new Button();
        Label actionsText = new Label("Actions");
        actionsText.getStyleClass().add("action-button-label");
        HBox actionsGraphic = new HBox(
                6,
                SvgIcon.of(UiIcon.ZAP, 15, "action-icon"),
                actionsText,
                SvgIcon.of(UiIcon.CHEVRON_DOWN, 12, "action-chevron-icon")
        );
        actionsGraphic.setAlignment(Pos.CENTER);
        actionsBtn.setGraphic(actionsGraphic);
        actionsBtn.setOnAction(e -> showActionsMenu(actionsBtn));
        actionsBtn.setAccessibleText("Clip actions");
        actionsBtn.setAccessibleHelp(
                "Open the actions menu. Use Up and Down to navigate and Enter to activate."
        );
        actionsBtn.setTooltip(new Tooltip("Context actions"));
        actionsBtn.getStyleClass().addAll("action-btn", "action-neutral", "actions-menu-button");

        Button favBtn = new Button("Pin / Unpin");
        favBtn.setGraphic(SvgIcon.of(UiIcon.PIN, 15, "action-icon", "favorite-action-icon"));
        favBtn.setContentDisplay(ContentDisplay.LEFT);
        favBtn.setAccessibleText("Pin or unpin selected clips");
        favBtn.setAccessibleHelp("Toggle pinned state for the current selection.");
        favBtn.setOnAction(e -> toggleFavoriteSelected());
        favBtn.getStyleClass().addAll("action-btn", "action-neutral", "action-state");

        Button delBtn = new Button("Delete");
        delBtn.setGraphic(SvgIcon.of(UiIcon.TRASH_2, 15, "action-icon", "danger-action-icon"));
        delBtn.setContentDisplay(ContentDisplay.LEFT);
        delBtn.setAccessibleText("Delete selected clips");
        delBtn.setAccessibleHelp("Delete the current selection. Multiple clips require confirmation.");
        delBtn.setOnAction(e -> deleteSelected());
        delBtn.getStyleClass().addAll("action-btn", "action-danger");

        this.pasteBtnRef = pasteBtn;
        this.copyBtnRef = copyBtn;
        this.favBtnRef = favBtn;
        this.delBtnRef = delBtn;
        this.actionsBtnRef = actionsBtn;

        SplitActionButton pasteControl = new SplitActionButton(
                pasteBtn,
                this::pasteSelectedOrFirst,
                this::copySelectedOrFirst
        );

        PopupActionBar actions = new PopupActionBar(
                pasteControl,
                copyBtn,
                actionsBtn,
                favBtn,
                delBtn
        );
        this.actionBar = actions;

        BorderPane root = new BorderPane();
        root.getStyleClass().add("popup-root");

        VBox shellHeader = new VBox(popupTitleBar, popupHeader);
        shellHeader.getStyleClass().add("popup-shell-header");
        root.setTop(shellHeader);

        root.setCenter(listView);
        root.setBottom(actions);

        Scene scene = new Scene(root, WIDTH, HEIGHT);

        // A primary click anywhere in the main scene must dismiss row actions immediately.
        // ContextMenu normally auto-hides, but explicit dismissal remains reliable when
        // ListCell mouse handlers consume the pointer event for custom selection.
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && actionsMenu.isShowing()) {
                actionsMenu.hide();
            }
        });
        scene.addEventFilter(ScrollEvent.SCROLL, event -> actionsMenu.hide());

        UiStyles.applyPopup(scene);

        windowChrome.installResizeSupport(
                scene,
                6.0,
                stage.getMinWidth(),
                stage.getMinHeight()
        );

        stage.setScene(scene);
        configureKeyboardUx(
                scene,
                clearSearchBtn,
                pauseBtn,
                settingsBtn,
                help,
                clearBtn,
                pasteControl,
                copyBtn,
                actionsBtn,
                favBtn,
                delBtn,
                popupTitleBar
        );

        stage.setOnCloseRequest(e -> {
            e.consume();
            windowChrome.closeToBackground();
        });

        // Auto-hide with suppression
        autoHideDelay.setOnFinished(e -> {
            if (!suppressAutoHide) hide();
        });
        stage.focusedProperty().addListener((o, was, now) -> {
            if (now) {
                autoHideDelay.stop();
                Platform.runLater(this::recoverWindowForCurrentTopology);
                return;
            }

            // Minimize is a real window operation, not an auto-hide request.
            // Check on the next pulse because iconified can update after focus.
            Platform.runLater(() -> {
                if (suppressAutoHide || windowChrome.isIconified()) return;
                autoHideDelay.playFromStart();
            });
        });

        // One audited popup-level keyboard contract. Text editing shortcuts
        // remain native while the search field or another TextInputControl owns focus.
        stage.addEventFilter(KeyEvent.KEY_PRESSED, this::handlePopupKeyPressed);

        statusReset.setOnFinished(e -> {
            if (actionBar != null) actionBar.showHints();
        });

        reloadNow("");
    }

    private void configureKeyboardUx(
            Scene scene,
            Button clearSearchBtn,
            Button pauseBtn,
            Button settingsBtn,
            Button helpBtn,
            Button clearBtn,
            SplitActionButton pasteControl,
            Button copyBtn,
            Button actionsBtn,
            Button favoriteBtn,
            Button deleteBtn,
            PopupTitleBar titleBar
    ) {
        keyboardFocusOrder.clear();
        keyboardFocusOrder.add(searchField);
        keyboardFocusOrder.add(clearSearchBtn);
        keyboardFocusOrder.add(filterAllBtn);
        keyboardFocusOrder.add(filterPinnedBtn);
        keyboardFocusOrder.add(filterRecentBtn);
        keyboardFocusOrder.add(typeFilterCombo);
        keyboardFocusOrder.add(tagFilterCombo);
        keyboardFocusOrder.add(resetFiltersBtn);
        keyboardFocusOrder.add(listView);
        keyboardFocusOrder.add(pasteControl.mainButton());
        keyboardFocusOrder.add(pasteControl.menuButton());
        keyboardFocusOrder.add(copyBtn);
        keyboardFocusOrder.add(actionsBtn);
        keyboardFocusOrder.add(favoriteBtn);
        keyboardFocusOrder.add(deleteBtn);
        keyboardFocusOrder.add(pauseBtn);
        keyboardFocusOrder.add(settingsBtn);
        keyboardFocusOrder.add(helpBtn);
        keyboardFocusOrder.add(clearBtn);
        keyboardFocusOrder.addAll(titleBar.focusableControls());

        keyboardFocusZones.clear();
        keyboardFocusZones.add(searchField);
        keyboardFocusZones.add(filterAllBtn);
        keyboardFocusZones.add(listView);
        keyboardFocusZones.add(pasteControl.mainButton());
        keyboardFocusZones.add(pauseBtn);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.TAB
                    || event.isControlDown()
                    || event.isAltDown()
                    || event.isMetaDown()
                    || actionsMenu.isShowing()
                    || quickHelp.isShowing()) {
                return;
            }

            event.consume();
            moveFocusInOrder(scene, keyboardFocusOrder, event.isShiftDown());
        });

        configureListKeyboardNavigation();
    }

    private void handlePopupKeyPressed(KeyEvent event) {
        boolean textInputFocused = event.getTarget() instanceof TextInputControl;
        PopupKeyBindings.Action action = PopupKeyBindings.resolve(
                event.getCode().name(),
                event.isControlDown(),
                event.isShiftDown(),
                event.isAltDown(),
                event.isMetaDown(),
                textInputFocused
        );
        if (action == PopupKeyBindings.Action.NONE) return;

        boolean nativeActionControlFocused =
                event.getTarget() instanceof ButtonBase
                        || event.getTarget() instanceof ComboBoxBase<?>;
        if (nativeActionControlFocused
                && (action == PopupKeyBindings.Action.PASTE
                || action == PopupKeyBindings.Action.MOVE_PINNED_UP
                || action == PopupKeyBindings.Action.MOVE_PINNED_DOWN)) {
            return;
        }

        event.consume();
        switch (action) {
            case SELECT_RECENT -> {
                selectSectionClips("RECENT");
                listView.requestFocus();
            }
            case SELECT_ALL -> {
                selectAllClips();
                listView.requestFocus();
            }
            case CLEAR_SELECTION -> clearSelection();
            case INVERT_SELECTION -> {
                invertSelection();
                listView.requestFocus();
            }
            case FOCUS_SEARCH -> {
                searchField.requestFocus();
                searchField.selectAll();
            }
            case CLEAR_SEARCH -> {
                searchField.clear();
                searchField.requestFocus();
            }
            case COPY -> copySelectedOrFirst();
            case TOGGLE_PIN -> toggleFavoriteSelected();
            case OPEN_SETTINGS -> openSettings();
            case MOVE_PINNED_UP -> moveSelectedPinned(PinnedMoveAction.UP);
            case MOVE_PINNED_DOWN -> moveSelectedPinned(PinnedMoveAction.DOWN);
            case RENAME_PINNED -> renameSelectedPinned();
            case TOGGLE_PREVIEW -> toggleExpandSelected();
            case ESCAPE -> handleEscapeKey();
            case PASTE -> pasteSelectedOrFirst();
            case DELETE -> deleteSelected();
            case OPEN_ACTIONS -> showActionsMenu(actionsBtnRef);
            case FOCUS_NEXT_ZONE ->
                    moveFocusInOrder(stage.getScene(), keyboardFocusZones, false);
            case FOCUS_PREVIOUS_ZONE ->
                    moveFocusInOrder(stage.getScene(), keyboardFocusZones, true);
            case NONE -> {
                // handled above
            }
        }
    }

    private void handleEscapeKey() {
        if (actionsMenu.isShowing()) {
            actionsMenu.hide();
        } else if (quickHelp.isShowing()) {
            quickHelp.hide();
        } else if (collapseExpandedPreviews()) {
            listView.requestFocus();
        } else if (!listView.getSelectionModel().getSelectedIndices().isEmpty()) {
            clearSelection();
        } else if (!searchField.getText().isBlank()) {
            searchField.clear();
            searchField.requestFocus();
        } else {
            hide();
        }
    }

    private void moveFocusInOrder(
            Scene scene,
            List<Node> order,
            boolean backwards
    ) {
        if (scene == null || order == null || order.isEmpty()) return;

        Node current = scene.getFocusOwner();
        int currentIndex = indexOfFocusOwner(order, current);
        int step = backwards ? -1 : 1;
        int size = order.size();

        for (int offset = 1; offset <= size; offset++) {
            int base = currentIndex < 0
                    ? (backwards ? 0 : -1)
                    : currentIndex;
            int index = Math.floorMod(base + (step * offset), size);
            Node candidate = order.get(index);
            if (!isKeyboardFocusable(candidate)) continue;

            if (candidate == listView
                    && listView.getSelectionModel().getSelectedIndices().isEmpty()) {
                int firstClip = findFirstClipIndex();
                if (firstClip >= 0) selectCellExclusively(firstClip);
            }

            candidate.requestFocus();
            return;
        }
    }

    private int indexOfFocusOwner(List<Node> order, Node focusOwner) {
        if (focusOwner == null) return -1;

        for (int index = 0; index < order.size(); index++) {
            Node candidate = order.get(index);
            if (focusOwner == candidate || isDescendantOf(focusOwner, candidate)) {
                return index;
            }
        }
        return -1;
    }

    private boolean isKeyboardFocusable(Node node) {
        return node != null
                && node.isVisible()
                && node.isManaged()
                && !node.isDisabled()
                && node.isFocusTraversable();
    }

    private boolean isDescendantOf(Node node, Node ancestor) {
        Node current = node;
        while (current != null) {
            if (current == ancestor) return true;
            current = current.getParent();
        }
        return false;
    }

    private void configureListKeyboardNavigation() {
        listView.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() || event.isAltDown() || event.isMetaDown()) return;

            KeyCode code = event.getCode();
            if (code != KeyCode.UP
                    && code != KeyCode.DOWN
                    && code != KeyCode.HOME
                    && code != KeyCode.END
                    && code != KeyCode.PAGE_UP
                    && code != KeyCode.PAGE_DOWN) {
                return;
            }

            int target = keyboardNavigationTarget(code);
            if (target < 0) return;

            event.consume();
            applyKeyboardSelection(target, event.isShiftDown());
        });
    }

    private int keyboardNavigationTarget(KeyCode code) {
        int current = listView.getSelectionModel().getSelectedIndex();
        if (current < 0) {
            return switch (code) {
                case UP, END, PAGE_UP ->
                        findClipIndexFrom(items.size() - 1, -1, true);
                case DOWN, HOME, PAGE_DOWN ->
                        findClipIndexFrom(0, 1, true);
                default -> -1;
            };
        }

        return switch (code) {
            case HOME -> findClipIndexFrom(0, 1, true);
            case END -> findClipIndexFrom(items.size() - 1, -1, true);
            case UP -> findClipIndexFrom(current - 1, -1, true);
            case DOWN -> findClipIndexFrom(current + 1, 1, true);
            case PAGE_UP -> moveByClipSteps(current, -1, 8);
            case PAGE_DOWN -> moveByClipSteps(current, 1, 8);
            default -> -1;
        };
    }

    private int moveByClipSteps(int current, int direction, int steps) {
        int index = current;
        if (index < 0) {
            return direction > 0
                    ? findClipIndexFrom(0, 1, true)
                    : findClipIndexFrom(items.size() - 1, -1, true);
        }

        int last = index;
        for (int count = 0; count < Math.max(1, steps); count++) {
            int next = findClipIndexFrom(last + direction, direction, true);
            if (next < 0) break;
            last = next;
        }
        return last == index ? -1 : last;
    }

    private int findClipIndexFrom(int start, int direction, boolean includeStart) {
        if (items.isEmpty()) return -1;

        int step = direction < 0 ? -1 : 1;
        int index = includeStart ? start : start + step;
        while (index >= 0 && index < items.size()) {
            if (items.get(index) instanceof ClipRow) return index;
            index += step;
        }
        return -1;
    }

    private void applyKeyboardSelection(int targetIndex, boolean extendRange) {
        if (targetIndex < 0 || targetIndex >= items.size()) return;
        if (!(items.get(targetIndex) instanceof ClipRow)) return;

        boolean collapsedPreview = collapseExpandedExcept(targetIndex);
        MultipleSelectionModel<PopupRow> selection = listView.getSelectionModel();

        if (!extendRange) {
            selectionAnchorIndex = targetIndex;
            selection.clearAndSelect(targetIndex);
        } else {
            int anchor = selectionAnchorIndex;
            if (anchor < 0
                    || anchor >= items.size()
                    || !(items.get(anchor) instanceof ClipRow)) {
                int current = selection.getSelectedIndex();
                anchor = current >= 0 && current < items.size()
                        && items.get(current) instanceof ClipRow
                        ? current
                        : targetIndex;
                selectionAnchorIndex = anchor;
            }

            selection.clearSelection();
            int first = Math.min(anchor, targetIndex);
            int last = Math.max(anchor, targetIndex);
            for (int index = first; index <= last; index++) {
                if (items.get(index) instanceof ClipRow) selection.select(index);
            }
        }

        listView.getFocusModel().focus(targetIndex);
        listView.scrollTo(targetIndex);
        listView.requestFocus();
        if (collapsedPreview) listView.refresh();
    }

    private PopupActionsMenu createPopupActionsMenu() {
        return new PopupActionsMenu(new PopupActionsMenu.Actions() {
            @Override
            public void paste() {
                pasteSelectedOrFirst();
            }

            @Override
            public void copy() {
                copySelectedOrFirst();
            }

            @Override
            public void performPrimaryTypeAction() {
                performPrimaryTypeActionForSelection();
            }

            @Override
            public void editTags() {
                editTagsSelected();
            }

            @Override
            public void manageTags() {
                manageTagsLibrary();
            }

            @Override
            public boolean tagsAvailable() {
                return tagDao != null;
            }

            @Override
            public void toggleFavorite() {
                toggleFavoriteSelected();
            }

            @Override
            public void renamePinned() {
                renameSelectedPinned();
            }

            @Override
            public void clearTitle() {
                clearSelectedTitle();
            }

            @Override
            public void movePinnedUp() {
                moveSelectedPinned(PinnedMoveAction.UP);
            }

            @Override
            public void movePinnedDown() {
                moveSelectedPinned(PinnedMoveAction.DOWN);
            }

            @Override
            public void movePinnedToTop() {
                moveSelectedPinned(PinnedMoveAction.TOP);
            }

            @Override
            public void movePinnedToBottom() {
                moveSelectedPinned(PinnedMoveAction.BOTTOM);
            }

            @Override
            public void delete() {
                deleteSelected();
            }

            @Override
            public ClipPrimaryAction primaryActionFor(ClipEntry entry) {
                return PopupWindow.this.primaryActionFor(entry);
            }
        });
    }

    private ClipRowCell.Controller createRowCellController() {
        return new ClipRowCell.Controller() {
            @Override
            public void requestListFocus() {
                listView.requestFocus();
            }

            @Override
            public void selectFromPrimaryPointer(
                    int index,
                    boolean shiftDown,
                    boolean controlDown
            ) {
                selectFromCellPointer(index, shiftDown, controlDown);
            }

            @Override
            public void selectExclusively(int index) {
                selectCellExclusively(index);
            }

            @Override
            public void pasteEntry(ClipEntry entry) {
                PopupWindow.this.pasteEntry(entry);
            }

            @Override
            public void performPrimaryTypeAction(ClipEntry entry) {
                PopupWindow.this.performPrimaryTypeAction(entry);
            }

            @Override
            public void showContextMenu(
                    Node owner,
                    int index,
                    double screenX,
                    double screenY
            ) {
                showContextMenuForCell(owner, index, screenX, screenY);
            }

            @Override
            public void hideContextMenu() {
                actionsMenu.hide();
            }

            @Override
            public ClipContentType contentTypeFor(ClipEntry entry) {
                return PopupWindow.this.contentTypeFor(entry);
            }

            @Override
            public String currentQueryLower() {
                return PopupWindow.this.currentQueryLower;
            }

            @Override
            public boolean isExpanded(long id) {
                return expandedById.getOrDefault(id, false);
            }

            @Override
            public void setExpanded(long id, boolean expanded) {
                // XClip uses an accordion-style preview: one expanded clip at a time.
                // This prevents multiple large rows from consuming the entire list.
                expandedById.clear();
                if (expanded) expandedById.put(id, true);
            }

            @Override
            public PreviewData previewData(long id, String fullContent) {
                return getPreviewData(id, fullContent);
            }

            @Override
            public String expandedPreview(String fullContent) {
                return ClipPreviewPolicy.expandedPreview(fullContent);
            }

            @Override
            public void refreshList() {
                listView.refresh();
            }
        };
    }

    private void selectFromCellPointer(
            int index,
            boolean shiftDown,
            boolean controlDown
    ) {
        if (index < 0 || index >= items.size()) return;

        boolean collapsedPreview = collapseExpandedExcept(index);
        MultipleSelectionModel<PopupRow> selection = listView.getSelectionModel();

        if (shiftDown) {
            if (selectionAnchorIndex < 0 || selectionAnchorIndex >= items.size()) {
                selectionAnchorIndex = index;
            }

            int first = Math.min(selectionAnchorIndex, index);
            int last = Math.max(selectionAnchorIndex, index);

            selection.clearSelection();
            for (int i = first; i <= last; i++) {
                if (items.get(i) instanceof ClipRow) {
                    selection.select(i);
                }
            }
            if (collapsedPreview) listView.refresh();
            return;
        }

        if (controlDown) {
            if (selection.isSelected(index)) {
                selection.clearSelection(index);
            } else {
                selection.select(index);
            }

            selectionAnchorIndex = index;
            if (collapsedPreview) listView.refresh();
            return;
        }

        selectionAnchorIndex = index;
        selection.clearAndSelect(index);
        if (collapsedPreview) listView.refresh();
    }

    private void selectCellExclusively(int index) {
        if (index < 0 || index >= items.size()) return;

        boolean collapsedPreview = collapseExpandedExcept(index);
        selectionAnchorIndex = index;
        listView.getSelectionModel().clearAndSelect(index);
        if (collapsedPreview) listView.refresh();
    }

    private boolean collapseExpandedExcept(int index) {
        if (expandedById.isEmpty() || index < 0 || index >= items.size()) return false;

        PopupRow row = items.get(index);
        if (!(row instanceof ClipRow clipRow)) return false;

        long keepId = clipRow.entry().id();
        boolean changed = expandedById.keySet().removeIf(id -> id != keepId);
        return changed;
    }

    private void showContextMenuForCell(
            Node owner,
            int index,
            double screenX,
            double screenY
    ) {
        if (owner == null || index < 0 || index >= items.size()) {
            actionsMenu.hide();
            return;
        }

        PopupRow row = items.get(index);
        if (!(row instanceof ClipRow)) {
            actionsMenu.hide();
            return;
        }

        if (!listView.getSelectionModel().isSelected(index)) {
            selectCellExclusively(index);
        }

        quickHelp.hide();
        actionsMenu.show(
                owner,
                screenX,
                screenY,
                getSelectedClipsOrdered()
        );
    }

    private Button iconButton(UiIcon icon, String accessibleText, String extraStyleClass) {
        Button button = new Button();
        button.setGraphic(SvgIcon.of(icon, 15, "toolbar-icon"));
        button.setFocusTraversable(true);
        button.setAccessibleText(accessibleText);
        button.setAccessibleHelp(accessibleText + ".");
        button.setTooltip(new Tooltip(accessibleText));
        button.getStyleClass().addAll("topbar-btn", "topbar-icon-button");
        if (extraStyleClass != null && !extraStyleClass.isBlank()) {
            button.getStyleClass().add(extraStyleClass);
        }
        return button;
    }

    private void showActionsMenu(Node owner) {
        if (owner == null) return;

        List<ClipEntry> selected = getSelectedClipsOrdered();
        if (selected.isEmpty()) {
            int first = findFirstClipIndex();
            if (first >= 0) {
                selectCellExclusively(first);
                selected = getSelectedClipsOrdered();
            }
        }
        actionsMenu.showAbove(owner, selected);
    }

    private void configureFilterControls() {
        configureScopeToggle(filterAllBtn, ClipViewScope.ALL);
        configureScopeToggle(filterPinnedBtn, ClipViewScope.PINNED);
        configureScopeToggle(filterRecentBtn, ClipViewScope.RECENT);

        scopeFilterGroup.selectToggle(filterAllBtn);
        scopeFilterGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (filterUiSync) return;

            if (newToggle == null) {
                filterUiSync = true;
                if (oldToggle != null) oldToggle.setSelected(true);
                filterUiSync = false;
                return;
            }

            Object value = newToggle.getUserData();
            ClipViewScope scope = value instanceof ClipViewScope s ? s : ClipViewScope.ALL;
            setFilterState(scope, viewState.contentType(), viewState.tagId(), true);
        });

        ObservableList<ContentTypeOption> typeOptions = FXCollections.observableArrayList();
        typeOptions.add(new ContentTypeOption(null, "All types"));
        for (ClipContentType type : ClipContentType.values()) {
            typeOptions.add(new ContentTypeOption(type, type.label()));
        }

        typeFilterCombo.setItems(typeOptions);
        typeFilterCombo.getSelectionModel().selectFirst();
        typeFilterCombo.setFocusTraversable(true);
        typeFilterCombo.setAccessibleText("Clipboard content type filter");
        typeFilterCombo.setAccessibleHelp("Choose a content type or show all types.");
        typeFilterCombo.setPrefWidth(190);
        typeFilterCombo.setMinWidth(160);
        typeFilterCombo.getStyleClass().add("filter-type-combo");
        typeFilterCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (filterUiSync) return;
            ClipContentType type = newValue == null ? null : newValue.type();
            setFilterState(viewState.scope(), type, viewState.tagId(), true);
        });

        tagFilterCombo.setItems(FXCollections.observableArrayList(
                new TagFilterOption(null, "Tag: All tags")
        ));
        tagFilterCombo.getSelectionModel().selectFirst();
        tagFilterCombo.setFocusTraversable(true);
        tagFilterCombo.setAccessibleText("Clipboard tag filter");
        tagFilterCombo.setAccessibleHelp("Choose a tag or show clips with any tag.");
        tagFilterCombo.setPrefWidth(180);
        tagFilterCombo.setMinWidth(150);
        tagFilterCombo.getStyleClass().add("filter-tag-combo");
        tagFilterCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (filterUiSync) return;
            Long tagId = newValue == null ? null : newValue.tagId();
            setFilterState(viewState.scope(), viewState.contentType(), tagId, true);
        });

        resetFiltersBtn.setGraphic(SvgIcon.of(UiIcon.ROTATE_CCW, 13, "filter-icon", "filter-reset-icon"));
        resetFiltersBtn.setContentDisplay(ContentDisplay.LEFT);
        resetFiltersBtn.setFocusTraversable(true);
        resetFiltersBtn.setAccessibleText("Reset clipboard filters");
        resetFiltersBtn.setAccessibleHelp("Return to All clips, All types, and All tags.");
        resetFiltersBtn.getStyleClass().add("filter-reset");
        resetFiltersBtn.setOnAction(e -> setFilterState(ClipViewScope.ALL, null, null, true));

        updateFilterControlState();
    }

    private void configureScopeToggle(ToggleButton button, ClipViewScope scope) {
        button.setToggleGroup(scopeFilterGroup);
        button.setUserData(scope);
        button.setFocusTraversable(true);
        button.setAccessibleText("Show " + scope.label().toLowerCase(Locale.ROOT) + " clips");
        button.setAccessibleHelp("Change the clipboard history scope to " + scope.label() + ".");
        button.setContentDisplay(ContentDisplay.LEFT);
        UiIcon icon = switch (scope) {
            case ALL -> UiIcon.LIST;
            case PINNED -> UiIcon.PIN;
            case RECENT -> UiIcon.ROTATE_CCW_CLOCK;
        };
        button.setGraphic(SvgIcon.of(icon, 13, "filter-icon"));
        button.getStyleClass().add("filter-toggle");
    }

    private void setFilterState(
            ClipViewScope scope,
            ClipContentType contentType,
            Long tagId,
            boolean reload
    ) {
        ClipViewScope effectiveScope = scope == null ? ClipViewScope.ALL : scope;
        PopupViewState nextState = new PopupViewState(effectiveScope, contentType, tagId);
        boolean changed = !viewState.equals(nextState);

        viewState = nextState;

        filterUiSync = true;
        Toggle target = switch (effectiveScope) {
            case ALL -> filterAllBtn;
            case PINNED -> filterPinnedBtn;
            case RECENT -> filterRecentBtn;
        };
        scopeFilterGroup.selectToggle(target);

        ContentTypeOption targetType = typeFilterCombo.getItems().stream()
                .filter(option -> option.type() == contentType)
                .findFirst()
                .orElseGet(() -> typeFilterCombo.getItems().isEmpty()
                        ? null
                        : typeFilterCombo.getItems().get(0));
        typeFilterCombo.getSelectionModel().select(targetType);

        TagFilterOption targetTag = tagFilterCombo.getItems().stream()
                .filter(option -> java.util.Objects.equals(option.tagId(), tagId))
                .findFirst()
                .orElseGet(() -> tagFilterCombo.getItems().isEmpty()
                        ? null
                        : tagFilterCombo.getItems().get(0));
        tagFilterCombo.getSelectionModel().select(targetTag);
        filterUiSync = false;

        updateFilterControlState();
        updateEmptyStateText();

        if (reload && changed) {
            reloadNow(searchField.getText());
        }
    }

    private void syncTagFilterOptions(List<ClipTag> tags) {
        Long selectedTagId = viewState.tagId();

        ObservableList<TagFilterOption> options = FXCollections.observableArrayList();
        options.add(new TagFilterOption(null, "Tag: All tags"));
        if (tags != null) {
            for (ClipTag tag : tags) {
                if (tag != null) {
                    options.add(new TagFilterOption(tag.id(), "Tag: " + tag.name()));
                }
            }
        }

        filterUiSync = true;
        if (!tagFilterCombo.getItems().equals(options)) {
            tagFilterCombo.setItems(options);
        }
        TagFilterOption target = tagFilterCombo.getItems().stream()
                .filter(option -> java.util.Objects.equals(option.tagId(), selectedTagId))
                .findFirst()
                .orElse(tagFilterCombo.getItems().get(0));
        if (!java.util.Objects.equals(tagFilterCombo.getValue(), target)) {
            tagFilterCombo.getSelectionModel().select(target);
        }
        filterUiSync = false;

        if (selectedTagId != null && target.tagId() == null) {
            viewState = new PopupViewState(
                    viewState.scope(),
                    viewState.contentType(),
                    null
            );
        }
    }

    private void updateFilterControlState() {
        boolean active = viewState.filtersActive();
        resetFiltersBtn.setVisible(active);
        resetFiltersBtn.setManaged(active);
    }

    public void enableWindowPersistence(io.xseries.xclip.config.ConfigService configService,
                                        io.xseries.xclip.config.Config config) {
        this.configService = configService;
        this.config = (config != null) ? config : io.xseries.xclip.config.Config.defaults();
        this.uiClipLimit = this.config.uiClipLimit();

        WindowBounds configuredNormalBounds = new WindowBounds(
                this.config.windowX(),
                this.config.windowY(),
                this.config.windowW(),
                this.config.windowH()
        );
        windowChrome.rememberNormalBounds(configuredNormalBounds);

        // Initial restored size can be applied before the first show.
        stage.setWidth(this.config.windowW());
        stage.setHeight(this.config.windowH());

        windowSaveDebounce.setOnFinished(e -> persistWindowStateNow());

        stage.xProperty().addListener((o, ov, nv) -> onWindowBoundsChanged());
        stage.yProperty().addListener((o, ov, nv) -> onWindowBoundsChanged());
        stage.widthProperty().addListener((o, ov, nv) -> onWindowBoundsChanged());
        stage.heightProperty().addListener((o, ov, nv) -> onWindowBoundsChanged());
        stage.maximizedProperty().addListener((o, wasMaximized, isMaximized) -> {
            if (!isMaximized) {
                // Native restore updates geometry asynchronously. Capture after
                // JavaFX has applied the restored rectangle.
                Platform.runLater(() -> {
                    windowChrome.captureNormalBounds();
                    scheduleWindowPersist();
                });
            } else {
                scheduleWindowPersist();
            }
        });
    }

    public void applyConfig(io.xseries.xclip.config.Config config) {
        if (config == null) return;
        this.config = config.normalized();
        this.uiClipLimit = this.config.uiClipLimit();

        Platform.runLater(() -> {
            if (stage.isShowing()) {
                reloadNow(searchField.getText());
            }
        });
    }

    private void onWindowBoundsChanged() {
        // Native maximize can emit geometry changes before the maximized flag.
        // Capture on the next pulse so full-screen bounds are not mistaken for
        // the restored rectangle.
        Platform.runLater(() -> {
            windowChrome.captureNormalBounds();
            scheduleWindowPersist();
        });
    }

    private void scheduleWindowPersist() {
        if (configService == null || config == null) return;
        if (!stage.isShowing() || windowChrome.isIconified()) return;
        windowSaveDebounce.playFromStart();
    }

    private void persistWindowStateNow() {
        if (configService == null || config == null) return;

        // Do not persist transient minimized or hidden native geometry.
        if (!stage.isShowing() || windowChrome.isIconified()) return;

        boolean maximized = windowChrome.isMaximized();
        java.util.Optional<WindowBounds> persistedBounds =
                windowChrome.persistenceBounds();
        if (persistedBounds.isEmpty()) return;

        WindowBounds bounds = persistedBounds.get();
        double x = bounds.x();
        double y = bounds.y();
        double w = bounds.width();
        double h = bounds.height();

        if (config.windowX() == x
                && config.windowY() == y
                && config.windowW() == w
                && config.windowH() == h
                && config.windowMaximized() == maximized) {
            return;
        }

        io.xseries.xclip.config.Config updated =
                config.withWindowState(x, y, w, h, maximized);
        this.config = updated;

        // Window geometry must not reapply unrelated runtime settings.
        configService.persist(updated);
    }

    private void applyWindowStateOrFallback() {
        WindowBounds applied;

        if (configService == null || config == null) {
            applied = positionNearMouse();
            windowChrome.rememberNormalBounds(applied);
            return;
        }

        WindowBounds requested = new WindowBounds(
                config.windowX(),
                config.windowY(),
                config.windowW(),
                config.windowH()
        );

        java.util.Optional<WindowBounds> recovered = config.hasWindowPos()
                ? WindowChromeController.recoverToVisibleScreens(
                        requested,
                        currentVisualScreens()
                )
                : java.util.Optional.empty();

        if (recovered.isPresent()) {
            applied = recovered.get();
            windowChrome.applyRestoredBounds(applied);
        } else {
            stage.setWidth(config.windowW());
            stage.setHeight(config.windowH());
            applied = positionNearMouse();
        }

        windowChrome.rememberNormalBounds(applied);
        if (!applied.equals(requested)) {
            scheduleWindowPersist();
        }
    }

    /**
     * Revalidates the last restored rectangle against the current display
     * topology. This is intentionally safe to call on every show/focus event:
     * visible negative-coordinate windows are preserved unchanged.
     */
    private void recoverWindowForCurrentTopology() {
        if (!stage.isShowing() || windowChrome.isIconified()) return;

        WindowBounds requested = windowChrome.normalBounds()
                .orElseGet(() -> windowChrome.persistenceBounds()
                        .orElse(windowChrome.currentBounds()));

        java.util.Optional<WindowBounds> recovered =
                WindowChromeController.recoverToVisibleScreens(
                        requested,
                        currentVisualScreens()
                );
        if (recovered.isEmpty()) return;

        WindowBounds safe = recovered.get();
        windowChrome.rememberNormalBounds(safe);

        if (!windowChrome.isMaximized()
                && !safe.equals(windowChrome.currentBounds())) {
            windowChrome.applyRestoredBounds(safe);
        }

        if (!safe.equals(requested)) {
            scheduleWindowPersist();
        }
    }

    private List<WindowBounds> currentVisualScreens() {
        return Screen.getScreens().stream()
                .map(Screen::getVisualBounds)
                .map(PopupWindow::toWindowBounds)
                .toList();
    }

    private static WindowBounds toWindowBounds(Rectangle2D bounds) {
        return new WindowBounds(
                bounds.getMinX(),
                bounds.getMinY(),
                bounds.getWidth(),
                bounds.getHeight()
        );
    }

    // v1.1: called from TrayController to reflect paused state in UI
    public void setPaused(boolean paused) {
        boolean changed = this.paused != paused;
        this.paused = paused;
        Platform.runLater(() -> {
            if (pauseBtnRef != null) {
                pauseBtnRef.setText(paused ? "Resume" : "Pause");
                pauseBtnRef.setAccessibleText(
                        paused ? "Resume clipboard capture" : "Pause clipboard capture"
                );
                pauseBtnRef.setGraphic(SvgIcon.of(
                        paused ? UiIcon.PLAY : UiIcon.PAUSE,
                        17,
                        "toolbar-icon",
                        paused ? "resume-icon" : "pause-icon"
                ));
                pauseBtnRef.pseudoClassStateChanged(
                        javafx.css.PseudoClass.getPseudoClass("paused"),
                        paused
                );
            }
            updateEmptyStateText();
            if (changed && stage.isShowing()) {
                showToast(
                        paused ? "Capturing paused" : "Capturing resumed",
                        paused ? StatusTone.WARNING : StatusTone.SUCCESS
                );
            }
        });
    }

    private void updateEmptyStateText() {
        if (paused) {
            emptyStateLabel.setText("Paused. Clipboard capture is turned off.\nResume capturing from the tray menu.");
            return;
        }

        String q = currentQueryRaw == null ? "" : currentQueryRaw.trim();
        boolean filtersActive = viewState.filtersActive();

        if (!q.isEmpty()) {
            emptyStateLabel.setText(filtersActive
                    ? "No results for \"" + q + "\" with the current filters.\nReset filters or press Ctrl+L to clear search."
                    : "No results for \"" + q + "\".\nPress Ctrl+L to clear search.");
            return;
        }

        if (filtersActive) {
            emptyStateLabel.setText("No clips match the current filters.\nReset filters to show all clips.");
            return;
        }

        emptyStateLabel.setText("No clips yet.\nCopy any text and it will appear here.");
    }

    public void showOrFocus() {
        pasteService.clearTarget();
        showOrFocusInternal();
    }

    /**
     * Used only by the global hotkey path. Captures the currently active
     * external application before XClip takes focus.
     */
    public void showOrFocusForPaste() {
        pasteService.prepareTargetForPaste();
        showOrFocusInternal();
    }

    private void showOrFocusInternal() {
        suppressAutoHide = false;
        autoHideDelay.stop();

        boolean first = !windowStateAppliedOnce;
        windowStateAppliedOnce = true;

        if (!stage.isShowing()) {
            stage.show();
        }

        windowChrome.restoreFromMinimized();

        if (first) {
            applyWindowStateOrFallback();

            if (config != null && config.windowMaximized()) {
                Platform.runLater(windowChrome::maximize);
            }
        } else {
            recoverWindowForCurrentTopology();
        }

        stage.toFront();
        stage.requestFocus();

        searchField.requestFocus();
        reloadNow(searchField.getText());
    }

    private void openSettings() {
        actionsMenu.hide();
        quickHelp.hide();
        pasteService.clearTarget();
        this.onOpenSettings.run();
    }

    public void hide() {
        suppressAutoHide = false;
        autoHideDelay.stop();
        pasteService.clearTarget();
        hideWindowOnly();
    }

    private void hideForPaste() {
        // Prevent the normal focus-loss auto-hide callback from clearing
        // the captured target before the delayed Ctrl+V is sent.
        suppressAutoHide = true;
        autoHideDelay.stop();
        hideWindowOnly();
    }

    private void hideWindowOnly() {
        actionsMenu.hide();
        quickHelp.hide();
        stage.hide();
    }

    public void shutdown() {
        reloadGate.invalidate();
        if (pendingSearch != null) pendingSearch.cancel(false);
        previewCache.clear();
        contentTypeCache.clear();
        pasteService.close();
        dbExec.shutdownNow();
        debounceExec.shutdownNow();
    }

    private void showToast(String msg) {
        showToast(msg, StatusTone.NEUTRAL);
    }

    private void showToast(String msg, StatusTone tone) {
        if (actionBar == null) return;

        statusReset.stop();
        actionBar.showStatus(msg, tone);
        statusReset.playFromStart();
    }

    private <T> T showPopupModal(Supplier<T> dialogAction) {
        java.util.Objects.requireNonNull(dialogAction, "dialogAction");

        pasteService.clearTarget();
        suppressAutoHide = true;
        autoHideDelay.stop();
        actionsMenu.hide();
        quickHelp.hide();

        try {
            return dialogAction.get();
        } finally {
            suppressAutoHide = false;
            restorePopupFocus();
        }
    }

    private void restorePopupFocus() {
        Platform.runLater(() -> {
            if (!stage.isShowing()) return;
            stage.toFront();
            stage.requestFocus();
            listView.requestFocus();
        });
    }

    private void showOperationError(
            String status,
            String windowTitle,
            String heading,
            String body
    ) {
        showPopupModal(() -> {
            UiDialogs.showError(stage, windowTitle, heading, body);
            return null;
        });
        showToast(status, StatusTone.ERROR);
    }

    private void debounceReload() {
        String q = searchField.getText();
        MultiSelectionSnapshot snap = MultiSelectionSnapshot.capture(listView, items, selectionAnchorIndex);
        if (pendingSearch != null) pendingSearch.cancel(false);
        pendingSearch = debounceExec.schedule(
                () -> reloadNow(q, snap),
                PopupPerformancePolicy.SEARCH_DEBOUNCE_MS,
                TimeUnit.MILLISECONDS
        );
    }

    private void reloadNow(String q) {
        reloadNow(q, MultiSelectionSnapshot.capture(listView, items, selectionAnchorIndex));
    }

    private void reloadNow(String q, MultiSelectionSnapshot snap) {
        String query = q == null ? "" : q;
        String normQuery = query.trim();

        PopupViewState stateSnapshot = viewState;
        ClipViewScope scopeSnapshot = stateSnapshot.scope();
        ClipContentType typeSnapshot = stateSnapshot.contentType();
        Long tagSnapshot = stateSnapshot.tagId();
        long requestGeneration = reloadGate.nextRequest();

        currentQueryRaw = normQuery;
        currentQueryLower = normQuery.isEmpty() ? "" : normQuery.toLowerCase(Locale.ROOT);

        Platform.runLater(this::updateEmptyStateText);

        dbExec.submit(() -> {
            int limit = Math.max(1, uiClipLimit);
            int candidateLimit = PopupPerformancePolicy.candidateLimit(
                    limit,
                    typeSnapshot != null
            );

            int totalClipCount = dao.countAll();

            List<ClipEntry> candidates = dao.queryLatest(
                    normQuery,
                    candidateLimit,
                    scopeSnapshot.favoriteFilter(),
                    tagSnapshot
            );

            List<ClipEntry> list = new java.util.ArrayList<>(ClipFilterEngine.apply(
                    candidates,
                    scopeSnapshot,
                    typeSnapshot,
                    limit,
                    this::contentTypeFor
            ));

            list.sort(
                    Comparator.comparing(ClipEntry::favorite).reversed()
                            .thenComparingInt(ClipEntry::effectivePinOrder)
                            .thenComparing(Comparator.comparingLong(ClipEntry::createdAt).reversed())
            );

            Map<Long, List<ClipTag>> tagsByClipId = tagDao == null
                    ? Map.of()
                    : tagDao.listForClips(
                            list.stream().map(ClipEntry::id).toList()
                    );
            List<ClipTag> availableTags = tagDao == null
                    ? List.of()
                    : tagDao.listAll();

            List<PopupRow> preparedRows = PopupRows.build(list, tagsByClipId);
            int visibleClipCount = list.size();

            Platform.runLater(() -> {
                if (!reloadGate.isCurrent(requestGeneration)) return;

                syncTagFilterOptions(availableTags);
                items.setAll(preparedRows);
                countLabel.setText("Clips " + totalClipCount);
                countLabel.setAccessibleText(
                        visibleClipCount < totalClipCount
                                ? "Showing " + visibleClipCount + " of " + totalClipCount + " clips"
                                : totalClipCount + (totalClipCount == 1 ? " clip" : " clips")
                );
                countLabel.setTooltip(visibleClipCount < totalClipCount
                        ? new Tooltip("Showing " + visibleClipCount + " of " + totalClipCount + " clips")
                        : null);

                updateEmptyStateText();

                if (items.isEmpty()) {
                    listView.getSelectionModel().clearSelection();
                    selectionAnchorIndex = -1;
                    updateSelectionUi();
                    return;
                }

                // --- restore multi-selection by ids ---
                listView.getSelectionModel().clearSelection();

                boolean restoredAny = false;
                java.util.Set<Long> ids = (snap == null) ? java.util.Set.of() : snap.ids();

                if (!ids.isEmpty()) {
                    for (int i = 0; i < items.size(); i++) {
                        PopupRow r = items.get(i);
                        if (r instanceof ClipRow cr && ids.contains(cr.entry().id())) {
                            listView.getSelectionModel().select(i);
                            restoredAny = true;
                        }
                    }
                }

                // restore anchorIndex from anchorId (if possible)
                if (snap != null && snap.anchorId() > 0) {
                    for (int i = 0; i < items.size(); i++) {
                        PopupRow r = items.get(i);
                        if (r instanceof ClipRow cr && cr.entry().id() == snap.anchorId()) {
                            selectionAnchorIndex = i;
                            break;
                        }
                    }
                }

                // if nothing restored -> select first clip
                if (!restoredAny) {
                    int firstClip = findFirstClipIndex();
                    if (firstClip >= 0) {
                        selectAndReveal(firstClip);
                        selectionAnchorIndex = firstClip;
                    }
                }

                // finally: always show top context
                revealAnchor();
                updateSelectionUi();
            });
        });
    }

    private int findFirstClipIndex() {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof ClipRow) return i;
        }
        return -1;
    }

    private List<ClipEntry> getSelectedClipsOrdered() {
        List<Integer> idxs = new java.util.ArrayList<>(listView.getSelectionModel().getSelectedIndices());
        idxs.sort(Integer::compareTo);

        List<ClipEntry> out = new java.util.ArrayList<>();
        for (int idx : idxs) {
            if (idx < 0 || idx >= items.size()) continue;
            PopupRow r = items.get(idx);
            if (r instanceof ClipRow cr) out.add(cr.entry());
        }
        return out;
    }


    private ClipEntry getSelectedClipOrNull() {
        PopupRow r = listView.getSelectionModel().getSelectedItem();
        if (r instanceof ClipRow cr) return cr.entry();
        return null;
    }

    private void pasteSelectedOrFirst() {
        List<ClipEntry> selected = getSelectedClipsOrdered();

        if (!selected.isEmpty()) {
            pasteText(joinClipContents(selected));
            return;
        }

        int idx = findFirstClipIndex();
        if (idx >= 0) {
            listView.getSelectionModel().clearAndSelect(idx);
            ClipEntry sel = getSelectedClipOrNull();
            if (sel != null) pasteEntry(sel);
        }
    }

    private void pasteEntry(ClipEntry entry) {
        if (entry == null) return;
        pasteText(entry.content());
    }

    private void pasteText(String text) {
        PasteService.StartResult result = pasteService.paste(text, this::hideForPaste);
        if (result == PasteService.StartResult.CLIPBOARD_UNAVAILABLE) {
            showToast("Clipboard unavailable", StatusTone.ERROR);
        }
    }

    private void copySelectedOrFirst() {
        List<ClipEntry> selected = getSelectedClipsOrdered();

        if (!selected.isEmpty()) {
            copyText(joinClipContents(selected));
            return;
        }

        int idx = findFirstClipIndex();
        if (idx >= 0) {
            listView.getSelectionModel().clearAndSelect(idx);
            ClipEntry sel = getSelectedClipOrNull();
            if (sel != null) copyEntry(sel);
        }
    }

    private String joinClipContents(List<ClipEntry> clips) {
        return clips.stream()
                .map(e -> e.content() == null ? "" : e.content())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private void copyEntry(ClipEntry entry) {
        if (entry == null) return;
        copyText(entry.content());
    }

    private void copyText(String text) {
        clipService.markPushedByApp(text);
        if (clipboard.setTextSafely(text)) {
            hide();
        } else {
            showToast("Clipboard unavailable", StatusTone.ERROR);
        }
    }

    private ClipPrimaryAction primaryActionFor(ClipEntry entry) {
        if (entry == null) return ClipPrimaryAction.NONE;
        return ClipContentActionService.primaryActionFor(contentTypeFor(entry));
    }

    private void performPrimaryTypeActionForSelection() {
        List<ClipEntry> selected = getSelectedClipsOrdered();
        if (selected.size() != 1) {
            showToast("Select one clip", StatusTone.WARNING);
            return;
        }
        performPrimaryTypeAction(selected.get(0));
    }

    private void performPrimaryTypeAction(ClipEntry entry) {
        if (entry == null) return;

        ClipPrimaryAction action = primaryActionFor(entry);
        String content = entry.content() == null ? "" : entry.content();

        switch (action) {
            case OPEN_URL -> handleExternalOpenResult(
                    externalOpenService.openUrl(content),
                    "Opened in browser",
                    "Invalid URL",
                    "Couldn't open URL"
            );
            case REVEAL_PATH -> handleExternalOpenResult(
                    externalOpenService.revealPath(content),
                    "Shown in Explorer",
                    "Invalid path",
                    "Couldn't show path"
            );
            case COPY_FORMATTED_JSON, COPY_CODE, COPY_COMMAND ->
                    ClipContentActionService.clipboardTextFor(action, content)
                            .ifPresentOrElse(
                                    this::copyText,
                                    () -> showToast(
                                            action == ClipPrimaryAction.COPY_FORMATTED_JSON
                                                    ? "Invalid JSON"
                                                    : "Nothing to copy",
                                            action == ClipPrimaryAction.COPY_FORMATTED_JSON
                                                    ? StatusTone.ERROR
                                                    : StatusTone.WARNING
                                    )
                            );
            case NONE -> showToast("No type action available", StatusTone.WARNING);
        }
    }

    private void handleExternalOpenResult(
            ExternalOpenService.OpenResult result,
            String successMessage,
            String invalidMessage,
            String failedMessage
    ) {
        if (result == null) {
            showToast(failedMessage, StatusTone.ERROR);
            return;
        }

        switch (result) {
            case OPENED -> showToast(successMessage, StatusTone.SUCCESS);
            case INVALID_INPUT -> showToast(invalidMessage, StatusTone.ERROR);
            case NOT_FOUND -> showToast("Path not found", StatusTone.ERROR);
            case UNSUPPORTED -> showToast("Action unavailable on this system", StatusTone.WARNING);
            case FAILED -> showToast(failedMessage, StatusTone.ERROR);
        }
    }

    private void manageTagsLibrary() {
        if (tagDao == null) {
            showToast("Tags are unavailable", StatusTone.WARNING);
            return;
        }

        showToast("Loading tag library…", StatusTone.NEUTRAL);
        dbExec.submit(() -> {
            try {
                List<TagSummary> summaries = tagDao.listAllWithUsage();

                Platform.runLater(() -> {
                    if (!stage.isShowing()) return;

                    TagManagementDialog.Actions dialogActions = new TagManagementDialog.Actions() {
                        @Override
                        public CompletionStage<List<TagSummary>> rename(long tagId, String newName) {
                            return CompletableFuture.supplyAsync(() -> {
                                boolean renamed = tagDao.renameTag(tagId, newName);
                                if (!renamed) {
                                    throw new IllegalArgumentException("The tag no longer exists.");
                                }
                                return tagDao.listAllWithUsage();
                            }, dbExec);
                        }

                        @Override
                        public CompletionStage<List<TagSummary>> delete(long tagId) {
                            return CompletableFuture.supplyAsync(() -> {
                                boolean deleted = tagDao.deleteTag(tagId);
                                if (!deleted) {
                                    throw new IllegalArgumentException("The tag no longer exists.");
                                }
                                return tagDao.listAllWithUsage();
                            }, dbExec);
                        }

                        @Override
                        public CompletionStage<List<TagSummary>> cleanupUnused() {
                            return CompletableFuture.supplyAsync(() -> {
                                tagDao.cleanupUnusedTags();
                                return tagDao.listAllWithUsage();
                            }, dbExec);
                        }
                    };

                    TagManagementDialog.Result result = showPopupModal(() ->
                            TagManagementDialog.show(stage, summaries, dialogActions)
                    );
                    if (result.changed()) {
                        Long activeTagId = viewState.tagId();
                        boolean activeTagStillExists = activeTagId == null
                                || result.tags().stream().anyMatch(tag -> tag.id() == activeTagId);
                        if (!activeTagStillExists) {
                            setFilterState(
                                    viewState.scope(),
                                    viewState.contentType(),
                                    null,
                                    false
                            );
                        }
                        reloadNow(searchField.getText());
                        showToast("Tag library updated", StatusTone.SUCCESS);
                    }
                });
            } catch (Throwable failure) {
                Platform.runLater(() -> showOperationError(
                        "Tags unavailable",
                        "Manage tags failed",
                        "The tag library could not be loaded",
                        "No clipboard data was changed. Reopen XClip and try again."
                ));
            }
        });
    }

    private void editTagsSelected() {
        List<ClipEntry> selected = getSelectedClipsOrdered();
        if (selected.isEmpty()) return;
        if (tagDao == null) {
            showToast("Tags are unavailable", StatusTone.WARNING);
            return;
        }

        List<Long> clipIds = selected.stream().map(ClipEntry::id).toList();
        showToast("Loading tags…", StatusTone.NEUTRAL);

        dbExec.submit(() -> {
            try {
                List<ClipTag> allTags = tagDao.listAll();
                Map<Long, List<ClipTag>> assignmentsByClip =
                        new LinkedHashMap<>(tagDao.listForClips(clipIds));

                Platform.runLater(() -> {
                    if (!stage.isShowing()) return;

                    java.util.Optional<EditPlan> result = showPopupModal(() ->
                            TagEditorDialog.show(
                                    stage,
                                    clipIds,
                                    allTags,
                                    assignmentsByClip
                            )
                    );
                    result.ifPresent(plan -> saveTagEdit(clipIds, plan));
                });
            } catch (Throwable failure) {
                Platform.runLater(() -> showOperationError(
                        "Tags unavailable",
                        "Load tags failed",
                        "Tags could not be loaded",
                        "No clipboard data was changed. Reopen XClip and try again."
                ));
            }
        });
    }

    private void saveTagEdit(List<Long> clipIds, EditPlan plan) {
        if (tagDao == null || plan == null || plan.isEmpty()) return;

        showToast("Saving tags…", StatusTone.NEUTRAL);
        dbExec.submit(() -> {
            try {
                List<ClipTag> resolvedNewTags = tagDao.applyEdit(
                        clipIds,
                        plan.assignTagIds(),
                        plan.removeTagIds(),
                        plan.createAndAssignNames()
                );

                Platform.runLater(() -> {
                    reloadNow(searchField.getText());

                    int changedExisting = plan.assignTagIds().size()
                            + plan.removeTagIds().size();
                    int created = resolvedNewTags.size();
                    String message;
                    if (created > 0 && changedExisting > 0) {
                        message = "Tags updated · " + created + " created";
                    } else if (created > 0) {
                        message = created == 1 ? "Tag created and assigned" : created + " tags created";
                    } else {
                        message = "Tags updated";
                    }
                    showToast(message, StatusTone.SUCCESS);
                });
            } catch (IllegalArgumentException invalid) {
                Platform.runLater(() -> showOperationError(
                        "Tag save failed",
                        "Save tags failed",
                        "The tag changes were not saved",
                        java.util.Objects.requireNonNullElse(
                                invalid.getMessage(),
                                "One or more tag values are invalid."
                        )
                ));
            } catch (Throwable failure) {
                Platform.runLater(() -> showOperationError(
                        "Tag save failed",
                        "Save tags failed",
                        "The tag changes were not saved",
                        "The operation was rolled back, so existing assignments remain unchanged."
                ));
            }
        });
    }

    private void toggleFavoriteSelected() {
        List<ClipEntry> selected = getSelectedClipsOrdered();
        if (selected.isEmpty()) return;

        boolean shouldPin = selected.stream().anyMatch(e -> !e.favorite());
        selected.forEach(entry -> expandedById.remove(entry.id()));

        dbExec.submit(() -> {
            List<ClipEntry> processingOrder = new java.util.ArrayList<>(selected);
            if (shouldPin) {
                // setFavorite places each newly pinned item at the top. Processing
                // bottom-to-top preserves the visual order of a multi-selection.
                java.util.Collections.reverse(processingOrder);
            }

            for (ClipEntry e : processingOrder) {
                dao.setFavorite(e.id(), shouldPin);
            }
            Platform.runLater(() -> {
                reloadNow(searchField.getText());
                showToast(
                        (shouldPin ? "Pinned" : "Unpinned")
                                + (selected.size() > 1 ? (" (" + selected.size() + ")") : ""),
                        StatusTone.SUCCESS
                );
            });
        });
    }

    private void moveSelectedPinned(PinnedMoveAction action) {
        List<ClipEntry> selected = getSelectedClipsOrdered();
        if (selected.size() != 1 || !selected.get(0).favorite()) {
            showToast("Select one pinned clip", StatusTone.WARNING);
            return;
        }

        ClipEntry entry = selected.get(0);
        dbExec.submit(() -> {
            boolean moved = switch (action) {
                case UP -> dao.movePinnedUp(entry.id());
                case DOWN -> dao.movePinnedDown(entry.id());
                case TOP -> dao.movePinnedToTop(entry.id());
                case BOTTOM -> dao.movePinnedToBottom(entry.id());
            };

            Platform.runLater(() -> {
                reloadNow(searchField.getText());
                if (moved) {
                    showToast(
                            switch (action) {
                                case UP -> "Moved up";
                                case DOWN -> "Moved down";
                                case TOP -> "Moved to top";
                                case BOTTOM -> "Moved to bottom";
                            },
                            StatusTone.SUCCESS
                    );
                } else {
                    showToast(
                            switch (action) {
                                case UP, TOP -> "Already at top";
                                case DOWN, BOTTOM -> "Already at bottom";
                            },
                            StatusTone.WARNING
                    );
                }
            });
        });
    }

    private void renameSelectedPinned() {
        List<ClipEntry> selected = getSelectedClipsOrdered();
        if (selected.size() != 1 || !selected.get(0).favorite()) {
            showToast("Select one pinned clip", StatusTone.WARNING);
            return;
        }

        ClipEntry entry = selected.get(0);
        java.util.Optional<String> result = showPopupModal(() ->
                UiDialogs.promptPinnedTitle(
                        stage,
                        entry.title(),
                        PINNED_TITLE_MAX_LENGTH
                )
        );
        if (result.isEmpty()) return;

        String normalized = result.get();
        String oldTitle = entry.title() == null ? "" : entry.title().trim();
        if (normalized.equals(oldTitle)) return;

        dbExec.submit(() -> {
            try {
                dao.setTitle(entry.id(), normalized);
                Platform.runLater(() -> {
                    reloadNow(searchField.getText());
                    showToast(
                            normalized.isEmpty() ? "Title cleared" : "Title saved",
                            StatusTone.SUCCESS
                    );
                });
            } catch (Throwable failure) {
                Platform.runLater(() -> showOperationError(
                        "Rename failed",
                        "Rename failed",
                        "The pinned clip title was not saved",
                        "The clipboard entry is unchanged. Try again after reopening XClip."
                ));
            }
        });
    }

    private void clearSelectedTitle() {
        List<ClipEntry> selected = getSelectedClipsOrdered();
        if (selected.size() != 1 || !selected.get(0).favorite()) {
            showToast("Select one pinned clip", StatusTone.WARNING);
            return;
        }

        ClipEntry entry = selected.get(0);
        if (!entry.hasTitle()) {
            showToast("No title to clear", StatusTone.WARNING);
            return;
        }

        dbExec.submit(() -> {
            try {
                dao.setTitle(entry.id(), null);
                Platform.runLater(() -> {
                    reloadNow(searchField.getText());
                    showToast("Title cleared", StatusTone.SUCCESS);
                });
            } catch (Throwable failure) {
                Platform.runLater(() -> showOperationError(
                        "Clear title failed",
                        "Clear title failed",
                        "The pinned clip title was not cleared",
                        "The clipboard entry is unchanged. Try again after reopening XClip."
                ));
            }
        });
    }

    private void toggleExpandSelected() {
        ClipEntry target = null;

        PopupRow focused = listView.getSelectionModel().getSelectedItem();
        if (focused instanceof ClipRow clipRow && !clipRow.entry().favorite()) {
            target = clipRow.entry();
        }

        if (target == null) {
            for (PopupRow row : listView.getSelectionModel().getSelectedItems()) {
                if (row instanceof ClipRow clipRow && !clipRow.entry().favorite()) {
                    target = clipRow.entry();
                    break;
                }
            }
        }

        if (target == null) {
            int first = findFirstClipIndex();
            if (first >= 0 && items.get(first) instanceof ClipRow clipRow
                    && !clipRow.entry().favorite()) {
                target = clipRow.entry();
            }
        }

        if (target == null) {
            showToast("Pinned clips stay compact", StatusTone.WARNING);
            return;
        }

        long id = target.id();
        boolean expand = !expandedById.getOrDefault(id, false);
        expandedById.clear();
        if (expand) expandedById.put(id, true);
        listView.refresh();
    }

    private boolean collapseExpandedPreviews() {
        if (expandedById.isEmpty()) return false;

        expandedById.clear();
        listView.refresh();
        showToast("Preview collapsed", StatusTone.NEUTRAL);
        return true;
    }

    private void deleteSelected() {
        List<ClipEntry> selected = getSelectedClipsOrdered();
        if (selected.isEmpty()) return;

        if (selected.size() > 1) {
            int pinnedCount = (int) selected.stream().filter(ClipEntry::favorite).count();
            boolean confirmed = showPopupModal(() ->
                    UiDialogs.confirmBatchDelete(stage, selected.size(), pinnedCount)
            );
            if (!confirmed) return;
        }

        dbExec.submit(() -> {
            try {
                for (ClipEntry entry : selected) {
                    dao.deleteById(entry.id());
                }

                Platform.runLater(() -> {
                    for (ClipEntry entry : selected) {
                        expandedById.remove(entry.id());
                        previewCache.remove(entry.id());
                        contentTypeCache.remove(entry.id());
                    }
                    reloadNow(searchField.getText());
                    showToast(
                            selected.size() == 1
                                    ? "Deleted"
                                    : "Deleted " + selected.size() + " clips",
                            StatusTone.SUCCESS
                    );
                });
            } catch (Throwable failure) {
                Platform.runLater(() -> {
                    reloadNow(searchField.getText());
                    showOperationError(
                        "Delete failed",
                        "Delete failed",
                        "The selected clips were not fully deleted",
                        "XClip could not complete the database operation. Reopen the popup and try again."
                    );
                });
            }
        });
    }

    private void clearHistoryNonFavorites() {
        java.util.List<Long> visibleNonFavoriteIds = new java.util.ArrayList<>();

        for (PopupRow row : items) {
            if (row instanceof ClipRow clipRow && !clipRow.entry().favorite()) {
                visibleNonFavoriteIds.add(clipRow.entry().id());
            }
        }

        if (visibleNonFavoriteIds.isEmpty()) {
            showToast("Nothing to clear", StatusTone.WARNING);
            return;
        }

        boolean confirmed = showPopupModal(() ->
                UiDialogs.confirmClearVisible(stage, visibleNonFavoriteIds.size())
        );
        if (!confirmed) return;

        dbExec.submit(() -> {
            try {
                dao.deleteByIds(visibleNonFavoriteIds);
                Platform.runLater(() -> {
                    java.util.Set<Long> removed = new java.util.HashSet<>(visibleNonFavoriteIds);
                    expandedById.keySet().removeIf(removed::contains);
                    previewCache.removeKeys(removed);
                    contentTypeCache.removeKeys(removed);
                    clearSelection();
                    reloadNow(searchField.getText());
                    showToast(
                            "Cleared " + visibleNonFavoriteIds.size() + " visible clips",
                            StatusTone.SUCCESS
                    );
                });
            } catch (Throwable failure) {
                Platform.runLater(() -> showOperationError(
                        "Clear failed",
                        "Clear history failed",
                        "Visible clipboard history was not cleared",
                        "No pinned clips were affected. Reopen the popup and try again."
                ));
            }
        });
    }

    private WindowBounds positionNearMouse() {
        List<WindowBounds> screens = currentVisualScreens();
        Point2D pointer = currentPointerPosition();

        WindowBounds screen = WindowChromeController.screenForPoint(
                pointer.getX(),
                pointer.getY(),
                screens
        ).orElseGet(() -> toWindowBounds(Screen.getPrimary().getVisualBounds()));

        double width = Math.min(
                Math.max(stage.getMinWidth(), stage.getWidth()),
                screen.width()
        );
        double height = Math.min(
                Math.max(stage.getMinHeight(), stage.getHeight()),
                screen.height()
        );

        double minX = screen.x() + WINDOW_EDGE_MARGIN;
        double minY = screen.y() + WINDOW_EDGE_MARGIN;
        double maxX = screen.x() + screen.width() - width - WINDOW_EDGE_MARGIN;
        double maxY = screen.y() + screen.height() - height - WINDOW_EDGE_MARGIN;

        double x = clamp(pointer.getX(), minX, Math.max(minX, maxX));
        double y = clamp(pointer.getY(), minY, Math.max(minY, maxY));

        WindowBounds placed = new WindowBounds(x, y, width, height);
        windowChrome.applyRestoredBounds(placed);
        return placed;
    }

    private Point2D currentPointerPosition() {
        try {
            Point2D point = new Robot().getMousePosition();
            if (point != null
                    && Double.isFinite(point.getX())
                    && Double.isFinite(point.getY())) {
                return point;
            }
        } catch (Throwable ignored) {
        }

        Rectangle2D primary = Screen.getPrimary().getVisualBounds();
        return new Point2D(
                primary.getMinX() + primary.getWidth() / 2.0,
                primary.getMinY() + primary.getHeight() / 2.0
        );
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (maximum < minimum) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private PreviewData getPreviewData(long id, String full) {
        PreviewData cached = previewCache.get(id);
        if (cached != null) return cached;

        PreviewData pd = computePreviewData(full);
        previewCache.put(id, pd);
        return pd;
    }

    private ClipContentType contentTypeFor(ClipEntry entry) {
        if (entry == null) return ClipContentType.TEXT;

        String content = entry.content() == null ? "" : entry.content();
        PopupPerformancePolicy.ContentFingerprint fingerprint =
                PopupPerformancePolicy.fingerprint(content);
        ContentTypeCache cached = contentTypeCache.get(entry.id());

        if (cached != null && cached.fingerprint().equals(fingerprint)) {
            return cached.type();
        }

        ClipContentType type = ClipContentClassifier.classify(content);
        contentTypeCache.put(entry.id(), new ContentTypeCache(fingerprint, type));
        return type;
    }

    private PreviewData computePreviewData(String s) {
        if (s == null || s.isEmpty()) return new PreviewData(false, "");

        // Fast scan: count lines up to PREVIEW_LINES+1 and build preview up to PREVIEW_CHAR_LIMIT
        int lines = 1;
        int len = s.length();

        StringBuilder sb = new StringBuilder(Math.min(len, PREVIEW_CHAR_LIMIT + 8));

        boolean truncated = false;
        int previewLines = 0;

        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);

            // line breaks: treat '\n' as line separator (works for Windows too, because '\r\n' contains '\n')
            if (ch == '\n') {
                lines++;
                previewLines++;
                if (previewLines >= PREVIEW_LINES) {
                    // stop preview after N lines, but keep scanning a bit for needsToggle
                    // We'll stop building preview, but continue counting lines only if needed.
                    // Mark truncated by lines (if there is more content after this).
                    if (i + 1 < len) truncated = true;
                    break;
                }
            }

            if (sb.length() < PREVIEW_CHAR_LIMIT) {
                sb.append(ch);
            } else {
                truncated = true;
            }
        }

        // If we broke early because of lines, we still need to know if there are more lines beyond.
        // Quick check: if not already truncated by char limit, count up to PREVIEW_LINES+1.
        if (!truncated && lines <= PREVIEW_LINES) {
            // nothing
        } else {
            // Determine needsToggle cheaply
            // If we already know there is more content -> needsToggle true
        }

        String out = sb.toString().trim();

        boolean needsToggle = false;

        // needsToggle if longer than char limit or more lines than PREVIEW_LINES
        if (len > PREVIEW_CHAR_LIMIT) needsToggle = true;

        if (!needsToggle) {
            // Count lines up to PREVIEW_LINES+1 (cheap bound)
            int lc = 1;
            for (int i = 0; i < len; i++) {
                if (s.charAt(i) == '\n') {
                    lc++;
                    if (lc > PREVIEW_LINES) {
                        needsToggle = true;
                        break;
                    }
                }
            }
        }

        // append ellipsis if we truncated by chars or lines
        if (needsToggle) {
            if (!out.endsWith("…")) out = out + "…";
        }

        return new PreviewData(needsToggle, out);
    }


    private void selectAndReveal(int index) {
        if (index < 0 || index >= items.size()) return;

        listView.getSelectionModel().select(index);

        // 1st: request immediately
        listView.scrollTo(index);

        // 2nd: ensure after layout pass
        Platform.runLater(() -> listView.scrollTo(index));
    }
    private int findSectionIndex(String title) {
        for (int i = 0; i < items.size(); i++) {
            PopupRow r = items.get(i);
            if (r instanceof SectionRow sr && sr.title().equalsIgnoreCase(title)) return i;
        }
        return -1;
    }

    private int findBestAnchorIndex() {
        int pinned = findSectionIndex("PINNED");
        if (pinned >= 0) return pinned;

        int recent = findSectionIndex("RECENT");
        if (recent >= 0) return recent;

        return items.isEmpty() ? -1 : 0;
    }
    private void revealAnchor() {
        int anchor = findBestAnchorIndex();
        if (anchor < 0) return;

        listView.scrollTo(anchor);
        Platform.runLater(() -> listView.scrollTo(anchor));
    }
    private void updateSelectionUi() {
        if (pasteBtnRef == null || copyBtnRef == null || favBtnRef == null || delBtnRef == null) return;

        List<ClipEntry> selected = getSelectedClipsOrdered();
        int n = selected.size();

        boolean has = n > 0;
        selectedLabel.setVisible(has);
        selectedLabel.setManaged(has);
        selectedLabel.setText(has ? ("Selected " + n) : "");
        selectedLabel.setAccessibleText(has
                ? n + (n == 1 ? " clip selected" : " clips selected")
                : "No clips selected");

        // The header already owns the selection count. Footer actions stay
        // visually stable and do not repeat "(N)" on every button.
        pasteBtnRef.setText("Paste");
        copyBtnRef.setText("Copy");
        delBtnRef.setText("Delete");

        if (!has) {
            favBtnRef.setText("Pin / Unpin");
            favBtnRef.setAccessibleText("Pin or unpin selected clips");
            favBtnRef.setGraphic(SvgIcon.of(
                    UiIcon.PIN,
                    17,
                    "action-icon",
                    "favorite-action-icon"
            ));
            return;
        }

        boolean shouldPin = selected.stream().anyMatch(e -> !e.favorite());
        favBtnRef.setText(shouldPin ? "Pin" : "Unpin");
        favBtnRef.setAccessibleText(
                (shouldPin ? "Pin " : "Unpin ")
                        + n
                        + (n == 1 ? " selected clip" : " selected clips")
        );
        favBtnRef.setGraphic(SvgIcon.of(
                shouldPin ? UiIcon.PIN : UiIcon.PIN_OFF,
                17,
                "action-icon",
                "favorite-action-icon"
        ));
    }

    private void selectAllClips() {
        MultipleSelectionModel<PopupRow> sm = listView.getSelectionModel();
        sm.clearSelection();

        int first = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof ClipRow) {
                sm.select(i);
                if (first < 0) first = i;
            }
        }
        if (first >= 0) {
            final int firstIndex = first;
            selectionAnchorIndex = firstIndex;
            listView.scrollTo(firstIndex);
            Platform.runLater(() -> listView.scrollTo(firstIndex));
        }
    }

    private void selectSectionClips(String sectionTitle) {
        MultipleSelectionModel<PopupRow> sm = listView.getSelectionModel();
        sm.clearSelection();

        boolean inSection = false;
        int firstIndex = -1;

        for (int i = 0; i < items.size(); i++) {
            PopupRow r = items.get(i);

            if (r instanceof SectionRow sr) {
                inSection = sr.title().equalsIgnoreCase(sectionTitle);
                continue;
            }

            if (inSection && r instanceof ClipRow) {
                sm.select(i);
                if (firstIndex < 0) firstIndex = i;
            }
        }

        if (firstIndex >= 0) {
            selectionAnchorIndex = firstIndex;
            listView.scrollTo(firstIndex);
            final int fi = firstIndex;
            Platform.runLater(() -> listView.scrollTo(fi));
        }
    }

    private void clearSelection() {
        listView.getSelectionModel().clearSelection();
        selectionAnchorIndex = -1;
        updateSelectionUi();
    }
    private void invertSelection() {
        MultipleSelectionModel<PopupRow> sm = listView.getSelectionModel();

        java.util.Set<Integer> selected = new java.util.HashSet<>(sm.getSelectedIndices());
        sm.clearSelection();

        int firstIndex = -1;

        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof ClipRow)) continue;
            if (!selected.contains(i)) {
                sm.select(i);
                if (firstIndex < 0) firstIndex = i;
            }
        }

        if (firstIndex >= 0) {
            selectionAnchorIndex = firstIndex;
            listView.scrollTo(firstIndex);
            final int fi = firstIndex;
            Platform.runLater(() -> listView.scrollTo(fi));
        }

        updateSelectionUi();
    }
}




