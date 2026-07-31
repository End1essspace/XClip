
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipPrimaryAction;
import io.xseries.xclip.domain.service.ClipContentActionService;
import io.xseries.xclip.ui.components.SvgIcon;
import io.xseries.xclip.ui.components.UiIcon;
import io.xseries.xclip.ui.popup.PopupRow.ClipRow;
import io.xseries.xclip.ui.popup.PopupRow.SectionRow;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Reusable list cell for section headers and clipboard-entry cards.
 *
 * The cell keeps one stable node tree per ListCell so ListView virtualization
 * remains intact while exposing the stronger visual hierarchy used by the new
 * XClip reference: selection, pin state, content, type, time, and overflow.
 *
 * Clipboard content is never shown in an automatic row-hover tooltip. Long
 * previews are opened only through explicit More / E interactions.
 */
public final class ClipRowCell extends ListCell<PopupRow> {

    public record PreviewData(boolean needsToggle, String preview) {}

    public interface Controller {
        void requestListFocus();
        void selectFromPrimaryPointer(int index, boolean shiftDown, boolean controlDown);
        void selectExclusively(int index);
        void pasteEntry(ClipEntry entry);
        void performPrimaryTypeAction(ClipEntry entry);
        void showContextMenu(Node owner, int index, double screenX, double screenY);
        void hideContextMenu();
        ClipContentType contentTypeFor(ClipEntry entry);
        String currentQueryLower();
        boolean isExpanded(long id);
        void setExpanded(long id, boolean expanded);
        PreviewData previewData(long id, String fullContent);
        String expandedPreview(String fullContent);
        void refreshList();
    }

    private static final int PINNED_COMPACT_CHAR_LIMIT = 220;
    private static final PseudoClass SECTION_PC = PseudoClass.getPseudoClass("section");
    private static final PseudoClass FAVORITE_PC = PseudoClass.getPseudoClass("favorite");
    private static final PseudoClass COMPACT_PC = PseudoClass.getPseudoClass("compact");
    private static final PseudoClass TWO_LINE_PC = PseudoClass.getPseudoClass("two-line");
    private static final PseudoClass EXPANDED_PC = PseudoClass.getPseudoClass("expanded");

    private final Controller controller;

    // Section row UI
    private final HBox sectionRoot = new HBox(6);
    private final StackPane sectionIcon = new StackPane();
    private final Label sectionTitle = new Label();
    private final Label sectionCount = new Label();

    // Clip row UI
    private final HBox clipRoot = new HBox(9);
    private final HBox leading = new HBox(6);
    private final StackPane selectionIndicator = new StackPane();
    private final StackPane pinIndicator = new StackPane();
    private final Region pinAccent = new Region();
    private final VBox clipLeft = new VBox(3);
    private final HBox metadata = new HBox(10);
    private final Label timeLabel = new Label();
    private final Label typeBadge = new Label();
    private final Tooltip typeTooltip = new Tooltip();
    private final Button collapseButton = new Button("Collapse");
    private final Button moreButton = new Button();
    private final Label pinnedTitleLabel = new Label();
    private final Label pinnedPreviewLabel = new Label();
    private final Hyperlink toggleLink = new Hyperlink();

    public ClipRowCell(Controller controller) {
        this.controller = java.util.Objects.requireNonNull(controller, "controller");

        configureSectionRoot();
        configureClipRoot();
        configurePointerBehavior();

        selectedProperty().addListener((obs, oldValue, newValue) -> updateSelectionIndicator());
        hoverProperty().addListener((obs, oldValue, newValue) -> updateSelectionIndicator());
    }

    private void configureSectionRoot() {
        sectionRoot.setAlignment(Pos.CENTER_LEFT);
        sectionRoot.setMaxWidth(Double.MAX_VALUE);
        sectionRoot.getStyleClass().add("section-row-root");

        sectionTitle.getStyleClass().add("section-row");
        sectionCount.getStyleClass().add("section-count");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        sectionRoot.getChildren().setAll(sectionIcon, sectionTitle, sectionCount, spacer);
    }

