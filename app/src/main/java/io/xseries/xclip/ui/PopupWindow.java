/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui;

import io.xseries.xclip.system.window.WindowsTitleBar;
import io.xseries.xclip.data.dao.ClipEntryDao;
import io.xseries.xclip.data.model.ClipEntry;
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
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.css.PseudoClass;
import javafx.scene.input.MouseEvent;
import java.awt.MouseInfo;
import java.awt.Point;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;

public final class PopupWindow {

    private static final int WIDTH = 520;
    private static final int HEIGHT = 420;
    private volatile int uiClipLimit = io.xseries.xclip.config.Config.DEFAULT_UI_CLIP_LIMIT;

    private double lastNormalX = -1;
    private double lastNormalY = -1;
    private double lastNormalW = -1;
    private double lastNormalH = -1;

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

    // Preview behavior (prevents "text wall" in list)
    private static final int PREVIEW_LINES = 3;
    private static final int PREVIEW_CHAR_LIMIT = 320;

    // Pinned clips remain intentionally compact even when their content is large.
    private static final int PINNED_TITLE_MAX_LENGTH = 120;
    private static final int PINNED_COMPACT_CHAR_LIMIT = 220;
    private static final int TOOLTIP_CHAR_LIMIT = 2_000;
    private static final int TYPE_FILTER_SCAN_LIMIT = 5_000;

    // Expanded preview (still bounded to protect UI)
    private static final int EXPANDED_LINES = 200;
    private static final int EXPANDED_CHAR_LIMIT = 100_000;

    private final Stage stage;
    private final TextField searchField = new TextField();
    private final ListView<Row> listView = new ListView<>();
    private final ObservableList<Row> items = FXCollections.observableArrayList();

    private final ToggleGroup scopeFilterGroup = new ToggleGroup();
    private final ToggleButton filterAllBtn = new ToggleButton("All");
    private final ToggleButton filterPinnedBtn = new ToggleButton("Pinned");
    private final ToggleButton filterRecentBtn = new ToggleButton("Recent");
    private final ComboBox<ContentTypeOption> typeFilterCombo = new ComboBox<>();
    private final Button resetFiltersBtn = new Button("Reset filters");

    private volatile ClipViewScope currentScope = ClipViewScope.ALL;
    private volatile ClipContentType currentTypeFilter = null;
    private boolean filterUiSync = false;

    private final ClipEntryDao dao;
    private final ClipboardAccess clipboard;
    private final ClipService clipService;
    private final PasteService pasteService;
    private final ExternalOpenService externalOpenService = new ExternalOpenService();

    private final Runnable onOpenSettings;

    // per-entry expand state for "More/Less"
    private final Map<Long, Boolean> expandedById = new HashMap<>();
    // Cache for preview/needsToggle to avoid split("\\R") per cell repaint
    private record PreviewData(boolean needsToggle, String preview) {}
    private final Map<Long, PreviewData> previewCache = new HashMap<>();

    private record ContentTypeCache(String content, ClipContentType type) {}
    private final Map<Long, ContentTypeCache> contentTypeCache = new ConcurrentHashMap<>();

    // v1.1 UX state
    private final Label pausedBadge = new Label("PAUSED");
    private final Label countLabel = new Label();
    private final Label emptyStateLabel = new Label();
    private volatile boolean paused = false;

    // v1.2: current query (lowercased) for highlighting in cells
    private volatile String currentQueryLower = "";
    private volatile String currentQueryRaw = "";

    // prevent auto-hide while modal dialog is shown (Clear confirmation)
    private volatile boolean suppressAutoHide = false;

    // v1.2: toast (small feedback for power actions)
    private final Label toast = new Label();
    private final PauseTransition toastHide = new PauseTransition(Duration.millis(1400));

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
    private final java.util.concurrent.atomic.AtomicLong reloadGeneration =
            new java.util.concurrent.atomic.AtomicLong();

    private final PauseTransition autoHideDelay = new PauseTransition(Duration.millis(160));

    // -----------------------
    // List rows
    // -----------------------
    private sealed interface Row permits SectionRow, ClipRow {}

    private record SectionRow(String title) implements Row {}

    private record ClipRow(ClipEntry entry) implements Row {}

    private record ContentTypeOption(ClipContentType type, String label) {
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

        static MultiSelectionSnapshot capture(ListView<Row> lv, ObservableList<Row> items, int anchorIndex) {
            java.util.Set<Long> ids = new java.util.HashSet<>();

            for (Row r : lv.getSelectionModel().getSelectedItems()) {
                if (r instanceof ClipRow cr) ids.add(cr.entry().id());
            }

            long anchorId = -1L;
            if (anchorIndex >= 0 && anchorIndex < items.size()) {
                Row ar = items.get(anchorIndex);
                if (ar instanceof ClipRow cr) anchorId = cr.entry().id();
            }

            return new MultiSelectionSnapshot(ids, anchorId);
        }
    }

    // Context menu (created once)
    private final ContextMenu ctxMenu = new ContextMenu();
    private final MenuItem miPaste = new MenuItem("Paste");
    private final MenuItem miCopy = new MenuItem("Copy");
    private final MenuItem miTypeAction = new MenuItem();
    private final MenuItem miPin = new MenuItem("Pin / Unpin");
    private final MenuItem miRename = new MenuItem("Rename pinned clip…");
    private final MenuItem miClearTitle = new MenuItem("Clear title");
    private final Menu movePinnedMenu = new Menu("Move pinned clip");
    private final MenuItem miMoveUp = new MenuItem("Move up");
    private final MenuItem miMoveDown = new MenuItem("Move down");
    private final MenuItem miMoveTop = new MenuItem("Move to top");
    private final MenuItem miMoveBottom = new MenuItem("Move to bottom");
    private final MenuItem miDelete = new MenuItem("Delete");

    public PopupWindow(ClipEntryDao dao, ClipboardAccess clipboard, ClipService clipService) {
        this(dao, clipboard, clipService, () -> {});
    }

