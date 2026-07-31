
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.ui.components.SvgIcon;
import io.xseries.xclip.ui.components.UiIcon;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Objects;

/** A complete, scroll-safe Quick Help popover anchored to the header button. */
public final class QuickHelpPopover {

    private final ContextMenu menu = new ContextMenu();

    public QuickHelpPopover() {
        menu.setAutoHide(true);
        menu.setHideOnEscape(true);
        menu.setConsumeAutoHidingEvents(false);
        menu.getStyleClass().add("quick-help-menu");

        CustomMenuItem item = new CustomMenuItem(buildContent(), false);
        item.setHideOnClick(false);
        menu.getItems().setAll(item);
    }

    public boolean isShowing() {
        return menu.isShowing();
    }

    public void toggle(Node owner) {
        Objects.requireNonNull(owner, "owner");
        if (isShowing()) {
            hide();
            return;
        }

        menu.show(owner, Side.BOTTOM, 0, 6);
    }

    public void hide() {
        menu.hide();
    }

    private Node buildContent() {
        VBox body = new VBox(12);
        body.getStyleClass().add("quick-help-content");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("quick-help-header");

        Label title = new Label("XClip — Quick Help");
        title.getStyleClass().add("quick-help-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button close = new Button();
        close.setGraphic(SvgIcon.of(UiIcon.X, 13, "quick-help-close-icon"));
        close.setFocusTraversable(false);
        close.setAccessibleText("Close quick help");
        close.getStyleClass().add("quick-help-close");
        close.setOnAction(event -> hide());

        header.getChildren().setAll(title, spacer, close);
        body.getChildren().add(header);

        for (QuickHelpContent.Section section : QuickHelpContent.sections()) {
            VBox sectionBox = new VBox(5);
            sectionBox.getStyleClass().add("quick-help-section");

            Label sectionTitle = new Label(section.title());
            sectionTitle.getStyleClass().add("quick-help-section-title");
            sectionBox.getChildren().add(sectionTitle);

            for (QuickHelpContent.Shortcut shortcut : section.shortcuts()) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("quick-help-row");

                Label keys = new Label(shortcut.keys());
                keys.getStyleClass().add("quick-help-key");
                keys.setMinWidth(126);
                keys.setPrefWidth(126);

                Label description = new Label(shortcut.description());
                description.getStyleClass().add("quick-help-description");
                description.setWrapText(true);
                HBox.setHgrow(description, Priority.ALWAYS);

                row.getChildren().setAll(keys, description);
                sectionBox.getChildren().add(row);
            }

            body.getChildren().add(sectionBox);
        }

        Label footer = new Label("Click outside or press Esc to close");
        footer.getStyleClass().add("quick-help-footer");
        body.getChildren().add(footer);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPrefViewportWidth(420);
        scroll.setPrefViewportHeight(450);
        scroll.setMaxHeight(520);
        scroll.getStyleClass().add("quick-help-scroll");
        return scroll;
    }
}

