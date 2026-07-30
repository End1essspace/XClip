/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.domain.model.ClipPrimaryAction;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import java.util.List;
import java.util.Objects;

/**
 * Owns construction, state updates, and dispatch for the popup context menu.
 *
 * PopupWindow remains the source of selection and business actions. This class
 * only translates the current selection into menu visibility/enabled state and
 * forwards user commands through the explicit Actions contract.
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

    private final MenuItem pasteItem = new MenuItem("Paste");
    private final MenuItem copyItem = new MenuItem("Copy");
    private final MenuItem typeActionItem = new MenuItem();
    private final MenuItem pinItem = new MenuItem("Pin / Unpin");
    private final MenuItem renameItem = new MenuItem("Rename pinned clip…");
    private final MenuItem clearTitleItem = new MenuItem("Clear title");
    private final Menu movePinnedMenu = new Menu("Move pinned clip");
    private final MenuItem moveUpItem = new MenuItem("Move up");
    private final MenuItem moveDownItem = new MenuItem("Move down");
    private final MenuItem moveTopItem = new MenuItem("Move to top");
    private final MenuItem moveBottomItem = new MenuItem("Move to bottom");
    private final MenuItem deleteItem = new MenuItem("Delete");

    public PopupActionsMenu(Actions actions) {
        this.actions = Objects.requireNonNull(actions, "actions");

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
        if (owner == null || selected == null || selected.isEmpty()) {
            hide();
            return;
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

        boolean singlePinned = single != null && single.favorite();
        renameItem.setDisable(!singlePinned);
        clearTitleItem.setDisable(!singlePinned || !single.hasTitle());
        movePinnedMenu.setDisable(!singlePinned);

        contextMenu.show(owner, screenX, screenY);
    }

    public void hide() {
        contextMenu.hide();
    }
}
