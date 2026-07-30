/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.system.window.WindowChromeController;
import io.xseries.xclip.ui.components.WindowsGlyphs;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.Objects;

/**
 * Custom title bar for the undecorated popup shell.
 *
 * The bar provides normal Windows window controls while leaving the large
 * center area draggable. It deliberately contains only product identity and
 * window management; search and clipboard actions live in PopupHeader.
 */
public final class PopupTitleBar extends HBox {

    private static final double ICON_SIZE = 20.0;

    private final WindowChromeController chrome;
    private final Button maximizeButton;

    public PopupTitleBar(Stage stage, WindowChromeController chrome) {
        Objects.requireNonNull(stage, "stage");
        this.chrome = Objects.requireNonNull(chrome, "chrome");

        getStyleClass().add("popup-title-bar");
        setAlignment(Pos.CENTER_LEFT);

        HBox dragRegion = createDragRegion();
        HBox.setHgrow(dragRegion, Priority.ALWAYS);

        Button minimizeButton = createWindowButton(
                WindowsGlyphs.MINIMIZE,
                "Minimize",
                "window-minimize-button"
        );
        minimizeButton.setOnAction(event -> chrome.minimize());

        maximizeButton = createWindowButton(
                WindowsGlyphs.MAXIMIZE,
                "Maximize",
                "window-maximize-button"
        );
        maximizeButton.setOnAction(event -> chrome.toggleMaximized());

        Button closeButton = createWindowButton(
                WindowsGlyphs.CLOSE,
                "Close to tray",
                "window-close-button"
        );
        closeButton.setOnAction(event -> chrome.closeToBackground());

        HBox controls = new HBox(minimizeButton, maximizeButton, closeButton);
        controls.setAlignment(Pos.CENTER_RIGHT);
        controls.getStyleClass().add("window-controls");

        getChildren().setAll(dragRegion, controls);

        stage.maximizedProperty().addListener((observable, oldValue, newValue) ->
                updateMaximizeButton(newValue)
        );
        updateMaximizeButton(stage.isMaximized());
    }

    private HBox createDragRegion() {
        HBox dragRegion = new HBox(10.0);
        dragRegion.setAlignment(Pos.CENTER_LEFT);
        dragRegion.setMaxWidth(Double.MAX_VALUE);
        dragRegion.getStyleClass().add("title-drag-region");

        ImageView icon = loadAppIcon();
        Label title = new Label("XClip");
        title.getStyleClass().add("title-app-name");

        if (icon != null) {
            dragRegion.getChildren().add(icon);
        }
        dragRegion.getChildren().add(title);

        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        dragRegion.getChildren().add(fill);

        dragRegion.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 1) return;
            chrome.beginDrag(event.getScreenX(), event.getScreenY());
        });

        dragRegion.setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown()) return;
            chrome.dragTo(event.getScreenX(), event.getScreenY());
        });

        dragRegion.setOnMouseReleased(event -> chrome.endDrag());

        dragRegion.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2) return;
            chrome.endDrag();
            chrome.handleTitleBarDoubleClick();
            event.consume();
        });

        return dragRegion;
    }

    private Button createWindowButton(String glyph, String tooltip, String extraStyleClass) {
        Button button = new Button();
        button.setGraphic(WindowsGlyphs.icon(glyph, "window-control-glyph"));
        button.setFocusTraversable(false);
        button.setAccessibleText(tooltip);
        button.setTooltip(new Tooltip(tooltip));
        button.getStyleClass().addAll("window-control-button", extraStyleClass);
        return button;
    }

    private void updateMaximizeButton(boolean maximized) {
        String glyph = maximized ? WindowsGlyphs.RESTORE : WindowsGlyphs.MAXIMIZE;
        String label = maximized ? "Restore" : "Maximize";

        maximizeButton.setGraphic(WindowsGlyphs.icon(glyph, "window-control-glyph"));
        maximizeButton.setAccessibleText(label);
        maximizeButton.setTooltip(new Tooltip(label));
    }

    private ImageView loadAppIcon() {
        try (InputStream stream = PopupTitleBar.class.getResourceAsStream("/icons/icon.png")) {
            if (stream == null) return null;

            ImageView view = new ImageView(new Image(stream));
            view.setFitWidth(ICON_SIZE);
            view.setFitHeight(ICON_SIZE);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setMouseTransparent(true);
            view.getStyleClass().add("title-app-icon");
            return view;
        } catch (Exception ignored) {
            return null;
        }
    }
}