    private void configureClipRoot() {
        clipRoot.setAlignment(Pos.CENTER_LEFT);
        clipRoot.setFillHeight(true);
        clipRoot.setMaxWidth(Double.MAX_VALUE);
        clipRoot.getStyleClass().add("clip-row-card");

        pinAccent.getStyleClass().add("pin-accent");
        pinAccent.setMinWidth(2);
        pinAccent.setPrefWidth(2);
        pinAccent.setMaxWidth(2);
        pinAccent.setMaxHeight(Double.MAX_VALUE);

        SvgIcon checkGlyph = SvgIcon.of(UiIcon.CHECK, 11, "selection-check-icon");
        selectionIndicator.getChildren().add(checkGlyph);
        selectionIndicator.getStyleClass().add("selection-indicator");
        selectionIndicator.setMinSize(18, 18);
        selectionIndicator.setPrefSize(18, 18);
        selectionIndicator.setMaxSize(18, 18);

        SvgIcon pinGlyph = SvgIcon.of(UiIcon.PIN, 12, "row-pin-icon");
        pinIndicator.getChildren().add(pinGlyph);
        pinIndicator.getStyleClass().add("row-pin-indicator");
        pinIndicator.setMinSize(18, 18);
        pinIndicator.setPrefSize(18, 18);
        pinIndicator.setMaxSize(18, 18);

        leading.setAlignment(Pos.CENTER_LEFT);
        leading.setMinWidth(48);
        leading.setPrefWidth(48);
        leading.setMaxWidth(48);
        leading.getChildren().setAll(selectionIndicator, pinIndicator);

        clipLeft.setSpacing(3);
        clipLeft.setAlignment(Pos.CENTER_LEFT);
        clipLeft.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(clipLeft, Priority.ALWAYS);

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

        typeBadge.getStyleClass().add("clip-type-badge");
        typeBadge.setAlignment(Pos.CENTER);
        typeBadge.setMinWidth(Region.USE_PREF_SIZE);
        typeBadge.setMaxWidth(Region.USE_PREF_SIZE);
        typeTooltip.setShowDelay(Duration.millis(250));
        Tooltip.install(typeBadge, typeTooltip);

        timeLabel.getStyleClass().add("clip-time");
        timeLabel.setAlignment(Pos.CENTER_RIGHT);
        timeLabel.setWrapText(false);
        timeLabel.setMinWidth(76);

        SvgIcon collapseIcon = SvgIcon.of(UiIcon.CHEVRON_DOWN, 12, "row-collapse-icon");
        collapseIcon.setRotate(180);
        collapseButton.setGraphic(collapseIcon);
        collapseButton.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        collapseButton.setFocusTraversable(false);
        collapseButton.setAccessibleText("Collapse expanded preview");
        collapseButton.setTooltip(new Tooltip("Collapse preview (E or Esc)"));
        collapseButton.getStyleClass().add("row-collapse-button");
        collapseButton.setManaged(false);
        collapseButton.setVisible(false);
        collapseButton.setOnAction(event -> collapseCurrentPreview(event));

        moreButton.setGraphic(SvgIcon.of(UiIcon.ELLIPSIS_VERTICAL, 13, "row-more-icon"));
        moreButton.setFocusTraversable(false);
        moreButton.setAccessibleText("More actions");
        moreButton.setTooltip(new Tooltip("More actions"));
        moreButton.getStyleClass().add("row-more-button");
        moreButton.setOnAction(event -> showMoreMenu());

        metadata.setAlignment(Pos.CENTER_RIGHT);
        metadata.setMinWidth(208);
        metadata.setPrefWidth(208);
        metadata.setMaxWidth(208);
        metadata.getChildren().setAll(collapseButton, typeBadge, timeLabel, moreButton);

        clipRoot.getChildren().setAll(pinAccent, leading, clipLeft, metadata);
    }

