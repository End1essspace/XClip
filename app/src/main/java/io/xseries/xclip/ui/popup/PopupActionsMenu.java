/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.domain.model.ClipPrimaryAction;
import io.xseries.xclip.ui.components.SvgIcon;
import io.xseries.xclip.ui.components.UiIcon;
import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import java.util.List;
import java.util.Objects;

/**
 * Owns construction, visual state, and dispatch for the popup context menu.
 */
public final class PopupActionsMenu {

    public interface Actions {
        void paste();
        void copy();
        void performPrimaryTypeAction();
        void editTags();
        void manageTags();
        boolean tagsAvailable();
        void toggleFavorite();
        void renamePinned();
        void clearTitle();
        void movePinnedUp();
        void movePinnedDown();
        void movePinnedToTop();
        void movePinnedToBottom();
        void delete();
        ClipPrimaryAction primaryActionFor(ClipEntry entry);
    }

    private final Actions actions;
    private final ContextMenu contextMenu = new ContextMenu();

    private final MenuItem pasteItem = item("Paste", UiIcon.CLIPBOARD_PASTE);
    private final MenuItem copyItem = item("Copy", UiIcon.COPY);
    private final MenuItem typeActionItem = new MenuItem();
    private final MenuItem tagsItem = item("Tags…", UiIcon.TAGS);
    private final MenuItem manageTagsItem = item("Manage tags…", UiIcon.TAG);
    private final MenuItem pinItem = item("Pin selected", UiIcon.PIN);
    private final MenuItem renameItem = item("Rename pinned clip…", UiIcon.PENCIL);
    private final MenuItem clearTitleItem = item("Clear title", UiIcon.X);
    private final Menu movePinnedMenu = new Menu(
            "Move pinned clip",
            SvgIcon.of(UiIcon.LIST, 13, "menu-item-icon")
    );
    private final MenuItem moveUpItem = new MenuItem("Move up");
    private final MenuItem moveDownItem = new MenuItem("Move down");
    private final MenuItem moveTopItem = new MenuItem("Move to top");
    private final MenuItem moveBottomItem = new MenuItem("Move to bottom");
    private final MenuItem deleteItem = item("Delete", UiIcon.TRASH_2);

    public PopupActionsMenu(Actions actions) {
        this.actions = Objects.requireNonNull(actions, "actions");

        contextMenu.setAutoHide(true);
        contextMenu.setHideOnEscape(true);
        contextMenu.setConsumeAutoHidingEvents(false);
        contextMenu.setOnShown(event -> requestKeyboardFocus());

        pasteItem.setOnAction(e -> actions.paste());
        copyItem.setOnAction(e -> actions.copy());

        typeActionItem.setOnAction(e -> actions.performPrimaryTypeAction());
        typeActionItem.setVisible(false);

        tagsItem.setOnAction(e -> actions.editTags());
        manageTagsItem.setOnAction(e -> actions.manageTags());

        pinItem.setOnAction(e -> actions.toggleFavorite());
        renameItem.setOnAction(e -> actions.renamePinned());
        clearTitleItem.setOnAction(e -> actions.clearTitle());

        moveUpItem.setOnAction(e -> actions.movePinnedUp());
        moveDownItem.setOnAction(e -> actions.movePinnedDown());
        moveTopItem.setOnAction(e -> actions.movePinnedToTop());
        moveBottomItem.setOnAction(e -> actions.movePinnedToBottom());
        movePinnedMenu.getItems().setAll(
                moveUpItem,
                moveDownItem,
                moveTopItem,
                moveBottomItem
        );

        deleteItem.setOnAction(e -> actions.delete());
        deleteItem.getStyleClass().add("menu-item-danger");

        contextMenu.getItems().addAll(
                pasteItem,
                copyItem,
                typeActionItem,
                tagsItem,
                manageTagsItem,
                pinItem,
                new SeparatorMenuItem(),
                renameItem,
                clearTitleItem,
                movePinnedMenu,
                new SeparatorMenuItem(),
                deleteItem
        );
    }