    public PopupWindow(ClipEntryDao dao, ClipboardAccess clipboard, ClipService clipService, Runnable onOpenSettings) {
        this(
                dao,
                clipboard,
                clipService,
                onOpenSettings,
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
        this.dao = dao;
        this.clipboard = clipboard;
        this.clipService = clipService;
        this.pasteService = java.util.Objects.requireNonNull(pasteService);
        this.onOpenSettings = (onOpenSettings != null) ? onOpenSettings : (() -> {});

        stage = new Stage(StageStyle.DECORATED);
        stage.setTitle("XClip");
        stage.getIcons().add(new javafx.scene.image.Image(
                PopupWindow.class.getResourceAsStream("/icons/icon.png")
        ));
        stage.setAlwaysOnTop(true);
        stage.setResizable(true);
        stage.setMinWidth(500);
        stage.setMinHeight(300);

        listView.setItems(items);
        listView.setCellFactory(lv -> new RowCell()); // RowCell wires context menu + dblclick
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.getSelectionModel().getSelectedIndices().addListener((javafx.collections.ListChangeListener<? super Integer>) c ->
                Platform.runLater(this::updateSelectionUi)
        );

        listView.getStyleClass().add("clip-list");

        // Empty state placeholder
        emptyStateLabel.setWrapText(true);
        emptyStateLabel.setMaxWidth(360);
        emptyStateLabel.setPadding(Insets.EMPTY);
        emptyStateLabel.getStyleClass().add("empty-state");
        listView.setPlaceholder(emptyStateLabel);
        updateEmptyStateText();

        // Paused badge
        pausedBadge.setVisible(false);
        pausedBadge.setManaged(false);
        pausedBadge.setPadding(new Insets(2, 8, 2, 8));
        pausedBadge.getStyleClass().add("paused-badge");

        // Clip count indicator (counts only real clips, not section rows)
        countLabel.getStyleClass().add("topbar-status");
        countLabel.setText("Clips: 0");

        selectedLabel.getStyleClass().add("topbar-status");
        selectedLabel.setVisible(false);
        selectedLabel.setManaged(false);

        searchField.setPromptText("Search…");
        searchField.setMaxWidth(Double.MAX_VALUE);
        searchField.getStyleClass().add("search-field");

        Button clearSearchBtn = new Button("×");
        clearSearchBtn.setFocusTraversable(false);
        clearSearchBtn.getStyleClass().add("search-clear");
        clearSearchBtn.setVisible(false);
        clearSearchBtn.setManaged(false);

        clearSearchBtn.setOnAction(e -> {
            searchField.clear();
            searchField.requestFocus();
        });

        searchField.textProperty().addListener((obs, o, n) -> {
            boolean has = n != null && !n.isBlank();
            clearSearchBtn.setVisible(has);
            clearSearchBtn.setManaged(has);
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

            if (e.getCode() == KeyCode.TAB) {
                e.consume();
                listView.requestFocus();
            }
        });

        // Help
        Button help = new Button("?");
        help.setFocusTraversable(false);
        help.setAlignment(Pos.CENTER);
        help.setPadding(new Insets(2, 8, 2, 8));

        Tooltip tip = new Tooltip("""
        XClip — Quick Help

        Search:
        • Ctrl+F         Focus search
        • Ctrl+L         Clear search
        • Enter          Jump to first result
        • Esc            Clear search (if not empty) / Hide popup (if empty)
        • Tab            Focus list

        Filters:
        • All / Pinned / Recent restrict the visible section
        • Type filters by TEXT, CODE, URL, PATH, JSON, or COMMAND
        • Reset filters returns to the complete view
        • Search and filters can be combined

        Selection:
        • Ctrl+Click     Toggle item selection
        • Shift+Click    Select range
        • Ctrl+A         Select all clips (current list)
        • Ctrl+Shift+A   Select RECENT section
        • Ctrl+I         Invert selection
        • Ctrl+D         Clear selection

        Actions:
        • Ctrl+Shift+V   Open XClip and remember the active app
        • Enter          Paste selection into the remembered app
        • Ctrl+C         Copy selection only
        • Ctrl+P         Pin / Unpin selection
        • F2             Rename one pinned clip
        • Alt+↑          Move one pinned clip up
        • Alt+↓          Move one pinned clip down
        • E              Expand / Collapse selected recent clip
        • Delete         Delete selection
        • Double-click   Paste single item
        • Click badge    Run the safe primary type action
        • Right-click    Paste / Copy / Type action / Pin / Rename / Move / Delete

        Window:
        • Esc            Clear selection → clear search → hide popup (in this order)
        • Ctrl+,         Settings

        Notes:
        • Pinned clips are shown in PINNED section
        • Multi-copy joins clips with new lines
        • COMMAND actions only copy text; XClip never executes commands
        • PATH actions reveal files instead of launching them
        • Popup auto-hides when it loses focus
        """);

        tip.setWrapText(true);
        tip.setMaxWidth(340);
        tip.setShowDelay(Duration.millis(60));
        tip.setHideDelay(Duration.millis(40));
        tip.setShowDuration(Duration.seconds(30));
        Tooltip.install(help, tip);

        help.getStyleClass().addAll("topbar-btn", "topbar-help");

        // Settings button
        Button settingsBtn = new Button("Settings");
        settingsBtn.setFocusTraversable(false);
        settingsBtn.setTooltip(new Tooltip("Open settings"));
        settingsBtn.setOnAction(e -> openSettings());
        settingsBtn.getStyleClass().add("topbar-btn");

        // Clear button
        Button clearBtn = new Button("Clear");
        clearBtn.setFocusTraversable(false);
        clearBtn.setTooltip(new Tooltip("Clear non-favorites (keeps pinned)"));
        clearBtn.setOnAction(e -> clearHistoryNonFavorites());
        clearBtn.getStyleClass().add("topbar-btn");

        StackPane searchWrap = new StackPane(searchField, clearSearchBtn);
        searchWrap.getStyleClass().add("search-wrap");

        StackPane.setAlignment(clearSearchBtn, Pos.CENTER_RIGHT);
        StackPane.setMargin(clearSearchBtn, new Insets(0, 8, 0, 0));
        HBox.setHgrow(searchWrap, Priority.ALWAYS);

        searchWrap.setMinWidth(520);
        searchWrap.setPrefWidth(760);
        searchWrap.setMaxWidth(980);
        HBox.setHgrow(searchWrap, Priority.ALWAYS);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        HBox statusGroup = new HBox(10, pausedBadge, countLabel, selectedLabel);
        statusGroup.setAlignment(Pos.CENTER_RIGHT);
        statusGroup.getStyleClass().add("popup-status-group");

        HBox controlGroup = new HBox(8, help, settingsBtn, clearBtn);
        controlGroup.setAlignment(Pos.CENTER_RIGHT);
        controlGroup.getStyleClass().add("popup-control-group");

        HBox topBar = new HBox(12, searchWrap, headerSpacer, statusGroup, controlGroup);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        configureFilterControls();

        Label showFilterLabel = new Label("Show");
        showFilterLabel.getStyleClass().add("filter-label");

        HBox scopeButtons = new HBox(filterAllBtn, filterPinnedBtn, filterRecentBtn);
        scopeButtons.getStyleClass().add("filter-segment");

        Separator filterSeparator = new Separator(Orientation.VERTICAL);
        filterSeparator.getStyleClass().add("filter-separator");

        Label typeFilterLabel = new Label("Type");
        typeFilterLabel.getStyleClass().add("filter-label");

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        HBox filterBar = new HBox(
                10,
                showFilterLabel,
                scopeButtons,
                filterSeparator,
                typeFilterLabel,
                typeFilterCombo,
                filterSpacer,
                resetFiltersBtn
        );
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.getStyleClass().add("filter-bar");

        VBox popupHeader = new VBox(topBar, filterBar);
        popupHeader.getStyleClass().add("popup-header");


        Button pasteBtn = new Button("Paste");
        pasteBtn.setOnAction(e -> pasteSelectedOrFirst());
        pasteBtn.getStyleClass().addAll("action-btn", "action-primary");

        Button copyBtn = new Button("Copy");
        copyBtn.setOnAction(e -> copySelectedOrFirst());
        copyBtn.getStyleClass().addAll("action-btn", "action-neutral");

        Button favBtn = new Button("★");
        favBtn.setOnAction(e -> toggleFavoriteSelected());
        favBtn.getStyleClass().addAll("action-btn", "action-neutral");

        Button delBtn = new Button("Delete");
        delBtn.setOnAction(e -> deleteSelected());
        delBtn.getStyleClass().addAll("action-btn", "action-danger");

        this.pasteBtnRef = pasteBtn;
        this.copyBtnRef = copyBtn;
        this.favBtnRef = favBtn;
        this.delBtnRef = delBtn;

        HBox actions = new HBox(8, pasteBtn, copyBtn, favBtn, delBtn);

        actions.setPadding(new Insets(8));
        actions.getStyleClass().add("actions-bar");

        // Toast (overlay-style feedback inside the window)
        toast.setVisible(false);
        toast.setManaged(false);
        toast.getStyleClass().add("toast");

        BorderPane root = new BorderPane();
        root.setTop(popupHeader);

        BorderPane centerPane = new BorderPane();
        centerPane.setCenter(listView);

        BorderPane.setAlignment(toast, Pos.BOTTOM_RIGHT);
        BorderPane.setMargin(toast, new Insets(0, 10, 10, 0));
        centerPane.setBottom(toast);

        root.setCenter(centerPane);
        root.setBottom(actions);

        Scene scene = new Scene(root, WIDTH, HEIGHT);

        // Shared stylesheet (Popup + Settings)
        scene.getStylesheets().add(
                getClass().getResource("/ui/styles.css").toExternalForm()
        );

        stage.setScene(scene);

        stage.setOnCloseRequest(e -> {
            e.consume();
            hide();
        });

        // Auto-hide with suppression
        autoHideDelay.setOnFinished(e -> {
            if (!suppressAutoHide) hide();
        });
        stage.focusedProperty().addListener((o, was, now) -> {
            if (suppressAutoHide) return;
            if (!now) autoHideDelay.playFromStart();
            else autoHideDelay.stop();
        });

        // FIX: key handling at STAGE level (always works)
        stage.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() && e.isShiftDown() && e.getCode() == KeyCode.A) {
                e.consume();
                selectSectionClips("RECENT");
                listView.requestFocus();
                return;
            }
            if (e.isControlDown() && e.getCode() == KeyCode.A) {
                e.consume();
                selectAllClips();
                listView.requestFocus();
                return;
            }
            if (e.isControlDown() && e.getCode() == KeyCode.D) {
                e.consume();
                clearSelection();
                return;
            }
            if (e.isControlDown() && e.getCode() == KeyCode.I) {
                e.consume();
                invertSelection();
                listView.requestFocus();
                return;
            }
            if (e.isControlDown() && e.getCode() == KeyCode.F) {
                e.consume();
                searchField.requestFocus();
                searchField.selectAll();
                return;
            }
            if (e.isControlDown() && e.getCode() == KeyCode.L) {
                e.consume();
                searchField.clear();
                searchField.requestFocus();
                return;
            }
            if (e.isControlDown() && e.getCode() == KeyCode.C) {
                e.consume();
                copySelectedOrFirst();
                return;
            }
            if (e.isControlDown() && e.getCode() == KeyCode.P) {
                e.consume();
                toggleFavoriteSelected();
                return;
            }
            if (e.isControlDown() && e.getCode() == KeyCode.COMMA) {
                e.consume();

                openSettings();
                return;
            }
            if (e.isAltDown() && !e.isControlDown() && !e.isMetaDown()
                    && e.getCode() == KeyCode.UP) {
                if (e.getTarget() instanceof TextInputControl) return;

                e.consume();
                moveSelectedPinned(PinnedMoveAction.UP);
                return;
            }
            if (e.isAltDown() && !e.isControlDown() && !e.isMetaDown()
                    && e.getCode() == KeyCode.DOWN) {
                if (e.getTarget() instanceof TextInputControl) return;

                e.consume();
                moveSelectedPinned(PinnedMoveAction.DOWN);
                return;
            }
            if (!e.isControlDown() && !e.isAltDown() && !e.isMetaDown() && e.getCode() == KeyCode.F2) {
                if (e.getTarget() instanceof TextInputControl) return;

                e.consume();
                renameSelectedPinned();
                return;
            }
            // Expand/Collapse selected recent clip (UI-only, bounded)
            if (!e.isControlDown() && !e.isAltDown() && !e.isMetaDown() && e.getCode() == KeyCode.E) {
                // do not hijack typing in search/inputs
                if (e.getTarget() instanceof TextInputControl) return;

                e.consume();
                toggleExpandSelected();
                return;
            }
            if (e.getCode() == KeyCode.ESCAPE) {
                e.consume();
                if (!listView.getSelectionModel().getSelectedIndices().isEmpty()) {
                    clearSelection();
                } else if (!searchField.getText().isBlank()) {
                    searchField.clear();
                    searchField.requestFocus();
                } else {
                    hide();
                }
                return;
            }
            if (e.getCode() == KeyCode.ENTER) {
                if (e.getTarget() instanceof TextInputControl) return;

                e.consume();
                pasteSelectedOrFirst();
                return;
            }
            if (e.getCode() == KeyCode.DELETE) {
                e.consume();
                deleteSelected();
            }
        });

        // Context menu actions
        miPaste.setOnAction(e -> pasteSelectedOrFirst());
        miCopy.setOnAction(e -> copySelectedOrFirst());
        miTypeAction.setOnAction(e -> performPrimaryTypeActionForSelection());
        miTypeAction.setVisible(false);
        miPin.setOnAction(e -> toggleFavoriteSelected());
        miRename.setOnAction(e -> renameSelectedPinned());
        miClearTitle.setOnAction(e -> clearSelectedTitle());
        miMoveUp.setOnAction(e -> moveSelectedPinned(PinnedMoveAction.UP));
        miMoveDown.setOnAction(e -> moveSelectedPinned(PinnedMoveAction.DOWN));
        miMoveTop.setOnAction(e -> moveSelectedPinned(PinnedMoveAction.TOP));
        miMoveBottom.setOnAction(e -> moveSelectedPinned(PinnedMoveAction.BOTTOM));
        movePinnedMenu.getItems().setAll(miMoveUp, miMoveDown, miMoveTop, miMoveBottom);
        miDelete.setOnAction(e -> deleteSelected());
        ctxMenu.getItems().addAll(
                miPaste,
                miCopy,
                miTypeAction,
                miPin,
                new SeparatorMenuItem(),
                miRename,
                miClearTitle,
                movePinnedMenu,
                new SeparatorMenuItem(),
                miDelete
        );

        toastHide.setOnFinished(e -> {
            toast.setVisible(false);
            toast.setManaged(false);
        });

        reloadNow("");
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
            setFilterState(scope, currentTypeFilter, true);
        });

        ObservableList<ContentTypeOption> typeOptions = FXCollections.observableArrayList();
        typeOptions.add(new ContentTypeOption(null, "All types"));
        for (ClipContentType type : ClipContentType.values()) {
            typeOptions.add(new ContentTypeOption(type, type.label()));
        }

        typeFilterCombo.setItems(typeOptions);
        typeFilterCombo.getSelectionModel().selectFirst();
        typeFilterCombo.setFocusTraversable(false);
        typeFilterCombo.setPrefWidth(126);
        typeFilterCombo.setMinWidth(126);
        typeFilterCombo.getStyleClass().add("filter-type-combo");
        typeFilterCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (filterUiSync) return;
            ClipContentType type = newValue == null ? null : newValue.type();
            setFilterState(currentScope, type, true);
        });

        resetFiltersBtn.setFocusTraversable(false);
        resetFiltersBtn.getStyleClass().add("filter-reset");
        resetFiltersBtn.setOnAction(e -> setFilterState(ClipViewScope.ALL, null, true));

        updateFilterControlState();
    }

    private void configureScopeToggle(ToggleButton button, ClipViewScope scope) {
        button.setToggleGroup(scopeFilterGroup);
        button.setUserData(scope);
        button.setFocusTraversable(false);
        button.getStyleClass().add("filter-toggle");
    }

    private void setFilterState(
            ClipViewScope scope,
            ClipContentType contentType,
            boolean reload
    ) {
        ClipViewScope effectiveScope = scope == null ? ClipViewScope.ALL : scope;
        boolean changed = currentScope != effectiveScope || currentTypeFilter != contentType;

        currentScope = effectiveScope;
        currentTypeFilter = contentType;

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
        filterUiSync = false;

        updateFilterControlState();
        updateEmptyStateText();

        if (reload && changed) {
            reloadNow(searchField.getText());
        }
    }

    private void updateFilterControlState() {
        boolean active = currentScope != ClipViewScope.ALL || currentTypeFilter != null;
        resetFiltersBtn.setVisible(active);
        resetFiltersBtn.setManaged(active);
    }

    public void enableWindowPersistence(io.xseries.xclip.config.ConfigService configService,
                                        io.xseries.xclip.config.Config config) {
        this.configService = configService;
        this.config = (config != null) ? config : io.xseries.xclip.config.Config.defaults();
        this.uiClipLimit = this.config.uiClipLimit();

        // initial size from config (до первого show можно поставить)
        stage.setWidth(this.config.windowW());
        stage.setHeight(this.config.windowH());

        // debounce handler
        windowSaveDebounce.setOnFinished(e -> persistWindowStateNow());

        // listeners
        stage.xProperty().addListener((o, ov, nv) -> scheduleWindowPersist());
        stage.yProperty().addListener((o, ov, nv) -> scheduleWindowPersist());
        stage.widthProperty().addListener((o, ov, nv) -> scheduleWindowPersist());
        stage.heightProperty().addListener((o, ov, nv) -> scheduleWindowPersist());
        stage.maximizedProperty().addListener((o, ov, nv) -> {
            if (!nv) {
                // когда выходим из maximize — текущие bounds снова “normal”
                lastNormalX = stage.getX();
                lastNormalY = stage.getY();
                lastNormalW = stage.getWidth();
                lastNormalH = stage.getHeight();
                scheduleWindowPersist();
            } else {
                // перед уходом в maximize — зафиксировать нормальные bounds (если уже есть)
                if (!Double.isFinite(lastNormalW) || lastNormalW <= 0) {
                    lastNormalX = stage.getX();
                    lastNormalY = stage.getY();
                    lastNormalW = stage.getWidth();
                    lastNormalH = stage.getHeight();
                }
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
    private void scheduleWindowPersist() {
        if (configService == null || config == null) return;
        if (!stage.isShowing() || stage.isIconified()) return;
        windowSaveDebounce.playFromStart();
    }

    private void persistWindowStateNow() {
        if (configService == null || config == null) return;

        // не пишем мусор, когда окно не в нормальном показе
        if (!stage.isShowing() || stage.isIconified()) return;

        boolean maximized = stage.isMaximized();

        double x = stage.getX();
        double y = stage.getY();
        double w = stage.getWidth();
        double h = stage.getHeight();

        // защита от мусора
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(w) || !Double.isFinite(h)) return;
        if (w <= 0 || h <= 0) return;

        // Если окно maximized — сохраняем "normal bounds" (то, что было до maximize),
        // иначе при старте setWidth/setHeight даст "почти fullscreen", но не true maximized.
        if (maximized) {
            if (lastNormalW > 0 && lastNormalH > 0
                    && Double.isFinite(lastNormalX) && Double.isFinite(lastNormalY)
                    && Double.isFinite(lastNormalW) && Double.isFinite(lastNormalH)) {
                x = lastNormalX;
                y = lastNormalY;
                w = lastNormalW;
                h = lastNormalH;
            }
        } else {
            // Обновляем нормальные bounds в обычном режиме (актуальные)
            lastNormalX = x;
            lastNormalY = y;
            lastNormalW = w;
            lastNormalH = h;
        }

        // не сохраняем, если ничего не изменилось (с учётом maximize флага)
        if (config.windowX() == x &&
            config.windowY() == y &&
            config.windowW() == w &&
            config.windowH() == h &&
            config.windowMaximized() == maximized) {
            return;
        }

        io.xseries.xclip.config.Config updated = config.withWindowState(x, y, w, h, maximized);
        this.config = updated;

        // важно: persist(), не save()
        configService.persist(updated);
    }

    private void applyWindowStateOrFallback() {
        if (configService == null || config == null) {
            positionNearMouse();
            return;
        }

        // размер применяем всегда
        stage.setWidth(config.windowW());
        stage.setHeight(config.windowH());

        if (config.hasWindowPos() && isOnSomeScreen(config.windowX(), config.windowY(), config.windowW(), config.windowH())) {
            stage.setX(config.windowX());
            stage.setY(config.windowY());
        } else {
            positionNearMouse();
        }
    }

    private boolean isOnSomeScreen(double x, double y, double w, double h) {
        try {
            var screens = Screen.getScreensForRectangle(x, y, w, h);
            return screens != null && !screens.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // v1.1: called from TrayController to reflect paused state in UI
    public void setPaused(boolean paused) {
        this.paused = paused;
        Platform.runLater(() -> {
            pausedBadge.setVisible(paused);
            pausedBadge.setManaged(paused);
            updateEmptyStateText();
        });
    }

    private void updateEmptyStateText() {
        if (paused) {
            emptyStateLabel.setText("Paused. Clipboard capture is turned off.\nResume capturing from the tray menu.");
            return;
        }

        String q = currentQueryRaw == null ? "" : currentQueryRaw.trim();
        boolean filtersActive = currentScope != ClipViewScope.ALL || currentTypeFilter != null;

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
            WindowsTitleBar.applyDarkTitleBar(stage);
        }

        if (stage.isIconified()) {
            stage.setIconified(false);
        }

        if (first) {
            applyWindowStateOrFallback();

            if (config != null && config.windowMaximized()) {
                Platform.runLater(() -> stage.setMaximized(true));
            }
        }

        stage.toFront();
        stage.requestFocus();
        WindowsTitleBar.applyDarkTitleBar(stage);

        searchField.requestFocus();
        reloadNow(searchField.getText());
    }

    private void openSettings() {
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
        ctxMenu.hide();
        stage.hide();
    }

    public void shutdown() {
        pasteService.close();
        dbExec.shutdownNow();
        debounceExec.shutdownNow();
    }

    private void showToast(String msg) {
        toastHide.stop();
        toast.setText(msg);
        toast.setVisible(true);
        toast.setManaged(true);
        toastHide.playFromStart();
    }

    private void debounceReload() {
        String q = searchField.getText();
        MultiSelectionSnapshot snap = MultiSelectionSnapshot.capture(listView, items, selectionAnchorIndex);
        if (pendingSearch != null) pendingSearch.cancel(false);
        pendingSearch = debounceExec.schedule(() -> reloadNow(q, snap), 150, TimeUnit.MILLISECONDS);
    }

    private void reloadNow(String q) {
        reloadNow(q, MultiSelectionSnapshot.capture(listView, items, selectionAnchorIndex));
    }

    private void reloadNow(String q, MultiSelectionSnapshot snap) {
        String query = q == null ? "" : q;
        String normQuery = query.trim();

        ClipViewScope scopeSnapshot = currentScope;
        ClipContentType typeSnapshot = currentTypeFilter;
        long requestGeneration = reloadGeneration.incrementAndGet();

        currentQueryRaw = normQuery;
        currentQueryLower = normQuery.isEmpty() ? "" : normQuery.toLowerCase(Locale.ROOT);

        Platform.runLater(this::updateEmptyStateText);

        dbExec.submit(() -> {
            int limit = Math.max(1, uiClipLimit);
            int candidateLimit = typeSnapshot == null
                    ? limit
                    : Math.max(limit, TYPE_FILTER_SCAN_LIMIT);

            List<ClipEntry> candidates = normQuery.isBlank()
                    ? dao.listLatest(candidateLimit, scopeSnapshot.favoriteFilter())
                    : dao.search(normQuery, candidateLimit, scopeSnapshot.favoriteFilter());

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

            Platform.runLater(() -> {
                if (requestGeneration != reloadGeneration.get()) return;

                items.setAll(buildRows(list));
                countLabel.setText("Clips: " + countClips(items));

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
                        Row r = items.get(i);
                        if (r instanceof ClipRow cr && ids.contains(cr.entry().id())) {
                            listView.getSelectionModel().select(i);
                            restoredAny = true;
                        }
                    }
                }

                // restore anchorIndex from anchorId (if possible)
                if (snap != null && snap.anchorId() > 0) {
                    for (int i = 0; i < items.size(); i++) {
                        Row r = items.get(i);
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

    private ObservableList<Row> buildRows(List<ClipEntry> sorted) {
        ObservableList<Row> out = FXCollections.observableArrayList();

        boolean anyPinned = sorted.stream().anyMatch(ClipEntry::favorite);
        boolean anyRecent = sorted.stream().anyMatch(e -> !e.favorite());

        if (anyPinned) {
            out.add(new SectionRow("PINNED"));
            for (ClipEntry e : sorted) {
                if (e.favorite()) out.add(new ClipRow(e));
            }
        }

        if (anyRecent) {
            out.add(new SectionRow("RECENT"));
            for (ClipEntry e : sorted) {
                if (!e.favorite()) out.add(new ClipRow(e));
            }
        }

        return out;
    }

    private int countClips(List<Row> rows) {
        int c = 0;
        for (Row r : rows) if (r instanceof ClipRow) c++;
        return c;
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
            Row r = items.get(idx);
            if (r instanceof ClipRow cr) out.add(cr.entry());
        }
        return out;
    }


    private ClipEntry getSelectedClipOrNull() {
        Row r = listView.getSelectionModel().getSelectedItem();
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
            showToast("Clipboard unavailable");
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
            showToast("Clipboard unavailable");
        }
    }

    private ClipPrimaryAction primaryActionFor(ClipEntry entry) {
        if (entry == null) return ClipPrimaryAction.NONE;
        return ClipContentActionService.primaryActionFor(contentTypeFor(entry));
    }

    private void configureTypeActionMenu(List<ClipEntry> selected) {
        ClipEntry entry = selected != null && selected.size() == 1 ? selected.get(0) : null;
        ClipPrimaryAction action = primaryActionFor(entry);

        miTypeAction.setVisible(action.available());
        miTypeAction.setDisable(!action.available());
        miTypeAction.setText(action.available() ? action.label() : "Type action");
    }

    private void performPrimaryTypeActionForSelection() {
        List<ClipEntry> selected = getSelectedClipsOrdered();
        if (selected.size() != 1) {
            showToast("Select one clip");
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
                                    () -> showToast(action == ClipPrimaryAction.COPY_FORMATTED_JSON
                                            ? "Invalid JSON"
                                            : "Nothing to copy")
                            );
            case NONE -> showToast("No type action available");
        }
    }

    private void handleExternalOpenResult(
            ExternalOpenService.OpenResult result,
            String successMessage,
            String invalidMessage,
            String failedMessage
    ) {
        if (result == null) {
            showToast(failedMessage);
            return;
        }

        switch (result) {
            case OPENED -> showToast(successMessage);
            case INVALID_INPUT -> showToast(invalidMessage);
            case NOT_FOUND -> showToast("Path not found");
            case UNSUPPORTED -> showToast("Action unavailable on this system");
            case FAILED -> showToast(failedMessage);
        }
    }

    private void toggleFavoriteSelected() {
        List<ClipEntry> selected = getSelectedClipsOrdered();
        if (selected.isEmpty()) return;

        boolean shouldPin = selected.stream().anyMatch(e -> !e.favorite());

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
                showToast((shouldPin ? "Pinned" : "Unpinned") + (selected.size() > 1 ? (" (" + selected.size() + ")") : ""));
            });
        });
    }

    private void moveSelectedPinned(PinnedMoveAction action) {
        List<ClipEntry> selected = getSelectedClipsOrdered();
        if (selected.size() != 1 || !selected.get(0).favorite()) {
            showToast("Select one pinned clip");
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
                    showToast(switch (action) {
                        case UP -> "Moved up";
                        case DOWN -> "Moved down";
                        case TOP -> "Moved to top";
                        case BOTTOM -> "Moved to bottom";
                    });
                } else {
                    showToast(switch (action) {
                        case UP, TOP -> "Already at top";
                        case DOWN, BOTTOM -> "Already at bottom";
                    });
                }
            });
        });
    }

    private void renameSelectedPinned() {
        List<ClipEntry> selected = getSelectedClipsOrdered();
        if (selected.size() != 1 || !selected.get(0).favorite()) {
            showToast("Select one pinned clip");
            return;
        }

        ClipEntry entry = selected.get(0);
        pasteService.clearTarget();
        suppressAutoHide = true;
        autoHideDelay.stop();

        TextInputDialog dialog = new TextInputDialog(entry.title() == null ? "" : entry.title());
        dialog.initOwner(stage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Rename pinned clip");
        dialog.setHeaderText("Give this pinned clip a short title");
        dialog.setContentText("Title:");

        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/ui/styles.css").toExternalForm()
        );
        dialog.getDialogPane().getStyleClass().add("x-dialog");

        TextField editor = dialog.getEditor();
        editor.setPromptText("Example: XCC release checklist");
        editor.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= PINNED_TITLE_MAX_LENGTH ? change : null
        ));

        dialog.setOnShown(ev -> {
            Object window = dialog.getDialogPane().getScene().getWindow();
            if (window instanceof Stage dialogStage) {
                WindowsTitleBar.applyDarkTitleBar(dialogStage);
            }
            editor.requestFocus();
            editor.selectAll();
        });

        dialog.setOnHidden(ev -> {
            suppressAutoHide = false;
            Platform.runLater(() -> {
                if (stage.isShowing()) {
                    stage.toFront();
                    stage.requestFocus();
                    listView.requestFocus();
                }
            });
        });

        dialog.showAndWait().ifPresent(value -> {
            String normalized = value == null ? "" : value.trim();
            String oldTitle = entry.title() == null ? "" : entry.title().trim();
            if (normalized.equals(oldTitle)) return;

            dbExec.submit(() -> {
                dao.setTitle(entry.id(), normalized);
                Platform.runLater(() -> {
                    reloadNow(searchField.getText());
                    showToast(normalized.isEmpty() ? "Title cleared" : "Title saved");
                });
            });
        });
    }

    private void clearSelectedTitle() {
        List<ClipEntry> selected = getSelectedClipsOrdered();
        if (selected.size() != 1 || !selected.get(0).favorite()) {
            showToast("Select one pinned clip");
            return;
        }

        ClipEntry entry = selected.get(0);
        if (!entry.hasTitle()) {
            showToast("No title to clear");
            return;
        }

        dbExec.submit(() -> {
            dao.setTitle(entry.id(), null);
            Platform.runLater(() -> {
                reloadNow(searchField.getText());
                showToast("Title cleared");
            });
        });
    }

    private void toggleExpandSelected() {
        // Pinned clips intentionally stay compact. Expand applies only to RECENT rows.
        java.util.List<Long> ids = new java.util.ArrayList<>();

        for (Row r : listView.getSelectionModel().getSelectedItems()) {
            if (r instanceof ClipRow cr && !cr.entry().favorite()) {
                ids.add(cr.entry().id());
            }
        }

        if (ids.isEmpty()) {
            int first = findFirstClipIndex();
            if (first < 0) return;

            Row r = items.get(first);
            if (r instanceof ClipRow cr && !cr.entry().favorite()) {
                ids.add(cr.entry().id());
            }
        }

        if (ids.isEmpty()) {
            showToast("Pinned clips stay compact");
            return;
        }

        // If any is collapsed -> expand all. Else collapse all.
        boolean expand = false;
        for (Long id : ids) {
            if (!expandedById.getOrDefault(id, false)) {
                expand = true;
                break;
            }
        }

        for (Long id : ids) {
            expandedById.put(id, expand);
        }

        listView.refresh();
    }

    private void deleteSelected() {
        List<ClipEntry> selected = getSelectedClipsOrdered();
        if (selected.isEmpty()) return;

        for (ClipEntry e : selected) {
            expandedById.remove(e.id());
            previewCache.remove(e.id());
            contentTypeCache.remove(e.id());
        }

        dbExec.submit(() -> {
            for (ClipEntry e : selected) {
                dao.deleteById(e.id());
            }
            Platform.runLater(() -> {
                reloadNow(searchField.getText());
                showToast(selected.size() == 1 ? "Deleted" : ("Deleted (" + selected.size() + ")"));
            });
        });
    }

    private void clearHistoryNonFavorites() {
        java.util.List<Long> visibleNonFavoriteIds = new java.util.ArrayList<>();

        for (Row r : items) {
            if (r instanceof ClipRow cr && !cr.entry().favorite()) {
                visibleNonFavoriteIds.add(cr.entry().id());
            }
        }

        if (visibleNonFavoriteIds.isEmpty()) {
            showToast("Nothing to clear");
            return;
        }

        suppressAutoHide = true;
        autoHideDelay.stop();

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.getDialogPane().getStylesheets().add(
                getClass().getResource("/ui/styles.css").toExternalForm()
        );
        a.getDialogPane().getStyleClass().add("x-dialog");
        a.setTitle("Clear visible history");
        a.setHeaderText("Delete visible non-pinned clips?");
        a.setContentText("This will delete only the clips currently shown in the popup.\nPinned clips are kept.");
        a.initOwner(stage);
        a.initModality(Modality.WINDOW_MODAL);
        a.setOnHidden(ev -> suppressAutoHide = false);
        a.setOnShown(ev -> {
            Object window = a.getDialogPane().getScene().getWindow();
            if (window instanceof Stage dialogStage) {
                WindowsTitleBar.applyDarkTitleBar(dialogStage);
            }
        });

        a.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;

            java.util.Set<Long> removed = new java.util.HashSet<>(visibleNonFavoriteIds);
            expandedById.keySet().removeIf(removed::contains);
            previewCache.keySet().removeIf(removed::contains);
            contentTypeCache.keySet().removeIf(removed::contains);

            dbExec.submit(() -> {
                dao.deleteByIds(visibleNonFavoriteIds);
                Platform.runLater(() -> {
                    clearSelection();
                    reloadNow(searchField.getText());
                    showToast("Cleared visible clips (" + visibleNonFavoriteIds.size() + ")");
                });
            });
        });
    }

    private void positionNearMouse() {
        Point p = MouseInfo.getPointerInfo().getLocation();

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double x = Math.min(p.getX(), screen.getMaxX() - stage.getWidth() - 12);
        double y = Math.min(p.getY(), screen.getMaxY() - stage.getHeight() - 12);

        stage.setX(Math.max(screen.getMinX() + 12, x));
        stage.setY(Math.max(screen.getMinY() + 12, y));
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
        ContentTypeCache cached = contentTypeCache.get(entry.id());

        if (cached != null && java.util.Objects.equals(cached.content(), content)) {
            return cached.type();
        }

        ClipContentType type = ClipContentClassifier.classify(content);
        contentTypeCache.put(entry.id(), new ContentTypeCache(content, type));
        return type;
    }

    private String buildExpandedPreview(String s) {
        if (s == null || s.isEmpty()) return "";

        int len = s.length();
        StringBuilder sb = new StringBuilder(Math.min(len, EXPANDED_CHAR_LIMIT + 8));

        int lines = 1;
        boolean truncated = false;

        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);

            if (sb.length() < EXPANDED_CHAR_LIMIT) {
                sb.append(ch);
            } else {
                truncated = true;
            }

            if (ch == '\n') {
                lines++;
                if (lines > EXPANDED_LINES) {
                    truncated = true;
                    break;
                }
            }
        }

        String out = sb.toString().trim();
        if (truncated && !out.endsWith("…")) out = out + "…";
        return out;
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

    private final class RowCell extends ListCell<Row> {

        // clip row UI
        private final HBox clipRoot = new HBox(12);
        private final VBox clipLeft = new VBox(2);
        private final Label timeLabel = new Label();
        private final Label typeBadge = new Label();
        private final Tooltip typeTooltip = new Tooltip();
        private final Label pinnedTitleLabel = new Label();
        private final Label pinnedPreviewLabel = new Label();
        private final Tooltip clipTooltip = new Tooltip();
        private final Hyperlink toggleLink = new Hyperlink();
        private static final PseudoClass SECTION_PC = PseudoClass.getPseudoClass("section");
        private static final PseudoClass FAVORITE_PC = PseudoClass.getPseudoClass("favorite");

        private boolean isTypeBadgeTarget(Object target) {
            if (!(target instanceof javafx.scene.Node node)) return false;

            javafx.scene.Node current = node;
            while (current != null) {
                if (current == typeBadge) return true;
                current = current.getParent();
            }
            return false;
        }

        RowCell() {
            clipLeft.setSpacing(4);
            clipLeft.setAlignment(Pos.TOP_LEFT);
            clipRoot.setAlignment(Pos.TOP_LEFT);
            clipRoot.setFillHeight(true);

            // IMPORTANT: prevent row expansion
            clipRoot.setMaxWidth(Double.MAX_VALUE);

            toggleLink.getStyleClass().add("clip-toggle");
            toggleLink.setPadding(Insets.EMPTY);

            pinnedTitleLabel.getStyleClass().add("pinned-title");
            pinnedTitleLabel.setWrapText(false);
            pinnedTitleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            pinnedTitleLabel.setMaxWidth(Double.MAX_VALUE);
            pinnedTitleLabel.setMinWidth(0);
            pinnedTitleLabel.setPrefWidth(0);

            pinnedPreviewLabel.getStyleClass().add("pinned-preview");
            pinnedPreviewLabel.setWrapText(false);
            pinnedPreviewLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            pinnedPreviewLabel.setMaxWidth(Double.MAX_VALUE);
            pinnedPreviewLabel.setMinWidth(0);
            pinnedPreviewLabel.setPrefWidth(0);

            clipTooltip.setWrapText(true);
            clipTooltip.setMaxWidth(620);
            clipTooltip.setShowDelay(Duration.millis(250));
            clipTooltip.setShowDuration(Duration.seconds(30));
            Tooltip.install(clipRoot, clipTooltip);

            // Right column (fixed width): derived content type + timestamp.
            VBox right = new VBox(5, typeBadge, timeLabel);
            right.setAlignment(Pos.TOP_RIGHT);
            right.setMinWidth(92);
            right.setPrefWidth(92);
            right.setMaxWidth(92);

            typeBadge.getStyleClass().add("clip-type-badge");
            typeBadge.setAlignment(Pos.CENTER);
            typeBadge.setMinWidth(Region.USE_PREF_SIZE);
            typeBadge.setMaxWidth(Region.USE_PREF_SIZE);
            typeTooltip.setShowDelay(Duration.millis(250));
            Tooltip.install(typeBadge, typeTooltip);

            timeLabel.getStyleClass().add("clip-time");
            timeLabel.setAlignment(Pos.TOP_RIGHT);
            timeLabel.setMaxWidth(Double.MAX_VALUE);
            timeLabel.setWrapText(false);
            // Left content expands but DOES NOT grow root
            HBox.setHgrow(clipLeft, Priority.ALWAYS);

            clipLeft.setMaxWidth(Double.MAX_VALUE);

            clipRoot.getChildren().setAll(clipLeft, right);
            addEventFilter(MouseEvent.MOUSE_PRESSED, ev -> {
                if (isEmpty()) return;
                if (ev.getButton() != MouseButton.PRIMARY) return;

                Row r = getItem();
                if (!(r instanceof ClipRow)) {
                    ev.consume();
                    return;
                }

                // Type badges own their click so they can run the safe primary action.
                if (isTypeBadgeTarget(ev.getTarget())) return;

                // Don't hijack clicks on inner controls (e.g., "More/Less" hyperlink)
                if (ev.getTarget() instanceof javafx.scene.Node n) {
                    if (n instanceof Hyperlink || n instanceof ButtonBase || n instanceof TextField) return;
                    javafx.scene.Parent p = n.getParent();
                    while (p != null) {
                        if (p instanceof Hyperlink || p instanceof ButtonBase || p instanceof TextField) return;
                        p = p.getParent();
                    }
                }

                ev.consume();                 // <-- критично: ломаем дефолтное поведение ListView
                listView.requestFocus();

                int idx = getIndex();
                MultipleSelectionModel<Row> sm = listView.getSelectionModel();

                if (ev.isShiftDown()) {
                    if (selectionAnchorIndex < 0 || selectionAnchorIndex >= items.size()) {
                        selectionAnchorIndex = idx;
                    }

                    int a = Math.min(selectionAnchorIndex, idx);
                    int b = Math.max(selectionAnchorIndex, idx);

                    sm.clearSelection();
                    for (int i = a; i <= b; i++) {
                        if (items.get(i) instanceof ClipRow) sm.select(i);
                    }
                    return;
                }

                if (ev.isControlDown()) {
                    if (sm.isSelected(idx)) sm.clearSelection(idx);
                    else sm.select(idx);

                    selectionAnchorIndex = idx;
                    return;
                }

                selectionAnchorIndex = idx;
                sm.clearAndSelect(idx);
            });

            // Double click -> direct paste (only clip rows)
            setOnMouseClicked(ev -> {
                if (isTypeBadgeTarget(ev.getTarget())) return;

                if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2) {
                    Row r = getItem();
                    if (r instanceof ClipRow cr) {
                        pasteEntry(cr.entry());
                        ev.consume();
                    }
                }
            });

            // FIX: right-click selects the row before menu actions
            setOnContextMenuRequested(ev -> {
                if (isEmpty()) return;

                Row r = getItem();
                if (!(r instanceof ClipRow)) {
                    ctxMenu.hide();
                    return;
                }

                // Force selection to the clicked cell
                int idx = getIndex();
                if (!listView.getSelectionModel().isSelected(idx)) {
                    selectionAnchorIndex = idx;
                    listView.getSelectionModel().clearAndSelect(idx);
                }

                miPaste.setDisable(false);
                miCopy.setDisable(false);
                miPin.setDisable(false);

                List<ClipEntry> selected = getSelectedClipsOrdered();
                configureTypeActionMenu(selected);
                boolean singlePinned = selected.size() == 1 && selected.get(0).favorite();
                miRename.setDisable(!singlePinned);
                miClearTitle.setDisable(!singlePinned || !selected.get(0).hasTitle());
                movePinnedMenu.setDisable(!singlePinned);

                miDelete.setDisable(false);

                ctxMenu.show(this, ev.getScreenX(), ev.getScreenY());
                ev.consume();
            });
        }


        @Override
        protected void updateItem(Row item, boolean empty) {
            super.updateItem(item, empty);
            pseudoClassStateChanged(SECTION_PC, false);
            pseudoClassStateChanged(FAVORITE_PC, false);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setDisable(false);
                setMouseTransparent(false);
                return;
            }

            if (item instanceof SectionRow sr) {
                pseudoClassStateChanged(SECTION_PC, true);

                Label lbl = new Label(sr.title());
                lbl.getStyleClass().add("section-row");
                lbl.setMaxWidth(Double.MAX_VALUE);

                if ("RECENT".equalsIgnoreCase(sr.title())) {
                    Separator sep = new Separator();
                    sep.getStyleClass().add("section-separator");

                    VBox box = new VBox(8, sep, lbl);
                    box.setFillWidth(true);
                    box.setMaxWidth(Double.MAX_VALUE);

                    setText(null);
                    setGraphic(box);
                } else {
                    setText(null);
                    setGraphic(lbl);
                }

                setDisable(false);
                setMouseTransparent(false);
                setFocusTraversable(false);
                return;
            }

            // Clip row
            setDisable(false);
            setFocusTraversable(true);
            setMouseTransparent(false);

            ClipEntry ce = ((ClipRow) item).entry();
            pseudoClassStateChanged(FAVORITE_PC, ce.favorite());

            long id = ce.id();
            String full = (ce.content() == null) ? "" : ce.content();
            clipTooltip.setText(buildTooltipText(ce));

            ClipContentType contentType = contentTypeFor(ce);
            typeBadge.setText(contentType.label());
            typeBadge.getStyleClass().setAll(
                    "clip-type-badge",
                    "clip-type-" + contentType.cssClass()
            );

            ClipPrimaryAction primaryAction = ClipContentActionService.primaryActionFor(contentType);
            if (primaryAction.available()) {
                typeBadge.getStyleClass().add("clip-type-actionable");
                typeBadge.setCursor(Cursor.HAND);
                typeTooltip.setText(primaryAction.label() + " — click badge");
                typeBadge.setOnMouseClicked(ev -> {
                    if (ev.getButton() != MouseButton.PRIMARY || ev.getClickCount() != 1) return;

                    Row current = getItem();
                    if (!(current instanceof ClipRow currentClip)) return;

                    int idx = getIndex();
                    if (idx >= 0 && idx < items.size()) {
                        selectionAnchorIndex = idx;
                        listView.getSelectionModel().clearAndSelect(idx);
                    }

                    performPrimaryTypeAction(currentClip.entry());
                    ev.consume();
                });
            } else {
                typeBadge.setCursor(Cursor.DEFAULT);
                typeTooltip.setText("Detected content type: " + contentType.label());
                typeBadge.setOnMouseClicked(null);
            }

            pinnedTitleLabel.getStyleClass().remove("pinned-title-match");
            pinnedPreviewLabel.getStyleClass().remove("pinned-preview-match");

            // Pinned clips are intentionally compact:
            // - one line when no custom title exists;
            // - title + one-line content preview when a title exists.
            if (ce.favorite()) {
                String customTitle = ce.hasTitle() ? ce.title().trim() : null;
                String contentPreview = compactSingleLine(full, PINNED_COMPACT_CHAR_LIMIT);
                String primary = customTitle != null ? customTitle : contentPreview;

                if (primary.isBlank()) {
                    primary = "(empty clip)";
                }

                pinnedTitleLabel.setText("★ " + primary);

                boolean hasCustomTitle = customTitle != null;
                pinnedPreviewLabel.setManaged(hasCustomTitle);
                pinnedPreviewLabel.setVisible(hasCustomTitle);
                pinnedPreviewLabel.setText(hasCustomTitle ? contentPreview : "");

                String q = currentQueryLower;
                if (q != null && !q.isEmpty()) {
                    boolean titleMatch = primary.toLowerCase(Locale.ROOT).contains(q);
                    boolean contentMatch = full.toLowerCase(Locale.ROOT).contains(q);

                    if (titleMatch) {
                        pinnedTitleLabel.getStyleClass().add("pinned-title-match");
                    }
                    if (hasCustomTitle && contentMatch) {
                        pinnedPreviewLabel.getStyleClass().add("pinned-preview-match");
                    }
                }

                if (hasCustomTitle) {
                    clipLeft.getChildren().setAll(pinnedTitleLabel, pinnedPreviewLabel);
                } else {
                    clipLeft.getChildren().setAll(pinnedTitleLabel);
                }

                toggleLink.setManaged(false);
                toggleLink.setVisible(false);
                toggleLink.setOnAction(null);

                timeLabel.setText(formatTime(ce.createdAt()));
                setText(null);
                setGraphic(clipRoot);
                return;
            }

            boolean expanded = expandedById.getOrDefault(id, false);
            PreviewData pd = getPreviewData(id, full);
            boolean needsToggle = pd.needsToggle();
            String shown = expanded ? buildExpandedPreview(full) : pd.preview();

            // Recent content (with optional highlight)
            String q = currentQueryLower;
            if (q != null && !q.isEmpty()) {
                String shownLower = shown.toLowerCase(Locale.ROOT);
                if (shownLower.contains(q)) {
                    TextFlow tf = buildHighlightedText("", shown, q);
                    tf.getStyleClass().add("clip-content");
                    clipLeft.getChildren().setAll(tf, toggleLink);
                } else {
                    Label lbl = new Label(shown);
                    lbl.setWrapText(true);
                    lbl.setMaxWidth(Double.MAX_VALUE);
                    lbl.setMinWidth(0);
                    lbl.setPrefWidth(0);
                    lbl.getStyleClass().add("clip-content");
                    clipLeft.getChildren().setAll(lbl, toggleLink);
                }
            } else {
                Label lbl = new Label(shown);
                lbl.setWrapText(true);
                lbl.setMaxWidth(Double.MAX_VALUE);
                lbl.setMinWidth(0);
                lbl.setPrefWidth(0);
                lbl.getStyleClass().add("clip-content");
                clipLeft.getChildren().setAll(lbl, toggleLink);
            }

            // Right time column
            timeLabel.setText(formatTime(ce.createdAt()));

            // Toggle link
            toggleLink.setManaged(needsToggle);
            toggleLink.setVisible(needsToggle);
            if (needsToggle) {
                toggleLink.setText(expanded ? "Less" : "More");
                toggleLink.setOnAction(ev -> {
                    expandedById.put(id, !expanded);
                    listView.refresh();
                    ev.consume();
                });
            } else {
                toggleLink.setOnAction(null);
            }

            setText(null);
            setGraphic(clipRoot);
        }

        private String compactSingleLine(String value, int maxChars) {
            if (value == null || value.isEmpty()) return "";

            int limit = Math.max(1, maxChars);
            StringBuilder out = new StringBuilder(Math.min(value.length(), limit + 1));
            boolean pendingSpace = false;
            boolean truncated = false;

            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);

                if (Character.isWhitespace(ch)) {
                    pendingSpace = out.length() > 0;
                    continue;
                }

                if (pendingSpace && out.length() < limit) {
                    out.append(' ');
                }
                pendingSpace = false;

                if (out.length() >= limit) {
                    truncated = true;
                    break;
                }

                out.append(ch);
            }

            String result = out.toString().trim();
            if (truncated && !result.endsWith("…")) {
                result = result + "…";
            }
            return result;
        }

        private String buildTooltipText(ClipEntry entry) {
            String content = entry.content() == null ? "" : entry.content();
            boolean truncated = content.length() > TOOLTIP_CHAR_LIMIT;
            String body = truncated
                    ? content.substring(0, TOOLTIP_CHAR_LIMIT).trim() + "…"
                    : content;

            if (entry.hasTitle()) {
                return entry.title().trim() + "\n\n" + body;
            }
            return body;
        }

        private String formatTime(long epochMs) {
            if (epochMs <= 0) return "";

            ZonedDateTime zdt = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault());
            LocalDate d = zdt.toLocalDate();
            LocalDate now = LocalDate.now(zdt.getZone());

            DateTimeFormatter fmt = d.equals(now)
                    ? DateTimeFormatter.ofPattern("HH:mm")
                    : DateTimeFormatter.ofPattern("dd.MM HH:mm");

            return fmt.format(zdt);
        }

        private TextFlow buildHighlightedText(String prefix, String content, String queryLower) {
            TextFlow flow = new TextFlow();

            flow.setMaxWidth(Double.MAX_VALUE);
            flow.setPrefWidth(0);
            flow.setMinWidth(0);
            flow.setLineSpacing(2);

            if (prefix != null && !prefix.isEmpty()) {
                Text p = new Text(prefix);
                p.getStyleClass().add("clip-star");
                p.setFill(javafx.scene.paint.Color.web("#F5C542"));
                flow.getChildren().add(p);
            }

            if (content == null || content.isEmpty()) {
                return flow;
            }

            if (queryLower == null || queryLower.isEmpty()) {
                flow.getChildren().add(normalClipText(content));
                return flow;
            }

            String lower = content.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf(queryLower);

            if (idx < 0) {
                flow.getChildren().add(normalClipText(content));
                return flow;
            }

            if (idx > 0) {
                flow.getChildren().add(normalClipText(content.substring(0, idx)));
            }

            int end = Math.min(idx + queryLower.length(), content.length());

            Text match = new Text(content.substring(idx, end));
            match.getStyleClass().add("clip-highlight");
            match.setFill(javafx.scene.paint.Color.web("#F5C542"));
            flow.getChildren().add(match);

            if (end < content.length()) {
                flow.getChildren().add(normalClipText(content.substring(end)));
            }

            return flow;
        }

        private Text normalClipText(String text) {
            Text t = new Text(text == null ? "" : text);
            t.getStyleClass().add("clip-text");
            t.setFill(javafx.scene.paint.Color.web("#EEF2F8"));
            return t;
        }
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
            Row r = items.get(i);
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
        selectedLabel.setText(has ? ("Selected: " + n) : "");

        // Buttons
        pasteBtnRef.setText(has ? ("Paste (" + n + ")") : "Paste");
        copyBtnRef.setText(has ? ("Copy (" + n + ")") : "Copy");
        delBtnRef.setText(has ? ("Delete (" + n + ")") : "Delete");

        if (!has) {
            favBtnRef.setText("★");
            return;
        }

        boolean shouldPin = selected.stream().anyMatch(e -> !e.favorite());
        favBtnRef.setText(shouldPin ? ("Pin (" + n + ")") : ("Unpin (" + n + ")"));
    }

    private void selectAllClips() {
        MultipleSelectionModel<Row> sm = listView.getSelectionModel();
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
        MultipleSelectionModel<Row> sm = listView.getSelectionModel();
        sm.clearSelection();

        boolean inSection = false;
        int firstIndex = -1;

        for (int i = 0; i < items.size(); i++) {
            Row r = items.get(i);

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
        MultipleSelectionModel<Row> sm = listView.getSelectionModel();

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
