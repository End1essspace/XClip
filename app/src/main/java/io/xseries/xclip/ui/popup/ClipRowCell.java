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
import io.xseries.xclip.ui.popup.PopupRow.ClipRow;
import io.xseries.xclip.ui.popup.PopupRow.SectionRow;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
 * Reusable list cell for both section rows and clipboard-entry rows.
 *
 * The cell owns rendering and pointer interaction details. PopupWindow remains
 * the controller for selection state, actions, filtering, persistence, and the
 * shared context menu.
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
    private static final int TOOLTIP_CHAR_LIMIT = 2_000;

    private final Controller controller;

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

    public ClipRowCell(Controller controller) {
        this.controller = java.util.Objects.requireNonNull(controller, "controller");

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

            PopupRow r = getItem();
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

            ev.consume();                 // <-- critical: keep custom ListView selection behavior
            controller.requestListFocus();
            controller.selectFromPrimaryPointer(
                    getIndex(),
                    ev.isShiftDown(),
                    ev.isControlDown()
            );

        });

        // Double click -> direct paste (only clip rows)
        setOnMouseClicked(ev -> {
            if (isTypeBadgeTarget(ev.getTarget())) return;

            if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2) {
                PopupRow r = getItem();
                if (r instanceof ClipRow cr) {
                    controller.pasteEntry(cr.entry());
                    ev.consume();
                }
            }
        });

        // Right-click selects the row before menu actions.
        setOnContextMenuRequested(ev -> {
            if (isEmpty()) return;

            PopupRow r = getItem();
            if (!(r instanceof ClipRow)) {
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

        ClipContentType contentType = controller.contentTypeFor(ce);
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

            String q = controller.currentQueryLower();
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

        boolean expanded = controller.isExpanded(id);
        PreviewData pd = controller.previewData(id, full);
        boolean needsToggle = pd.needsToggle();
        String shown = expanded ? controller.expandedPreview(full) : pd.preview();

        // Recent content (with optional highlight)
        String q = controller.currentQueryLower();
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
                controller.setExpanded(id, !expanded);
                controller.refreshList();
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
