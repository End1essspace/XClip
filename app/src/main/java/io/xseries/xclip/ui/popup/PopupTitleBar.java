/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.system.window.WindowChromeController;
import io.xseries.xclip.system.window.WindowsCloseCornerSupport;
import io.xseries.xclip.ui.components.SvgIcon;
import io.xseries.xclip.ui.components.UiIcon;
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
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * Custom title bar for the undecorated popup shell.
 *
 * Product identity remains on the left, while Lucide-based Windows controls
 * stay optically balanced on the right. The large center area remains the drag
 * surface and supports double-click maximize/restore.
 */
public final class PopupTitleBar extends HBox {

    private static final double APP_ICON_SIZE = 20.0;
    private static final double SERIES_WATERMARK_HEIGHT = 12.0;

    private final WindowChromeController chrome;
    private final Button minimizeButton;
    private final Button maximizeButton;
    private final Button closeButton;
    private final WindowsCloseCornerSupport closeCornerSupport;

    public PopupTitleBar(Stage stage, WindowChromeController chrome) {
        Objects.requireNonNull(stage, "stage");
        this.chrome = Objects.requireNonNull(chrome, "chrome");

        getStyleClass().add("popup-title-bar");
        setAlignment(Pos.CENTER_LEFT);

        HBox dragRegion = createDragRegion();
        HBox.setHgrow(dragRegion, Priority.ALWAYS);

        minimizeButton = createWindowButton(
                UiIcon.MINUS,
                "Minimize",
                "window-minimize-button"
        );
        minimizeButton.setOnAction(event -> chrome.minimize());

        maximizeButton = createWindowButton(
                UiIcon.SQUARE,
                "Maximize",
                "window-maximize-button"
        );
        maximizeButton.setOnAction(event -> chrome.toggleMaximized());

        closeButton = createWindowButton(
                UiIcon.X,
                "Close to tray",
                "window-close-button"
        );
        closeCornerSupport = WindowsCloseCornerSupport.install(
                stage,
                closeButton,
                chrome::closeToBackground
        );
        closeButton.setOnAction(event -> closeCornerSupport.requestClose());

        HBox controls = new HBox(minimizeButton, maximizeButton, closeButton);
        controls.setAlignment(Pos.CENTER_RIGHT);
        controls.getStyleClass().add("window-controls");

        HBox titleRow = new HBox(dragRegion, controls);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setMaxWidth(Double.MAX_VALUE);

        StackPane titleSurface = new StackPane(titleRow);
        titleSurface.setAlignment(Pos.CENTER);
        titleSurface.setMaxWidth(Double.MAX_VALUE);
        titleSurface.getStyleClass().add("popup-title-surface");

        ImageView seriesWatermark = loadSeriesWatermark();
        if (seriesWatermark != null) {
            titleSurface.getChildren().add(seriesWatermark);
        }

        HBox.setHgrow(titleSurface, Priority.ALWAYS);
        getChildren().setAll(titleSurface);

        stage.maximizedProperty().addListener((observable, oldValue, newValue) ->
                updateMaximizeButton(newValue)
        );
        updateMaximizeButton(stage.isMaximized());
    }

    private HBox createDragRegion() {
        HBox dragRegion = new HBox(9.0);
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

    private Button createWindowButton(
            UiIcon icon,
            String tooltip,
            String extraStyleClass
    ) {
        Button button = new Button();
        button.setGraphic(SvgIcon.of(icon, 13, "window-control-icon"));
        button.setFocusTraversable(true);
        button.setAccessibleText(tooltip);
        button.setAccessibleHelp("Window control: " + tooltip + ".");
        button.setTooltip(new Tooltip(tooltip));
        button.getStyleClass().addAll("window-control-button", extraStyleClass);
        return button;
    }

    public List<Button> focusableControls() {
        return List.of(minimizeButton, maximizeButton, closeButton);
    }

    private void updateMaximizeButton(boolean maximized) {
        UiIcon icon = maximized ? UiIcon.COPY : UiIcon.SQUARE;
        String label = maximized ? "Restore" : "Maximize";

        maximizeButton.setGraphic(SvgIcon.of(icon, 12, "window-control-icon"));
        maximizeButton.setAccessibleText(label);
        maximizeButton.setAccessibleHelp("Window control: " + label + ".");
        maximizeButton.setTooltip(new Tooltip(label));
    }

    private ImageView loadSeriesWatermark() {
        try (InputStream stream = PopupTitleBar.class.getResourceAsStream("/icons/x-series.png")) {
            if (stream == null) return null;

            ImageView view = new ImageView(new Image(stream));
            view.setFitHeight(SERIES_WATERMARK_HEIGHT);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setMouseTransparent(true);
            view.setFocusTraversable(false);
            view.getStyleClass().add("title-series-watermark");
            return view;
        } catch (Exception ignored) {
            return null;
        }
    }

    private ImageView loadAppIcon() {
        try (InputStream stream = PopupTitleBar.class.getResourceAsStream("/icons/icon.png")) {
            if (stream == null) return null;

            ImageView view = new ImageView(new Image(stream));
            view.setFitWidth(APP_ICON_SIZE);
            view.setFitHeight(APP_ICON_SIZE);
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

