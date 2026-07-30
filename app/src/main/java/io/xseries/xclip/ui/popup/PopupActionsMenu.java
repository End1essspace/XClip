/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.domain.model.ClipPrimaryAction;
import io.xseries.xclip.ui.components.SvgIcon;
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

    private final MenuItem pasteItem = item("Paste", "clipboard-paste");
    private final MenuItem copyItem = item("Copy", "copy");
    private final MenuItem typeActionItem = new MenuItem();
    private final MenuItem pinItem = item("Pin selected", "pin");
    private final MenuItem renameItem = item("Rename pinned clip…", "pencil");
    private final MenuItem clearTitleItem = item("Clear title", "x");
    private final Menu movePinnedMenu = new Menu(
            "Move pinned clip",
            SvgIcon.of("list", 13, "menu-item-icon")
    );
    private final MenuItem moveUpItem = new MenuItem("Move up");
    private final MenuItem moveDownItem = new MenuItem("Move down");
    private final MenuItem moveTopItem = new MenuItem("Move to top");
    private final MenuItem moveBottomItem = new MenuItem("Move to bottom");
    private final MenuItem deleteItem = item("Delete", "trash-2");

    public PopupActionsMenu(Actions actions) {
        this.actions = Objects.requireNonNull(actions, "actions");

        contextMenu.setAutoHide(true);
        contextMenu.setHideOnEscape(true);
        contextMenu.setConsumeAutoHidingEvents(false);

        pasteItem.setOnAction(e -> actions.paste());
        copyItem.setOnAction(e -> actions.copy());

        typeActionItem.setOnAction(e -> actions.performPrimaryTypeAction());
        typeActionItem.setVisible(false);

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

        contextMenu.getItems().addAll(
                pasteItem,
                copyItem,
                typeActionItem,
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
        if (owner == null || selected == null || selected.isEmpty()) {
            hide();
            return false;
        }

        pasteItem.setDisable(false);
        copyItem.setDisable(false);
        pinItem.setDisable(false);
        deleteItem.setDisable(false);

        ClipEntry single = selected.size() == 1 ? selected.get(0) : null;
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

        boolean shouldPin = selected.stream().anyMatch(entry -> !entry.favorite());
        pinItem.setText(shouldPin ? "Pin selected" : "Unpin selected");
        pinItem.setGraphic(SvgIcon.of(
                shouldPin ? "pin" : "pin-off",
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

    private static MenuItem item(String text, String iconName) {
        return new MenuItem(
                text,
                SvgIcon.of(iconName, 13, "menu-item-icon")
        );
    }

    private static String iconFor(ClipPrimaryAction action) {
        return switch (action) {
            case OPEN_URL -> "external-link";
            case REVEAL_PATH -> "folder-open";
            case COPY_FORMATTED_JSON -> "braces";
            case COPY_CODE -> "code-xml";
            case COPY_COMMAND -> "terminal";
            case NONE -> "zap";
        };
    }
}