    private void configurePointerBehavior() {
        addEventFilter(MouseEvent.MOUSE_PRESSED, ev -> {
            if (ev.getButton() != MouseButton.PRIMARY) return;

            controller.hideContextMenu();
            if (isEmpty()) return;

            PopupRow row = getItem();
            if (!(row instanceof ClipRow)) {
                ev.consume();
                return;
            }

            if (isTypeBadgeTarget(ev.getTarget())) return;

            if (isInteractiveControl(ev.getTarget())) return;

            ev.consume();
            controller.requestListFocus();
            controller.selectFromPrimaryPointer(
                    getIndex(),
                    ev.isShiftDown(),
                    ev.isControlDown()
            );
        });

        setOnMouseClicked(ev -> {
            if (isTypeBadgeTarget(ev.getTarget()) || isInteractiveControl(ev.getTarget())) return;

            if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2) {
                PopupRow row = getItem();
                if (row instanceof ClipRow clipRow) {
                    controller.pasteEntry(clipRow.entry());
                    ev.consume();
                }
            }
        });

        setOnContextMenuRequested(ev -> {
            if (isEmpty()) return;

            PopupRow row = getItem();
            if (!(row instanceof ClipRow)) {
                controller.hideContextMenu();
                return;
            }

            controller.showContextMenu(
                    this,
                    getIndex(),
                    ev.getScreenX(),
                    ev.getScreenY()
            );
            ev.consume();
        });
    }

    @Override
    protected void updateItem(PopupRow item, boolean empty) {
        super.updateItem(item, empty);
        pseudoClassStateChanged(SECTION_PC, false);
        pseudoClassStateChanged(FAVORITE_PC, false);
        pseudoClassStateChanged(COMPACT_PC, false);
        pseudoClassStateChanged(TWO_LINE_PC, false);
        pseudoClassStateChanged(EXPANDED_PC, false);
        setCollapseButtonVisible(false);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setDisable(false);
            setMouseTransparent(false);
            return;
        }

        if (item instanceof SectionRow sectionRow) {
            renderSection(sectionRow);
            return;
        }

        renderClip(((ClipRow) item).entry());
    }

    private void renderSection(SectionRow row) {
        pseudoClassStateChanged(SECTION_PC, true);

        boolean pinned = "PINNED".equalsIgnoreCase(row.title());
        sectionIcon.getChildren().setAll(SvgIcon.of(
                pinned ? UiIcon.PIN : UiIcon.ROTATE_CCW_CLOCK,
                13,
                "section-icon",
                pinned ? "section-icon-pinned" : "section-icon-recent"
        ));
        sectionTitle.setText(row.title());
        sectionCount.setText(Integer.toString(row.count()));

        setText(null);
        setGraphic(sectionRoot);
        setDisable(false);
        setMouseTransparent(false);
        setFocusTraversable(false);
    }

    private void renderClip(ClipEntry entry) {
        setDisable(false);
        setFocusTraversable(true);
        setMouseTransparent(false);

        pseudoClassStateChanged(FAVORITE_PC, entry.favorite());
        pinAccent.setVisible(entry.favorite());
        pinAccent.setManaged(true);
        pinIndicator.setVisible(entry.favorite());
        pinIndicator.setOpacity(entry.favorite() ? 0.82 : 0.0);
        updateSelectionIndicator();

        long id = entry.id();
        String full = entry.content() == null ? "" : entry.content();
        ClipContentType contentType = controller.contentTypeFor(entry);
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

                PopupRow current = getItem();
                if (!(current instanceof ClipRow currentClip)) return;

                controller.selectExclusively(getIndex());
                controller.performPrimaryTypeAction(currentClip.entry());
                ev.consume();
            });
        } else {
            typeBadge.setCursor(Cursor.DEFAULT);
            typeTooltip.setText("Detected content type: " + contentType.label());
            typeBadge.setOnMouseClicked(null);
        }

        pinnedTitleLabel.getStyleClass().remove("pinned-title-match");
        pinnedPreviewLabel.getStyleClass().remove("pinned-preview-match");

        if (entry.favorite()) {
            renderPinned(entry, full);
        } else {
            renderRecent(entry, full, id);
        }

        timeLabel.setText(formatTime(entry.createdAt()));
        setText(null);
        setGraphic(clipRoot);
    }

    private void renderPinned(ClipEntry entry, String full) {
        String customTitle = entry.hasTitle() ? entry.title().trim() : null;
        String contentPreview = compactSingleLine(full, PINNED_COMPACT_CHAR_LIMIT);
        String primary = customTitle != null ? customTitle : contentPreview;

        if (primary.isBlank()) primary = "(empty clip)";

        pinnedTitleLabel.setText(primary);

        boolean hasCustomTitle = customTitle != null;
        pseudoClassStateChanged(COMPACT_PC, !hasCustomTitle);
        pseudoClassStateChanged(TWO_LINE_PC, hasCustomTitle);

        pinnedTitleLabel.getStyleClass().remove("pinned-title-plain");
        if (!hasCustomTitle) {
            pinnedTitleLabel.getStyleClass().add("pinned-title-plain");
        }

        pinnedPreviewLabel.setManaged(hasCustomTitle);
        pinnedPreviewLabel.setVisible(hasCustomTitle);
        pinnedPreviewLabel.setText(hasCustomTitle ? contentPreview : "");

        String query = controller.currentQueryLower();
        if (query != null && !query.isEmpty()) {
            boolean titleMatch = primary.toLowerCase(Locale.ROOT).contains(query);
            boolean contentMatch = full.toLowerCase(Locale.ROOT).contains(query);

            if (titleMatch) pinnedTitleLabel.getStyleClass().add("pinned-title-match");
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
        setCollapseButtonVisible(false);
    }

    private void renderRecent(ClipEntry entry, String full, long id) {
        boolean expanded = controller.isExpanded(id);
        PreviewData previewData = controller.previewData(id, full);
        boolean needsToggle = previewData.needsToggle();
        String shown = expanded ? controller.expandedPreview(full) : previewData.preview();

        boolean compact = !expanded
                && !needsToggle
                && full.indexOf('\n') < 0
                && full.indexOf('\r') < 0;
        pseudoClassStateChanged(COMPACT_PC, compact);
        pseudoClassStateChanged(TWO_LINE_PC, !compact);
        pseudoClassStateChanged(EXPANDED_PC, expanded);
        setCollapseButtonVisible(expanded);

        String query = controller.currentQueryLower();
        Node contentNode;
        if (query != null && !query.isEmpty()
                && shown.toLowerCase(Locale.ROOT).contains(query)) {
            TextFlow flow = buildHighlightedText(shown, query);
            flow.getStyleClass().add("clip-content");
            contentNode = flow;
        } else {
            Label label = new Label(shown);
            label.setWrapText(true);
            label.setMaxWidth(Double.MAX_VALUE);
            label.setMinWidth(0);
            label.setPrefWidth(0);
            label.getStyleClass().add("clip-content");
            contentNode = label;
        }

        clipLeft.getChildren().setAll(contentNode, toggleLink);

        boolean showExpandLink = needsToggle && !expanded;
        toggleLink.setManaged(showExpandLink);
        toggleLink.setVisible(showExpandLink);
        if (showExpandLink) {
            toggleLink.setText("More");
            toggleLink.setOnAction(event -> {
                controller.hideContextMenu();
                controller.setExpanded(id, true);
                controller.refreshList();
                event.consume();
            });
        } else {
            toggleLink.setOnAction(null);
        }
    }

    private void setCollapseButtonVisible(boolean visible) {
        collapseButton.setManaged(visible);
        collapseButton.setVisible(visible);

        double width = visible ? 286 : 208;
        metadata.setMinWidth(width);
        metadata.setPrefWidth(width);
        metadata.setMaxWidth(width);
    }

    private void collapseCurrentPreview(javafx.event.ActionEvent event) {
        PopupRow row = getItem();
        if (!(row instanceof ClipRow clipRow)) return;

        controller.hideContextMenu();
        controller.setExpanded(clipRow.entry().id(), false);
        controller.refreshList();
        controller.requestListFocus();
        event.consume();
    }

    private void showMoreMenu() {
        PopupRow row = getItem();
        if (!(row instanceof ClipRow)) return;

        Bounds screenBounds = moreButton.localToScreen(moreButton.getBoundsInLocal());
        if (screenBounds == null) return;

        controller.showContextMenu(
                moreButton,
                getIndex(),
                screenBounds.getMinX(),
                screenBounds.getMaxY() + 4
        );
    }

    private void updateSelectionIndicator() {
        boolean clip = getItem() instanceof ClipRow;
        boolean visible = clip && (isSelected() || isHover());
        selectionIndicator.setOpacity(visible ? 1.0 : 0.0);
        selectionIndicator.pseudoClassStateChanged(
                PseudoClass.getPseudoClass("checked"),
                clip && isSelected()
        );
    }

    private boolean isTypeBadgeTarget(Object target) {
        if (!(target instanceof Node node)) return false;

        Node current = node;
        while (current != null) {
            if (current == typeBadge) return true;
            current = current.getParent();
        }
        return false;
    }

    private boolean isInteractiveControl(Object target) {
        if (!(target instanceof Node node)) return false;

        Node current = node;
        while (current != null) {
            if (current instanceof Hyperlink
                    || current instanceof ButtonBase
                    || current instanceof TextField) {
                return true;
            }
            current = current.getParent();
        }
        return false;
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

            if (pendingSpace && out.length() < limit) out.append(' ');
            pendingSpace = false;

            if (out.length() >= limit) {
                truncated = true;
                break;
            }

            out.append(ch);
        }

        String result = out.toString().trim();
        if (truncated && !result.endsWith("…")) result += "…";
        return result;
    }

    private String formatTime(long epochMs) {
        if (epochMs <= 0) return "";

        ZonedDateTime value = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault());
        LocalDate date = value.toLocalDate();
        LocalDate today = LocalDate.now(value.getZone());

        DateTimeFormatter formatter = date.equals(today)
                ? DateTimeFormatter.ofPattern("HH:mm")
                : DateTimeFormatter.ofPattern("dd.MM HH:mm");

        return formatter.format(value);
    }

    private TextFlow buildHighlightedText(String content, String queryLower) {
        TextFlow flow = new TextFlow();
        flow.setMaxWidth(Double.MAX_VALUE);
        flow.setPrefWidth(0);
        flow.setMinWidth(0);
        flow.setLineSpacing(2);

        if (content == null || content.isEmpty()) return flow;
        if (queryLower == null || queryLower.isEmpty()) {
            flow.getChildren().add(normalClipText(content));
            return flow;
        }

        String lower = content.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(queryLower);
        if (index < 0) {
            flow.getChildren().add(normalClipText(content));
            return flow;
        }

        if (index > 0) flow.getChildren().add(normalClipText(content.substring(0, index)));

        int end = Math.min(index + queryLower.length(), content.length());
        Text match = new Text(content.substring(index, end));
        match.getStyleClass().add("clip-highlight");
        flow.getChildren().add(match);

        if (end < content.length()) {
            flow.getChildren().add(normalClipText(content.substring(end)));
        }

        return flow;
    }

    private Text normalClipText(String text) {
        Text value = new Text(text == null ? "" : text);
        value.getStyleClass().add("clip-text");
        return value;
    }
}