    public void show(
            Node owner,
            double screenX,
            double screenY,
            List<ClipEntry> selected
    ) {
        hide();
        if (!prepare(owner, selected)) return;
        contextMenu.show(owner, screenX, screenY);
    }

    public void showAbove(Node owner, List<ClipEntry> selected) {
        hide();
        if (!prepare(owner, selected)) return;
        contextMenu.show(owner, Side.TOP, 0, -4);
    }

    private boolean prepare(Node owner, List<ClipEntry> selected) {
        if (owner == null) {
            hide();
            return false;
        }

        List<ClipEntry> safeSelection = selected == null ? List.of() : selected;
        boolean hasSelection = !safeSelection.isEmpty();
        boolean tagsAvailable = actions.tagsAvailable();

        pasteItem.setDisable(!hasSelection);
        copyItem.setDisable(!hasSelection);
        tagsItem.setDisable(!hasSelection || !tagsAvailable);
        manageTagsItem.setDisable(!tagsAvailable);
        pinItem.setDisable(!hasSelection);
        deleteItem.setDisable(!hasSelection);

        ClipEntry single = safeSelection.size() == 1 ? safeSelection.get(0) : null;
        ClipPrimaryAction primaryAction = actions.primaryActionFor(single);
        if (primaryAction == null) {
            primaryAction = ClipPrimaryAction.NONE;
        }

        boolean typeActionAvailable = primaryAction.available();
        typeActionItem.setVisible(typeActionAvailable);
        typeActionItem.setDisable(!typeActionAvailable);
        typeActionItem.setText(typeActionAvailable
                ? primaryAction.label()
                : "Type action");
        typeActionItem.setGraphic(typeActionAvailable
                ? SvgIcon.of(iconFor(primaryAction), 13, "menu-item-icon", "menu-type-icon")
                : null);

        boolean shouldPin = !hasSelection || safeSelection.stream().anyMatch(entry -> !entry.favorite());
        pinItem.setText(shouldPin ? "Pin selected" : "Unpin selected");
        pinItem.setGraphic(SvgIcon.of(
                shouldPin ? UiIcon.PIN : UiIcon.PIN_OFF,
                13,
                "menu-item-icon",
                shouldPin ? "menu-pin-icon" : "menu-unpin-icon"
        ));

        boolean singlePinned = single != null && single.favorite();
        renameItem.setDisable(!singlePinned);
        clearTitleItem.setDisable(!singlePinned || !single.hasTitle());
        movePinnedMenu.setDisable(!singlePinned);
        return true;
    }

    public boolean isShowing() {
        return contextMenu.isShowing();
    }

    public void hide() {
        contextMenu.hide();
    }

    private void requestKeyboardFocus() {
        Platform.runLater(() -> {
            if (!contextMenu.isShowing()) return;
            if (contextMenu.getSkin() == null || contextMenu.getSkin().getNode() == null) return;

            Node menuNode = contextMenu.getSkin().getNode();
            menuNode.setAccessibleText("XClip actions menu");
            menuNode.setAccessibleHelp("Use Up and Down to navigate, Enter to activate, and Escape to close.");
            menuNode.requestFocus();
        });
    }

    private static MenuItem item(String text, UiIcon icon) {
        return new MenuItem(
                text,
                SvgIcon.of(icon, 13, "menu-item-icon")
        );
    }

    private static UiIcon iconFor(ClipPrimaryAction action) {
        return switch (action) {
            case OPEN_URL -> UiIcon.EXTERNAL_LINK;
            case REVEAL_PATH -> UiIcon.FOLDER_OPEN;
            case COPY_FORMATTED_JSON -> UiIcon.BRACES;
            case COPY_CODE -> UiIcon.CODE_XML;
            case COPY_COMMAND -> UiIcon.TERMINAL;
            case NONE -> UiIcon.ZAP;
        };
    }
}




