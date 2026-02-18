package io.xseries.xclip.ui;

import io.xseries.xclip.data.dao.ClipEntryDao;
import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.domain.service.ClipService;
import io.xseries.xclip.system.clipboard.ClipboardAccess;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
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
    private static final int LIMIT = 200;

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
    private Button copyBtnRef;
    private Button favBtnRef;
    private Button delBtnRef;

    // Preview behavior (prevents "text wall" in list)
    private static final int PREVIEW_LINES = 3;
    private static final int PREVIEW_CHAR_LIMIT = 320;

    // Expanded preview (still bounded to protect UI)
    private static final int EXPANDED_LINES = 200;
    private static final int EXPANDED_CHAR_LIMIT = 100_000;

    private final Stage stage;
    private final TextField searchField = new TextField();
    private final ListView<Row> listView = new ListView<>();
    private final ObservableList<Row> items = FXCollections.observableArrayList();

    private final ClipEntryDao dao;
    private final ClipboardAccess clipboard;
    private final ClipService clipService;

    private final Runnable onOpenSettings;

    // per-entry expand state for "More/Less"
    private final Map<Long, Boolean> expandedById = new HashMap<>();
    // Cache for preview/needsToggle to avoid split("\\R") per cell repaint
    private record PreviewData(boolean needsToggle, String preview) {}
    private final Map<Long, PreviewData> previewCache = new HashMap<>();

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

    private final PauseTransition autoHideDelay = new PauseTransition(Duration.millis(160));

    // -----------------------
    // List rows
    // -----------------------
    private sealed interface Row permits SectionRow, ClipRow {}

    private record SectionRow(String title) implements Row {}

    private record ClipRow(ClipEntry entry) implements Row {}


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
    private final MenuItem miCopy = new MenuItem("Copy");
    private final MenuItem miPin = new MenuItem("Pin / Unpin");
    private final MenuItem miDelete = new MenuItem("Delete");

    public PopupWindow(ClipEntryDao dao, ClipboardAccess clipboard, ClipService clipService) {
        this(dao, clipboard, clipService, () -> {});
    }
    
    public PopupWindow(ClipEntryDao dao, ClipboardAccess clipboard, ClipService clipService, Runnable onOpenSettings) {
        this.dao = dao;
        this.clipboard = clipboard;
        this.clipService = clipService;
        this.onOpenSettings = (onOpenSettings != null) ? onOpenSettings : (() -> {});

        stage = new Stage(StageStyle.UTILITY);
        stage.setTitle("XClip " + io.xseries.xclip.AppVersion.VERSION);
        stage.setAlwaysOnTop(true);
        stage.setResizable(true);
        stage.setMinWidth(420);
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
        pausedBadge.setStyle("""
                -fx-background-color: rgba(255,153,0,0.20);
                -fx-text-fill: #ff9900;
                -fx-background-radius: 999;
                -fx-border-radius: 999;
                -fx-border-color: rgba(255,153,0,0.35);
                """);

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

        Selection:
        • Ctrl+Click     Toggle item selection
        • Shift+Click    Select range
        • Ctrl+A         Select all clips (current list)
        • Ctrl+Shift+A   Select RECENT section
        • Ctrl+I         Invert selection
        • Ctrl+D         Clear selection

        Actions:
        • Enter          Copy selection
        • Ctrl+C         Copy selection
        • Ctrl+P         Pin / Unpin selection
        • E              Expand / Collapse selected clip (bounded preview)
        • Delete         Delete selection
        • Double-click   Copy single item
        • Right-click    Context menu (Copy / Pin / Delete)

        Window:
        • Esc            Clear selection → clear search → hide popup (in this order)
        • Ctrl+,         Settings

        Notes:
        • Pinned clips are shown in PINNED section
        • Multi-copy joins clips with new lines
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

        HBox topBar = new HBox(8, searchWrap, pausedBadge, countLabel, selectedLabel, help, settingsBtn, clearBtn);

        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);


        Button copyBtn = new Button("Copy");
        copyBtn.setOnAction(e -> copySelectedOrFirst());
        copyBtn.getStyleClass().addAll("action-btn", "action-primary");

        Button favBtn = new Button("★");
        favBtn.setOnAction(e -> toggleFavoriteSelected());
        favBtn.getStyleClass().addAll("action-btn", "action-neutral");

        Button delBtn = new Button("Delete");
        delBtn.setOnAction(e -> deleteSelected());
        delBtn.getStyleClass().addAll("action-btn", "action-danger");

        this.copyBtnRef = copyBtn;
        this.favBtnRef = favBtn;
        this.delBtnRef = delBtn;

        HBox actions = new HBox(8, copyBtn, favBtn, delBtn);

        actions.setPadding(new Insets(8));
        actions.getStyleClass().add("actions-bar");

        // Toast (overlay-style feedback inside the window)
        toast.setVisible(false);
        toast.setManaged(false);
        toast.getStyleClass().add("toast");

        BorderPane root = new BorderPane();
        root.setTop(topBar);

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
            // Expand/Collapse selected clip (UI-only, bounded)
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
                e.consume();
                copySelectedOrFirst();
                return;
            }
            if (e.getCode() == KeyCode.DELETE) {
                e.consume();
                deleteSelected();
            }
        });

        // Context menu actions
        miCopy.setOnAction(e -> copySelectedOrFirst());
        miPin.setOnAction(e -> toggleFavoriteSelected());
        miDelete.setOnAction(e -> deleteSelected());
        ctxMenu.getItems().addAll(miCopy, miPin, new SeparatorMenuItem(), miDelete);

        toastHide.setOnFinished(e -> {
            toast.setVisible(false);
            toast.setManaged(false);
        });

        reloadNow("");
    }
    public void enableWindowPersistence(io.xseries.xclip.config.ConfigService configService,
                                        io.xseries.xclip.config.Config config) {
        this.configService = configService;
        this.config = (config != null) ? config : io.xseries.xclip.config.Config.defaults();

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
        if (!q.isEmpty()) {
            emptyStateLabel.setText("No results for \"" + q + "\".\nPress Ctrl+L to clear search.");
            return;
        }

        emptyStateLabel.setText("No clips yet.\nCopy any text and it will appear here.");
    }

    public void showOrFocus() {

        boolean first = !windowStateAppliedOnce;
        windowStateAppliedOnce = true;

        if (!stage.isShowing()) {
            stage.show();
        }

        if (first) {
            applyWindowStateOrFallback();

            if (config != null && config.windowMaximized()) {
                Platform.runLater(() -> stage.setMaximized(true));
            }
        }

        stage.toFront();
        stage.requestFocus();

        searchField.requestFocus();
        reloadNow(searchField.getText());
    }


    private void openSettings() {
        this.onOpenSettings.run();
    }

    public void hide() {
        ctxMenu.hide();
        stage.hide();
    }

    public void shutdown() {
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
        currentQueryRaw = normQuery;
        currentQueryLower = normQuery.isEmpty() ? "" : normQuery.toLowerCase(Locale.ROOT);

        Platform.runLater(this::updateEmptyStateText);

        dbExec.submit(() -> {
            List<ClipEntry> list = query.isBlank()
                    ? dao.listLatest(LIMIT)
                    : dao.search(query.trim(), LIMIT);

            list.sort(
                    Comparator.comparing(ClipEntry::favorite).reversed()
                            .thenComparing(Comparator.comparingLong(ClipEntry::createdAt).reversed())
            );

            Platform.runLater(() -> {
                items.setAll(buildRows(list));
                countLabel.setText("Clips: " + countClips(items));
                
                updateEmptyStateText();

                if (items.isEmpty()) return;
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

    private void copySelectedOrFirst() {
        List<ClipEntry> selected = getSelectedClipsOrdered();

        if (!selected.isEmpty()) {
            String joined = selected.stream()
                    .map(e -> e.content() == null ? "" : e.content())
                    .collect(java.util.stream.Collectors.joining("\n"));

            clipService.markPushedByApp(joined);
            clipboard.setTextSafely(joined);
            hide();
            return;
        }

        int idx = findFirstClipIndex();
        if (idx >= 0) {
            listView.getSelectionModel().clearAndSelect(idx);
            ClipEntry sel = getSelectedClipOrNull();
            if (sel != null) copyEntry(sel);
        }
    }

    private void copyEntry(ClipEntry entry) {
        clipService.markPushedByApp(entry.content());
        clipboard.setTextSafely(entry.content());
        hide();
    }

    private void toggleFavoriteSelected() {
        List<ClipEntry> selected = getSelectedClipsOrdered();
        if (selected.isEmpty()) return;

        boolean shouldPin = selected.stream().anyMatch(e -> !e.favorite());

        dbExec.submit(() -> {
            for (ClipEntry e : selected) {
                dao.setFavorite(e.id(), shouldPin);
            }
            Platform.runLater(() -> {
                reloadNow(searchField.getText());
                showToast((shouldPin ? "Pinned" : "Unpinned") + (selected.size() > 1 ? (" (" + selected.size() + ")") : ""));
            });
        });
    }
    
    private void toggleExpandSelected() {
        // toggle selected clips; if nothing selected -> toggle first clip in list
        java.util.List<Long> ids = new java.util.ArrayList<>();

        for (Row r : listView.getSelectionModel().getSelectedItems()) {
            if (r instanceof ClipRow cr) ids.add(cr.entry().id());
        }

        if (ids.isEmpty()) {
            int first = findFirstClipIndex();
            if (first < 0) return;
            Row r = items.get(first);
            if (r instanceof ClipRow cr) ids.add(cr.entry().id());
            else return;
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

        for (ClipEntry e : selected) expandedById.remove(e.id());

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
        suppressAutoHide = true;
        autoHideDelay.stop();

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Clear history");
        a.setHeaderText("Delete non-favorites?");
        a.setContentText("This will delete all clips except pinned (favorites).");
        a.initOwner(stage);
        a.initModality(Modality.WINDOW_MODAL);
        a.setOnHidden(ev -> suppressAutoHide = false);

        a.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;

            // Clear expand state (safe)
            expandedById.clear();

            dbExec.submit(() -> {
                dao.deleteAllNonFavorites();
                Platform.runLater(() -> {
                    reloadNow(searchField.getText());
                    showToast("Cleared");
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
        private final Hyperlink toggleLink = new Hyperlink();
        private static final PseudoClass SECTION_PC = PseudoClass.getPseudoClass("section");


        RowCell() {
            clipLeft.setSpacing(4);
            clipLeft.setAlignment(Pos.TOP_LEFT);
            clipRoot.setAlignment(Pos.TOP_LEFT);
            clipRoot.setFillHeight(true);

            // IMPORTANT: prevent row expansion
            clipRoot.setMaxWidth(Double.MAX_VALUE);
            
            toggleLink.getStyleClass().add("clip-toggle");
            toggleLink.setPadding(Insets.EMPTY);

            // Right column (fixed width)
            VBox right = new VBox(timeLabel);
            right.setAlignment(Pos.TOP_RIGHT);
            right.setMinWidth(86);
            right.setPrefWidth(86);
            right.setMaxWidth(86);
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
                if (!(r instanceof ClipRow)) return;

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

            // Double click -> copy (only clip rows)
            setOnMouseClicked(ev -> {
                if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2) {
                    Row r = getItem();
                    if (r instanceof ClipRow cr) {
                        copyEntry(cr.entry());
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

                miCopy.setDisable(false);
                miPin.setDisable(false);
                miDelete.setDisable(false);

                ctxMenu.show(this, ev.getScreenX(), ev.getScreenY());
                ev.consume();
            });
        }

        @Override
        protected void updateItem(Row item, boolean empty) {
            super.updateItem(item, empty);
            pseudoClassStateChanged(SECTION_PC, false);

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

                setText(null);
                setGraphic(lbl);

                setDisable(true);          // важно
                setMouseTransparent(false);
                setFocusTraversable(false);
                return;
            }

            // Clip row
            setDisable(false);
            setFocusTraversable(true);
            setMouseTransparent(false);

            ClipEntry ce = ((ClipRow) item).entry();

            long id = ce.id();
            boolean expanded = expandedById.getOrDefault(id, false);

            String prefix = ce.favorite() ? "★ " : "";
            String full = (ce.content() == null) ? "" : ce.content();

            PreviewData pd = getPreviewData(id, full);
            boolean needsToggle = pd.needsToggle();
            String shown = expanded ? buildExpandedPreview(full) : pd.preview();

            // Left content (with optional highlight)
            String q = currentQueryLower;
            if (q != null && !q.isEmpty()) {
                String shownLower = shown.toLowerCase(Locale.ROOT);
                if (shownLower.contains(q)) {
                    TextFlow tf = buildHighlightedText(prefix, shown, q);
                    tf.getStyleClass().add("clip-content");
                    clipLeft.getChildren().setAll(tf, toggleLink);
                } else {
                    Label lbl = new Label(prefix + shown);
                    lbl.setWrapText(true);
                    lbl.setMaxWidth(Double.MAX_VALUE);
                    lbl.setMinWidth(0);
                    lbl.setPrefWidth(0);
                    lbl.getStyleClass().add("clip-content");
                    clipLeft.getChildren().setAll(lbl, toggleLink);
                }
            } else {
                Label lbl = new Label(prefix + shown);
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
                    // force re-render (cheap enough; only happens on click)
                    listView.refresh();
                    ev.consume();
                });
            } else {
                toggleLink.setOnAction(null);
            }

            setText(null);
            setGraphic(clipRoot);
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

            if (content == null || content.isEmpty()) {
                flow.getChildren().add(new Text(prefix));
                return flow;
            }

            String lower = content.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf(queryLower);

            if (idx < 0) {
                flow.getChildren().add(new Text(prefix + content));
                return flow;
            }

            if (prefix != null && !prefix.isEmpty()) {
                Text p = new Text(prefix);
                p.getStyleClass().add("clip-star");
                flow.getChildren().add(p);
            }

            if (idx > 0) flow.getChildren().add(new Text(content.substring(0, idx)));

            int end = Math.min(idx + queryLower.length(), content.length());
            Text match = new Text(content.substring(idx, end));
            // keep highlight subtle; exact color can be tuned later in CSS if desired
            match.getStyleClass().add("clip-highlight");
            flow.getChildren().add(match);

            if (end < content.length()) flow.getChildren().add(new Text(content.substring(end)));

            flow.setMaxWidth(Double.MAX_VALUE);
            flow.setPrefWidth(0);
            flow.setMinWidth(0);
            flow.setLineSpacing(2);

            return flow;
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
        if (copyBtnRef == null || favBtnRef == null || delBtnRef == null) return;

        List<ClipEntry> selected = getSelectedClipsOrdered();
        int n = selected.size();

        boolean has = n > 0;
        selectedLabel.setVisible(has);
        selectedLabel.setManaged(has);
        selectedLabel.setText(has ? ("Selected: " + n) : "");

        // Buttons
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
