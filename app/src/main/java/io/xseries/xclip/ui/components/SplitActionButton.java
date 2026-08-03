
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.components;

import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.util.Objects;

/**
 * Compact two-part action control: the main button performs the default action,
 * while the chevron exposes explicit Paste/Copy-only alternatives.
 */
public final class SplitActionButton extends HBox {

    private final Button mainButton;
    private final Button menuButton;
    private final ContextMenu menu = new ContextMenu();

    public SplitActionButton(
            Button mainButton,
            Runnable pasteAction,
            Runnable copyOnlyAction
    ) {
        this.mainButton = Objects.requireNonNull(mainButton, "mainButton");
        Objects.requireNonNull(pasteAction, "pasteAction");
        Objects.requireNonNull(copyOnlyAction, "copyOnlyAction");

        this.mainButton.getStyleClass().add("split-action-main");

        menuButton = new Button();
        menuButton.setGraphic(SvgIcon.of(UiIcon.CHEVRON_DOWN, 12, "split-chevron-icon"));
        menuButton.setAccessibleText("Paste options");
        menuButton.setAccessibleHelp("Open a menu with Paste and Copy only actions.");
        menuButton.setTooltip(new Tooltip("Paste options"));
        menuButton.setFocusTraversable(true);
        menuButton.getStyleClass().addAll(
                "action-btn",
                "action-primary",
                "split-action-menu"
        );

        MenuItem pasteItem = new MenuItem(
                "Paste",
                SvgIcon.of(UiIcon.CLIPBOARD_PASTE, 14, "menu-item-icon")
        );
        pasteItem.setOnAction(event -> pasteAction.run());

        MenuItem copyItem = new MenuItem(
                "Copy only",
                SvgIcon.of(UiIcon.COPY, 14, "menu-item-icon")
        );
        copyItem.setOnAction(event -> copyOnlyAction.run());

        menu.getItems().setAll(pasteItem, copyItem);
        menuButton.setOnAction(event -> {
            if (menu.isShowing()) {
                menu.hide();
            } else {
                menu.show(menuButton, Side.TOP, 0, -2);
            }
        });

        setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        getStyleClass().add("split-action-button");
        getChildren().setAll(this.mainButton, menuButton);
    }

    public Button mainButton() {
        return mainButton;
    }

    public Button menuButton() {
        return menuButton;
    }

}
