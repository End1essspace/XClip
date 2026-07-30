/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui;

import io.xseries.xclip.ui.popup.ClipRowCell;
import io.xseries.xclip.ui.popup.ClipRowCell.PreviewData;
import io.xseries.xclip.ui.popup.PopupActionBar;
import io.xseries.xclip.ui.popup.PopupActionsMenu;
import io.xseries.xclip.ui.popup.PopupFilterBar;
import io.xseries.xclip.ui.popup.QuickHelpPopover;
import io.xseries.xclip.ui.popup.ClipPreviewPolicy;
import io.xseries.xclip.ui.popup.PopupHeader;
import io.xseries.xclip.ui.popup.PopupTitleBar;
import io.xseries.xclip.ui.popup.PopupRow;
import io.xseries.xclip.ui.popup.PopupRow.ClipRow;
import io.xseries.xclip.ui.popup.PopupRow.SectionRow;
import io.xseries.xclip.ui.popup.PopupViewState;
import io.xseries.xclip.ui.components.SplitActionButton;
import io.xseries.xclip.ui.components.SvgIcon;
import io.xseries.xclip.system.window.WindowChromeController;
import io.xseries.xclip.system.window.WindowChromeController.WindowBounds;
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
import javafx.geometry.Pos;
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
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import java.awt.MouseInfo;
import java.awt.Point;
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

    // Preview behavior (prevents "text wall" in list)
    private static final int PREVIEW_LINES = 3;
    private static final int PREVIEW_CHAR_LIMIT = 320;

    // Pinned clips remain intentionally compact even when their content is large.
    private static final int PINNED_TITLE_MAX_LENGTH = 120;
    private static final int TYPE_FILTER_SCAN_LIMIT = 5_000;

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
    private final Button resetFiltersBtn = new Button("Reset filters");

    private volatile PopupViewState viewState = PopupViewState.defaults();
    private boolean filterUiSync = false;

    private final ClipEntryDao dao;
    private final ClipboardAccess clipboard;
    private final ClipService clipService;
    private final PasteService pasteService;
    private final ExternalOpenService externalOpenService = new ExternalOpenService();

    private final Runnable onOpenSettings;
    private final Runnable onTogglePaused;

    // per-entry expand state for "More/Less"
    private final Map<Long, Boolean> expandedById = new HashMap<>();
    // Cache for preview/needsToggle to avoid split("\\R") per cell repaint
    private final Map<Long, PreviewData> previewCache = new HashMap<>();

    private record ContentTypeCache(String content, ClipContentType type) {}
    private final Map<Long, ContentTypeCache> contentTypeCache = new ConcurrentHashMap<>();

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
    private final java.util.concurrent.atomic.AtomicLong reloadGeneration =
            new java.util.concurrent.atomic.AtomicLong();

    private final PauseTransition autoHideDelay = new PauseTransition(Duration.millis(160));

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

    public PopupWindow(ClipEntryDao dao, ClipboardAccess clipboard, ClipService clipService, Runnable onOpenSettings) {
        this(
                dao,
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
        this(dao, clipboard, clipService, onOpenSettings, () -> {}, pasteService);
    }

    public PopupWindow(
            ClipEntryDao dao,
            ClipboardAccess clipboard,
            ClipService clipService,
            Runnable onOpenSettings,
            Runnable onTogglePaused,
            PasteService pasteService
    ) {
        this.dao = dao;
        this.clipboard = clipboard;
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
        stage.setMinWidth(500);
        stage.setMinHeight(300);

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

        // Empty state placeholder
        emptyStateLabel.setWrapText(true);
        emptyStateLabel.setMaxWidth(360);
        emptyStateLabel.setPadding(Insets.EMPTY);
        emptyStateLabel.getStyleClass().add("empty-state");
        listView.setPlaceholder(emptyStateLabel);
        updateEmptyStateText();

        // Clip count indicator (counts only real clips, not section rows)
        countLabel.getStyleClass().add("topbar-status");
        countLabel.setText("Clips 0");

        selectedLabel.getStyleClass().add("topbar-status");
        selectedLabel.setVisible(false);
        selectedLabel.setManaged(false);

        searchField.setPromptText("Search clips...");
        searchField.setMaxWidth(Double.MAX_VALUE);
        searchField.getStyleClass().add("search-field");

        Button clearSearchBtn = new Button();
        clearSearchBtn.setGraphic(SvgIcon.of("x", 12, "search-clear-icon"));
        clearSearchBtn.setFocusTraversable(false);
        clearSearchBtn.setAccessibleText("Clear search");
        clearSearchBtn.setTooltip(new Tooltip("Clear search"));
        clearSearchBtn.getStyleClass().add("search-clear");
        clearSearchBtn.setVisible(false);
        clearSearchBtn.setManaged(false);

        SvgIcon searchIcon = SvgIcon.of("search", 16, "search-leading-icon");
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

            if (e.getCode() == KeyCode.TAB) {
                e.consume();
                listView.requestFocus();
            }
        });

        // Help is a real, scroll-safe popover instead of a long tooltip that can be clipped.
        Button help = iconButton("circle-question-mark", "Quick help", "topbar-help");
        help.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && quickHelp.isShowing()) {
                quickHelp.hide();
                event.consume();
            }
        });
        help.setOnAction(event -> quickHelp.toggle(help));

        Button pauseBtn = new Button("Pause");
        pauseBtn.setGraphic(SvgIcon.of("pause", 15, "toolbar-icon", "pause-icon"));
        pauseBtn.setContentDisplay(ContentDisplay.LEFT);
        pauseBtn.setFocusTraversable(false);
        pauseBtn.setOnAction(e -> onTogglePaused.run());
        pauseBtn.getStyleClass().addAll("topbar-btn", "pause-button");
        this.pauseBtnRef = pauseBtn;

        Button settingsBtn = iconButton("settings", "Open settings", "topbar-settings");
        settingsBtn.setOnAction(e -> openSettings());

        Button clearBtn = new Button("Clear");
        clearBtn.setGraphic(SvgIcon.of("trash-2", 15, "toolbar-icon", "clear-icon"));
        clearBtn.setContentDisplay(ContentDisplay.LEFT);
        clearBtn.setFocusTraversable(false);
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

        searchWrap.setMinWidth(420);
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
        pasteBtn.setGraphic(SvgIcon.of("clipboard-paste", 15, "action-icon"));
        pasteBtn.setContentDisplay(ContentDisplay.LEFT);
        pasteBtn.setOnAction(e -> pasteSelectedOrFirst());
        pasteBtn.getStyleClass().addAll("action-btn", "action-primary");

        Button copyBtn = new Button("Copy");
        copyBtn.setGraphic(SvgIcon.of("copy", 15, "action-icon"));
        copyBtn.setContentDisplay(ContentDisplay.LEFT);
        copyBtn.setOnAction(e -> copySelectedOrFirst());
        copyBtn.getStyleClass().addAll("action-btn", "action-neutral");

        Button actionsBtn = new Button();
        Label actionsText = new Label("Actions");
        actionsText.getStyleClass().add("action-button-label");
        HBox actionsGraphic = new HBox(
                6,
                SvgIcon.of("zap", 15, "action-icon"),
                actionsText,
                SvgIcon.of("chevron-down", 12, "action-chevron-icon")
        );
        actionsGraphic.setAlignment(Pos.CENTER);
        actionsBtn.setGraphic(actionsGraphic);
        actionsBtn.setOnAction(e -> showActionsMenu(actionsBtn));
        actionsBtn.setAccessibleText("Actions");
        actionsBtn.setTooltip(new Tooltip("Context actions"));
        actionsBtn.getStyleClass().addAll("action-btn", "action-neutral", "actions-menu-button");

        Button favBtn = new Button("Pin / Unpin");
        favBtn.setGraphic(SvgIcon.of("pin", 15, "action-icon", "favorite-action-icon"));
        favBtn.setContentDisplay(ContentDisplay.LEFT);
        favBtn.setOnAction(e -> toggleFavoriteSelected());
        favBtn.getStyleClass().addAll("action-btn", "action-neutral", "action-state");

        Button delBtn = new Button("Delete");
        delBtn.setGraphic(SvgIcon.of("trash-2", 15, "action-icon", "danger-action-icon"));
        delBtn.setContentDisplay(ContentDisplay.LEFT);
        delBtn.setOnAction(e -> deleteSelected());
        delBtn.getStyleClass().addAll("action-btn", "action-danger");

        this.pasteBtnRef = pasteBtn;
        this.copyBtnRef = copyBtn;
        this.favBtnRef = favBtn;
        this.delBtnRef = delBtn;

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

        // Shared stylesheet (Popup + Settings)
        scene.getStylesheets().add(
                getClass().getResource("/ui/styles.css").toExternalForm()
        );

        windowChrome.installResizeSupport(
                scene,
                6.0,
                stage.getMinWidth(),
                stage.getMinHeight()
        );

        stage.setScene(scene);

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
                return;
            }

            // Minimize is a real window operation, not an auto-hide request.
            // Check on the next pulse because iconified can update after focus.
            Platform.runLater(() -> {
                if (suppressAutoHide || windowChrome.isIconified()) return;
                autoHideDelay.playFromStart();
            });
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
            if (e.isControlDown()
                    && (e.getCode() == KeyCode.F || e.getCode() == KeyCode.K)) {
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

        statusReset.setOnFinished(e -> {
            if (actionBar != null) actionBar.showHints();
        });

        reloadNow("");
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

    private Button iconButton(String iconName, String accessibleText, String extraStyleClass) {
        Button button = new Button();
        button.setGraphic(SvgIcon.of(iconName, 15, "toolbar-icon"));
        button.setFocusTraversable(false);
        button.setAccessibleText(accessibleText);
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
        if (selected.isEmpty()) return;

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
            setFilterState(scope, viewState.contentType(), true);
        });

        ObservableList<ContentTypeOption> typeOptions = FXCollections.observableArrayList();
        typeOptions.add(new ContentTypeOption(null, "All types"));
        for (ClipContentType type : ClipContentType.values()) {
            typeOptions.add(new ContentTypeOption(type, type.label()));
        }

        typeFilterCombo.setItems(typeOptions);
        typeFilterCombo.getSelectionModel().selectFirst();
        typeFilterCombo.setFocusTraversable(false);
        typeFilterCombo.setPrefWidth(190);
        typeFilterCombo.setMinWidth(160);
        typeFilterCombo.getStyleClass().add("filter-type-combo");
        typeFilterCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (filterUiSync) return;
            ClipContentType type = newValue == null ? null : newValue.type();
            setFilterState(viewState.scope(), type, true);
        });

        resetFiltersBtn.setGraphic(SvgIcon.of("rotate-ccw", 13, "filter-icon", "filter-reset-icon"));
        resetFiltersBtn.setContentDisplay(ContentDisplay.LEFT);
        resetFiltersBtn.setFocusTraversable(false);
        resetFiltersBtn.getStyleClass().add("filter-reset");
        resetFiltersBtn.setOnAction(e -> setFilterState(ClipViewScope.ALL, null, true));

        updateFilterControlState();
    }

    private void configureScopeToggle(ToggleButton button, ClipViewScope scope) {
        button.setToggleGroup(scopeFilterGroup);
        button.setUserData(scope);
        button.setFocusTraversable(false);
        button.setContentDisplay(ContentDisplay.LEFT);
        String iconName = switch (scope) {
            case ALL -> "list";
            case PINNED -> "pin";
            case RECENT -> "rotate-ccw-clock";
        };
        button.setGraphic(SvgIcon.of(iconName, 13, "filter-icon"));
        button.getStyleClass().add("filter-toggle");
    }

    private void setFilterState(
            ClipViewScope scope,
            ClipContentType contentType,
            boolean reload
    ) {
        ClipViewScope effectiveScope = scope == null ? ClipViewScope.ALL : scope;
        PopupViewState nextState = new PopupViewState(effectiveScope, contentType);
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
        filterUiSync = false;

        updateFilterControlState();
        updateEmptyStateText();

        if (reload && changed) {
            reloadNow(searchField.getText());
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
        if (configService == null || config == null) {
            positionNearMouse();
            windowChrome.captureNormalBounds();
            return;
        }

        stage.setWidth(config.windowW());
        stage.setHeight(config.windowH());

        if (config.hasWindowPos()
                && isOnSomeScreen(
                        config.windowX(),
                        config.windowY(),
                        config.windowW(),
                        config.windowH()
                )) {
            stage.setX(config.windowX());
            stage.setY(config.windowY());
        } else {
            positionNearMouse();
        }

        windowChrome.captureNormalBounds();
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
        boolean changed = this.paused != paused;
        this.paused = paused;
        Platform.runLater(() -> {
            if (pauseBtnRef != null) {
                pauseBtnRef.setText(paused ? "Resume" : "Pause");
                pauseBtnRef.setGraphic(SvgIcon.of(
                        paused ? "play" : "pause",
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
                showToast(paused ? "Capturing paused" : "Capturing resumed");
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
        pasteService.close();
        dbExec.shutdownNow();
        debounceExec.shutdownNow();
    }

    private void showToast(String msg) {
        if (actionBar == null) return;

        statusReset.stop();
        actionBar.showStatus(msg);
        statusReset.playFromStart();
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

        PopupViewState stateSnapshot = viewState;
        ClipViewScope scopeSnapshot = stateSnapshot.scope();
        ClipContentType typeSnapshot = stateSnapshot.contentType();
        long requestGeneration = reloadGeneration.incrementAndGet();

        currentQueryRaw = normQuery;
        currentQueryLower = normQuery.isEmpty() ? "" : normQuery.toLowerCase(Locale.ROOT);

        Platform.runLater(this::updateEmptyStateText);

        dbExec.submit(() -> {
            int limit = Math.max(1, uiClipLimit);
            int candidateLimit = typeSnapshot == null
                    ? limit
                    : Math.max(limit, TYPE_FILTER_SCAN_LIMIT);

            int totalClipCount = dao.countAll();

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
                int visibleClipCount = countClips(items);
                countLabel.setText("Clips " + totalClipCount);
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

    private ObservableList<PopupRow> buildRows(List<ClipEntry> sorted) {
        ObservableList<PopupRow> out = FXCollections.observableArrayList();

        int pinnedCount = 0;
        int recentCount = 0;
        for (ClipEntry entry : sorted) {
            if (entry.favorite()) pinnedCount++;
            else recentCount++;
        }

        if (pinnedCount > 0) {
            out.add(new SectionRow("PINNED", pinnedCount));
            for (ClipEntry entry : sorted) {
                if (entry.favorite()) out.add(new ClipRow(entry));
            }
        }

        if (recentCount > 0) {
            out.add(new SectionRow("RECENT", recentCount));
            for (ClipEntry entry : sorted) {
                if (!entry.favorite()) out.add(new ClipRow(entry));
            }
        }

        return out;
    }

    private int countClips(List<PopupRow> rows) {
        int c = 0;
        for (PopupRow r : rows) if (r instanceof ClipRow) c++;
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
            showToast("Pinned clips stay compact");
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
        showToast("Preview collapsed");
        return true;
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

        for (PopupRow r : items) {
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

        // The header already owns the selection count. Footer actions stay
        // visually stable and do not repeat "(N)" on every button.
        pasteBtnRef.setText("Paste");
        copyBtnRef.setText("Copy");
        delBtnRef.setText("Delete");

        if (!has) {
            favBtnRef.setText("Pin / Unpin");
            favBtnRef.setGraphic(SvgIcon.of(
                    "pin",
                    17,
                    "action-icon",
                    "favorite-action-icon"
            ));
            return;
        }

        boolean shouldPin = selected.stream().anyMatch(e -> !e.favorite());
        favBtnRef.setText(shouldPin ? "Pin" : "Unpin");
        favBtnRef.setGraphic(SvgIcon.of(
                shouldPin ? "pin" : "pin-off",
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
