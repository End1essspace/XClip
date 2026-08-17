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
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.robot.Robot;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Fitts-law fallback for dragging the popup ListView scrollbar from the
 * physical right edge of a maximized Windows screen.
 *
 * The normal JavaFX ScrollBar remains the primary interaction path. This
 * support exists only because the exact native screen edge can be delivered as
 * non-client input before JavaFX sees it. The fallback never expands into a
 * neighbouring monitor and never runs for restored/inactive/hidden windows.
 */
public final class WindowsListScrollEdgeSupport implements AutoCloseable {

    private static final int VK_LBUTTON = 0x01;
    private static final double POLL_INTERVAL_MILLIS = 16.0;
    private static final double PHYSICAL_EDGE_BAND = 3.0;
    private static final double EDGE_TOLERANCE = 18.0;

    private static final PseudoClass EDGE_DRAG =
            PseudoClass.getPseudoClass("edge-drag");

    private interface User32PointerApi extends StdCallLibrary {
        User32PointerApi INSTANCE = Native.load(
                "user32",
                User32PointerApi.class,
                W32APIOptions.DEFAULT_OPTIONS
        );

        short GetAsyncKeyState(int virtualKey);
    }

    private final Stage stage;
    private final ListView<?> listView;
    private final Timeline watcher;
    private final Robot robot;

    private boolean lastLeftDown;
    private boolean dragging;
    private double thumbGrabOffsetY;
    private boolean closed;

    private WindowsListScrollEdgeSupport(
            Stage stage,
            ListView<?> listView
    ) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.listView = Objects.requireNonNull(listView, "listView");

        Robot candidate = null;
        if (isWindows()) {
            try {
                candidate = new Robot();
            } catch (Throwable ignored) {
                // Ordinary JavaFX scrolling remains available.
            }
        }
        robot = candidate;

        watcher = new Timeline(
                new KeyFrame(
                        Duration.millis(POLL_INTERVAL_MILLIS),
                        event -> poll()
                )
        );
        watcher.setCycleCount(Animation.INDEFINITE);

        stage.showingProperty().addListener((obs, oldValue, value) -> syncWatcher());
        stage.focusedProperty().addListener((obs, oldValue, value) -> syncWatcher());
        stage.iconifiedProperty().addListener((obs, oldValue, value) -> syncWatcher());
        stage.maximizedProperty().addListener((obs, oldValue, value) -> syncWatcher());
        listView.visibleProperty().addListener((obs, oldValue, value) -> syncWatcher());

