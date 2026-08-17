/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.window;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.robot.Robot;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Defensive Fitts-law fallback for the physical top edge of the maximized
 * Windows popup.
 *
 * JavaFX can lose the exact top-row pointer interaction to the native
 * non-client/resizable window edge even when the visible close button reaches
 * the screen corner. Normal JavaFX button events remain the primary path.
 *
 * This watcher is intentionally short-lived: it runs only while the stage is
 * showing, focused, non-iconified, and maximized. It covers only the narrow
 * physical top-edge band horizontally occupied by the real close button.
 */
public final class WindowsCloseCornerSupport implements AutoCloseable {

    private static final int VK_LBUTTON = 0x01;
    private static final double POLL_INTERVAL_MILLIS = 16.0;
    private static final double TOP_EDGE_BAND = 3.0;
    private static final double EDGE_TOLERANCE = 3.0;
    private static final double TOP_GEOMETRY_TOLERANCE = 16.0;

    private static final PseudoClass CORNER_HOVER =
            PseudoClass.getPseudoClass("corner-hover");
    private static final PseudoClass CORNER_PRESSED =
            PseudoClass.getPseudoClass("corner-pressed");

    private interface User32PointerApi extends StdCallLibrary {
        User32PointerApi INSTANCE = Native.load(
                "user32",
                User32PointerApi.class,
                W32APIOptions.DEFAULT_OPTIONS
        );

        short GetAsyncKeyState(int virtualKey);
    }

    private final Stage stage;
    private final Button closeButton;
    private final Runnable closeAction;
    private final Timeline watcher;
    private final Robot robot;

    private boolean lastLeftDown;
    private boolean cornerArmed;
    private boolean closeRequested;
    private boolean closed;

    private WindowsCloseCornerSupport(
            Stage stage,
            Button closeButton,
            Runnable closeAction
    ) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.closeButton = Objects.requireNonNull(closeButton, "closeButton");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");

        Robot candidate = null;
        if (isWindows()) {
            try {
                candidate = new Robot();
            } catch (Throwable ignored) {
                // Normal JavaFX button behavior remains fully functional.
            }
        }
        this.robot = candidate;

        watcher = new Timeline(
                new KeyFrame(
                        Duration.millis(POLL_INTERVAL_MILLIS),
                        event -> poll()
                )
        );
        watcher.setCycleCount(Animation.INDEFINITE);

        stage.showingProperty().addListener((observable, oldValue, showing) -> {
            if (showing) {
                closeRequested = false;
            }
            syncWatcher();
        });
        stage.focusedProperty().addListener((observable, oldValue, focused) ->
                syncWatcher()
        );
        stage.iconifiedProperty().addListener((observable, oldValue, iconified) ->
                syncWatcher()
        );
        stage.maximizedProperty().addListener((observable, oldValue, maximized) ->
                syncWatcher()
        );

