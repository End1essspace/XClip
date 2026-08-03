/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipTag;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Compact, responsive advanced-search assistance rendered directly below the
 * search field. It never executes queries; accepted suggestions only replace
 * the current token and return focus to the owning text field.
 */
public final class SearchAssistBar extends VBox {

    private final FlowPane chipPane = new FlowPane(Orientation.HORIZONTAL, 5, 5);
    private final FlowPane detailPane = new FlowPane(Orientation.HORIZONTAL, 6, 5);
    private final Label messageLabel = new Label();
    private final List<Button> suggestionButtons = new ArrayList<>();

    private final Consumer<SearchUiModel.AppliedQuery> onSuggestionAccepted;
    private final Runnable onReturnToSearch;
    private Runnable onSuggestionFocusExited = () -> {};

    public SearchAssistBar(
            Consumer<SearchUiModel.AppliedQuery> onSuggestionAccepted,
            Runnable onReturnToSearch
    ) {
        this.onSuggestionAccepted = Objects.requireNonNull(
                onSuggestionAccepted,
                "onSuggestionAccepted"
        );
        this.onReturnToSearch = Objects.requireNonNullElse(
                onReturnToSearch,
                () -> {}
        );

        setSpacing(5);
        setPadding(new Insets(1, 0, 0, 0));
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("search-assist");

        chipPane.setAlignment(Pos.CENTER_LEFT);
        chipPane.setPrefWrapLength(620);
        chipPane.getStyleClass().add("search-chip-pane");

        detailPane.setAlignment(Pos.CENTER_LEFT);
        detailPane.setPrefWrapLength(720);
        detailPane.getStyleClass().add("search-detail-pane");

        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(Double.MAX_VALUE);
        messageLabel.getStyleClass().add("search-assist-message");

        getChildren().setAll(chipPane, detailPane);
        setVisible(false);
        setManaged(false);
    }

    public void update(
            String rawQuery,
            int caretPosition,
            List<ClipTag> availableTags,
            boolean focused
    ) {
        SearchUiModel.State state = SearchUiModel.build(
                rawQuery,
                caretPosition,
                availableTags,
                focused
        );

        rebuildChips(state);
        rebuildDetails(rawQuery, state);

        setVisible(state.visible());
        setManaged(state.visible());
        setAccessibleText(accessibleSummary(state));
    }

    public boolean focusFirstSuggestion() {
        if (suggestionButtons.isEmpty()) return false;
        suggestionButtons.get(0).requestFocus();
        return true;
    }

    public boolean ownsFocusTarget(Node node) {
        return node != null && suggestionButtons.contains(node);
    }

    public boolean hasFocusedSuggestion() {
        return suggestionButtons.stream().anyMatch(Button::isFocused);
    }

    public void setOnSuggestionFocusExited(Runnable callback) {
        onSuggestionFocusExited = Objects.requireNonNullElse(callback, () -> {});
    }

    private void rebuildChips(SearchUiModel.State state) {
        chipPane.getChildren().clear();

        for (SearchUiModel.OperatorChip chip : state.chips()) {
            Label label = new Label(chip.text());
            label.setAccessibleText(
                    (chip.negated() ? "Excluded " : "Active ")
                            + chip.kind().name().toLowerCase()
                            + " operator "
                            + chip.text()
            );
            label.setTooltip(new Tooltip(
                    "Active search operator. Edit the search field to remove it."
            ));
            label.getStyleClass().addAll(
                    "search-query-chip",
                    switch (chip.kind()) {
                        case TYPE -> "search-query-chip-type";
                        case SCOPE -> "search-query-chip-scope";
                        case TAG -> "search-query-chip-tag";
                    }
            );
            if (chip.negated()) {
                label.getStyleClass().add("search-query-chip-negative");
            }
            chipPane.getChildren().add(label);
        }

        if (state.hiddenChipCount() > 0) {
            Label overflow = new Label("+" + state.hiddenChipCount());
            overflow.setAccessibleText(
                    state.hiddenChipCount() + " additional active search operators"
            );
            overflow.getStyleClass().addAll(
                    "search-query-chip",
                    "search-query-chip-overflow"
            );
            chipPane.getChildren().add(overflow);
        }

        boolean visible = !chipPane.getChildren().isEmpty();
        chipPane.setVisible(visible);
        chipPane.setManaged(visible);
    }

    private void rebuildDetails(String rawQuery, SearchUiModel.State state) {
        detailPane.getChildren().clear();
        suggestionButtons.clear();

        if (!state.message().isBlank()) {
            messageLabel.setText(state.message());
            messageLabel.getStyleClass().removeAll(
                    "search-assist-hint",
                    "search-assist-error"
            );
            messageLabel.getStyleClass().add(
                    state.messageTone() == SearchUiModel.MessageTone.ERROR
                            ? "search-assist-error"
                            : "search-assist-hint"
            );
            messageLabel.setAccessibleText(state.message());
            detailPane.getChildren().add(messageLabel);
        }

        for (SearchUiModel.Suggestion suggestion : state.suggestions()) {
            Button button = new Button(suggestion.label());
            button.setAccessibleText("Insert search suggestion " + suggestion.label());
            button.setAccessibleHelp(
                    "Replace the current search token with " + suggestion.replacement()
            );
            button.setTooltip(new Tooltip("Insert " + suggestion.replacement()));
            button.setFocusTraversable(true);
            button.getStyleClass().add("search-suggestion");
            button.setOnAction(event ->
                    onSuggestionAccepted.accept(suggestion.applyTo(rawQuery))
            );
            button.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (!wasFocused || isFocused) return;
                Platform.runLater(() -> {
                    if (!hasFocusedSuggestion()) {
                        onSuggestionFocusExited.run();
                    }
                });
            });
            button.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    event.consume();
                    onReturnToSearch.run();
                    return;
                }
                if (event.getCode() == KeyCode.RIGHT || event.getCode() == KeyCode.DOWN) {
                    event.consume();
                    moveSuggestionFocus(button, false);
                    return;
                }
                if (event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.UP) {
                    event.consume();
                    moveSuggestionFocus(button, true);
                }
            });
            suggestionButtons.add(button);
            detailPane.getChildren().add(button);
        }

        boolean visible = !detailPane.getChildren().isEmpty();
        detailPane.setVisible(visible);
        detailPane.setManaged(visible);
    }

    private void moveSuggestionFocus(Button current, boolean backwards) {
        int index = suggestionButtons.indexOf(current);
        if (index < 0 || suggestionButtons.isEmpty()) return;

        int next = backwards
                ? Math.floorMod(index - 1, suggestionButtons.size())
                : (index + 1) % suggestionButtons.size();
        suggestionButtons.get(next).requestFocus();
    }

    private String accessibleSummary(SearchUiModel.State state) {
        StringBuilder summary = new StringBuilder("Advanced search assistance.");
        if (!state.chips().isEmpty()) {
            summary.append(' ')
                    .append(state.chips().size() + state.hiddenChipCount())
                    .append(" active operators.");
        }
        if (!state.message().isBlank()) {
            summary.append(' ').append(state.message());
        }
        if (!state.suggestions().isEmpty()) {
            summary.append(' ')
                    .append(state.suggestions().size())
                    .append(" suggestions available. Press Down from Search to focus them.");
        }
        return summary.toString();
    }
}