        syncWatcher();
    }

    public static WindowsListScrollEdgeSupport install(
            Stage stage,
            ListView<?> listView
    ) {
        return new WindowsListScrollEdgeSupport(stage, listView);
    }

    private void syncWatcher() {
        if (closed) return;

        if (eligible()) {
            if (watcher.getStatus() != Animation.Status.RUNNING) {
                lastLeftDown = leftButtonDown();
                dragging = false;
                watcher.play();
            }
        } else {
            watcher.stop();
            resetDragState();
        }
    }

    private boolean eligible() {
        return robot != null
                && stage.isShowing()
                && stage.isFocused()
                && !stage.isIconified()
                && stage.isMaximized()
                && listView.isVisible();
    }

    private void poll() {
        if (!eligible()) {
            syncWatcher();
            return;
        }

        try {
            ScrollGeometry geometry = geometry();
            boolean leftDown = leftButtonDown();

            if (geometry == null) {
                dragging = false;
                lastLeftDown = leftDown;
                setEdgeDragPseudoClass(false);
                return;
            }

            Point2D pointer = robot.getMousePosition();
            boolean pressedNow = leftDown && !lastLeftDown;
            boolean releasedNow = !leftDown && lastLeftDown;
            lastLeftDown = leftDown;

            boolean atPhysicalEdge = geometry.atPhysicalRightEdge(pointer);
            boolean overThumbY = geometry.thumbContainsY(pointer);

            if (pressedNow && atPhysicalEdge && overThumbY) {
                dragging = true;
                thumbGrabOffsetY = pointer.getY() - geometry.thumbMinY();
                setEdgeDragPseudoClass(true);
            }

            if (dragging && leftDown) {
                // Once the press starts on the edge thumb, preserve ordinary
                // scrollbar capture semantics even if the pointer moves a few
                // pixels left/right while dragging.
                setScrollValueFromPointer(
                        geometry,
                        pointer.getY(),
                        thumbGrabOffsetY
                );
            }

            if (releasedNow) {
                dragging = false;
                setEdgeDragPseudoClass(false);
            }
        } catch (Throwable ignored) {
            // Defensive fallback only. Never damage ordinary ListView input.
            watcher.stop();
            resetDragState();
        }
    }

    private ScrollGeometry geometry() {
        ScrollBar bar = verticalScrollBar();
        if (bar == null || !bar.isVisible() || bar.getMax() <= bar.getMin()) {
            return null;
        }

        Node thumb = bar.lookup(".thumb");
        Node track = bar.lookup(".track");
        if (thumb == null || track == null || !thumb.isVisible()) {
            return null;
        }

        Bounds barBounds = bar.localToScreen(bar.getBoundsInLocal());
        Bounds thumbBounds = thumb.localToScreen(thumb.getBoundsInLocal());
        Bounds trackBounds = track.localToScreen(track.getBoundsInLocal());
        if (!valid(barBounds) || !valid(thumbBounds) || !valid(trackBounds)) {
            return null;
        }

        double centerX = (barBounds.getMinX() + barBounds.getMaxX()) * 0.5;
        double centerY = (barBounds.getMinY() + barBounds.getMaxY()) * 0.5;

        List<Screen> screens = Screen.getScreensForRectangle(
                centerX,
                centerY,
                1.0,
                1.0
        );
        if (screens.isEmpty()) return null;

        Rectangle2D screenBounds = screens.get(0).getBounds();
        Rectangle2D visualBounds = screens.get(0).getVisualBounds();

        // A right-side taskbar/panel owns the physical edge; do not intercept it.
        if (visualBounds.getMaxX() < screenBounds.getMaxX() - 3.0) {
            return null;
        }

        // The actual JavaFX scrollbar must already be adjacent to this hard edge.
        if (barBounds.getMaxX() < screenBounds.getMaxX() - EDGE_TOLERANCE) {
            return null;
        }

        return new ScrollGeometry(
                bar,
                thumbBounds.getMinY(),
                thumbBounds.getMaxY(),
                thumbBounds.getHeight(),
                trackBounds.getMinY(),
                trackBounds.getMaxY(),
                screenBounds.getMaxX()
        );
    }

    private ScrollBar verticalScrollBar() {
        Node node = listView.lookup(".scroll-bar:vertical");
        return node instanceof ScrollBar bar ? bar : null;
    }

    private void setScrollValueFromPointer(
            ScrollGeometry geometry,
            double pointerY,
            double grabOffsetY
    ) {
        double trackMin = geometry.trackMinY();
        double travel = (geometry.trackMaxY() - geometry.trackMinY())
                - geometry.thumbHeight();
        if (travel <= 0.0) return;

        double desiredThumbTop = pointerY - grabOffsetY;
        double clampedThumbTop = clamp(
                desiredThumbTop,
                trackMin,
                trackMin + travel
        );
        double fraction = (clampedThumbTop - trackMin) / travel;

        ScrollBar bar = geometry.bar();
        double next = bar.getMin() + fraction * (bar.getMax() - bar.getMin());
        if (Platform.isFxApplicationThread()) {
            bar.setValue(next);
        } else {
            Platform.runLater(() -> bar.setValue(next));
        }
    }

    private void setEdgeDragPseudoClass(boolean enabled) {
        ScrollBar bar = verticalScrollBar();
        if (bar != null) {
            bar.pseudoClassStateChanged(EDGE_DRAG, enabled);
        }
    }

    private void resetDragState() {
        dragging = false;
        lastLeftDown = leftButtonDown();
        setEdgeDragPseudoClass(false);
    }

    private boolean leftButtonDown() {
        if (!isWindows()) return false;
        try {
            return (User32PointerApi.INSTANCE.GetAsyncKeyState(VK_LBUTTON) & 0x8000) != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean valid(Bounds bounds) {
        return bounds != null
                && bounds.getWidth() > 0.0
                && bounds.getHeight() > 0.0
                && Double.isFinite(bounds.getMinX())
                && Double.isFinite(bounds.getMinY())
                && Double.isFinite(bounds.getMaxX())
                && Double.isFinite(bounds.getMaxY());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        watcher.stop();
        resetDragState();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }

    private record ScrollGeometry(
            ScrollBar bar,
            double thumbMinY,
            double thumbMaxY,
            double thumbHeight,
            double trackMinY,
            double trackMaxY,
            double physicalRight
    ) {
        private boolean atPhysicalRightEdge(Point2D point) {
            if (point == null) return false;
            return point.getX() >= physicalRight - PHYSICAL_EDGE_BAND
                    && point.getX() < physicalRight;
        }

        private boolean thumbContainsY(Point2D point) {
            if (point == null) return false;
            return point.getY() >= thumbMinY
                    && point.getY() < thumbMaxY;
        }
    }
}