        syncWatcher();
    }

    public static WindowsCloseCornerSupport install(
            Stage stage,
            Button closeButton,
            Runnable closeAction
    ) {
        return new WindowsCloseCornerSupport(stage, closeButton, closeAction);
    }

    /**
     * Single guarded close path used by both the normal JavaFX button and the
     * physical-corner fallback.
     */
    public void requestClose() {
        if (closed || closeRequested) return;

        closeRequested = true;
        resetInteractionState();

        Runnable request = () -> {
            try {
                closeAction.run();
            } finally {
                // close-to-background normally hides the Stage. If a caller ever
                // declines the close and keeps it visible, permit another attempt.
                if (stage.isShowing()) {
                    closeRequested = false;
                    syncWatcher();
                }
            }
        };

        if (Platform.isFxApplicationThread()) {
            request.run();
        } else {
            Platform.runLater(request);
        }
    }

    private void syncWatcher() {
        if (closed) return;

        if (eligible()) {
            if (watcher.getStatus() != Animation.Status.RUNNING) {
                lastLeftDown = leftButtonDown();
                cornerArmed = false;
                watcher.play();
            }
        } else {
            watcher.stop();
            resetInteractionState();
        }
    }

    private boolean eligible() {
        return robot != null
                && stage.isShowing()
                && stage.isFocused()
                && !stage.isIconified()
                && stage.isMaximized();
    }

    private void poll() {
        if (!eligible()) {
            syncWatcher();
            return;
        }

        try {
            EdgeTarget target = currentEdgeTarget();
            boolean leftDown = leftButtonDown();

            if (target == null) {
                updateVisualState(false, false);
                cornerArmed = false;
                lastLeftDown = leftDown;
                return;
            }

            Point2D pointer = robot.getMousePosition();
            boolean inside = target.contains(pointer);

            boolean pressedNow = leftDown && !lastLeftDown;
            boolean releasedNow = !leftDown && lastLeftDown;
            lastLeftDown = leftDown;

            if (pressedNow) {
                cornerArmed = inside;
            }

            updateVisualState(inside, cornerArmed && leftDown && inside);

            if (releasedNow) {
                boolean shouldClose = cornerArmed && inside;
                cornerArmed = false;
                updateVisualState(inside, false);

                if (shouldClose) {
                    requestClose();
                }
            }
        } catch (Throwable ignored) {
            // The fallback is defensive only. Any native/Robot failure must
            // leave ordinary JavaFX close-button behavior intact.
            watcher.stop();
            resetInteractionState();
        }
    }

    /**
     * Builds a target in JavaFX global logical coordinates.
     *
     * Screen bounds and Robot pointer coordinates share the same JavaFX
     * coordinate system, avoiding physical/logical DPI mixing. The target is
     * restricted to the actual close button's horizontal span and a tiny band
     * at the physical top edge.
     */
    private EdgeTarget currentEdgeTarget() {
        Bounds closeBounds = closeButton.localToScreen(closeButton.getBoundsInLocal());
        if (closeBounds == null
                || closeBounds.getWidth() <= 0.0
                || closeBounds.getHeight() <= 0.0) {
            return null;
        }

        double centerX = (closeBounds.getMinX() + closeBounds.getMaxX()) * 0.5;
        double centerY = (closeBounds.getMinY() + closeBounds.getMaxY()) * 0.5;

        List<Screen> screens = Screen.getScreensForRectangle(
                centerX,
                centerY,
                1.0,
                1.0
        );
        if (screens.isEmpty()) return null;

        Screen screen = screens.get(0);
        Rectangle2D physical = screen.getBounds();
        Rectangle2D visual = screen.getVisualBounds();

        // Do not claim a corner owned by a top or right taskbar/panel.
        if (visual.getMinY() > physical.getMinY() + EDGE_TOLERANCE
                || visual.getMaxX() < physical.getMaxX() - EDGE_TOLERANCE) {
            return null;
        }

        // The real close control must already be at the physical right edge.
        if (closeBounds.getMaxX() < physical.getMaxX() - EDGE_TOLERANCE) {
            return null;
        }

        // Allow only the small native-frame discrepancy that motivated this
        // fallback; never bridge a materially displaced title bar.
        if (closeBounds.getMinY() > physical.getMinY() + TOP_GEOMETRY_TOLERANCE) {
            return null;
        }

        double minX = Math.max(closeBounds.getMinX(), physical.getMinX());
        double maxX = physical.getMaxX();
        double minY = physical.getMinY();
        double maxY = Math.min(
                physical.getMaxY(),
                physical.getMinY() + TOP_EDGE_BAND
        );

        if (maxX <= minX || maxY <= minY) return null;
        return new EdgeTarget(minX, minY, maxX, maxY);
    }

    private boolean leftButtonDown() {
        if (!isWindows()) return false;

        try {
            return (User32PointerApi.INSTANCE.GetAsyncKeyState(VK_LBUTTON) & 0x8000) != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void updateVisualState(boolean hover, boolean pressed) {
        closeButton.pseudoClassStateChanged(CORNER_HOVER, hover);
        closeButton.pseudoClassStateChanged(CORNER_PRESSED, pressed);
    }

    private void resetInteractionState() {
        cornerArmed = false;
        lastLeftDown = leftButtonDown();
        updateVisualState(false, false);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        watcher.stop();
        resetInteractionState();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }

    private record EdgeTarget(
            double minX,
            double minY,
            double maxX,
            double maxY
    ) {
        private boolean contains(Point2D point) {
            if (point == null) return false;

            return point.getX() >= minX
                    && point.getX() < maxX
                    && point.getY() >= minY
                    && point.getY() < maxY;
        }
    }
}
