/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.window;

import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Central controller for both native and custom JavaFX window chrome.
 *
 * R2.2 activates an undecorated popup shell. The controller owns window-state
 * transitions, restored bounds, title-bar dragging, and manual edge resizing
 * while keeping the geometry rules independently testable.
 */
public final class WindowChromeController {

    /**
     * Logical JavaFX screen bounds. Negative x/y values are valid on multi-monitor setups.
     */
    public record WindowBounds(double x, double y, double width, double height) {

        public boolean isValid() {
            return Double.isFinite(x)
                    && Double.isFinite(y)
                    && Double.isFinite(width)
                    && Double.isFinite(height)
                    && width > 0
                    && height > 0;
        }
    }

    public enum ResizeEdge {
        NONE,
        NORTH,
        SOUTH,
        EAST,
        WEST,
        NORTH_EAST,
        NORTH_WEST,
        SOUTH_EAST,
        SOUTH_WEST
    }

    /**
     * Small host abstraction keeps transition logic testable without starting JavaFX.
     */
    public interface WindowHost {
        WindowBounds bounds();
        void setBounds(WindowBounds bounds);
        void setPosition(double x, double y);
        boolean isShowing();
        boolean isIconified();
        void setIconified(boolean iconified);
        boolean isMaximized();
        void setMaximized(boolean maximized);
        WindowBounds visualBounds();
    }

    private final WindowHost host;
    private final Runnable closeToBackground;

    private WindowBounds normalBounds;

    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    private boolean resizing;
    private ResizeEdge resizeEdge = ResizeEdge.NONE;
    private WindowBounds resizeStartBounds;
    private double resizeStartPointerX;
    private double resizeStartPointerY;
    private double resizeMinWidth = 1.0;
    private double resizeMinHeight = 1.0;

    WindowChromeController(WindowHost host, Runnable closeToBackground) {
        this.host = Objects.requireNonNull(host, "host");
        this.closeToBackground =
                closeToBackground != null ? closeToBackground : () -> {};
    }

    public static WindowChromeController forStage(
            Stage stage,
            Runnable closeToBackground
    ) {
        return new WindowChromeController(
                new StageWindowHost(Objects.requireNonNull(stage, "stage")),
                closeToBackground
        );
    }

    public boolean isMaximized() {
        return host.isMaximized();
    }

    public boolean isIconified() {
        return host.isIconified();
    }

    public WindowBounds currentBounds() {
        return host.bounds();
    }

    public WindowBounds currentVisualBounds() {
        return host.visualBounds();
    }

    public Optional<WindowBounds> normalBounds() {
        return Optional.ofNullable(normalBounds);
    }

    /**
     * Seeds the restored bounds from persisted configuration.
     */
    public void rememberNormalBounds(WindowBounds bounds) {
        if (bounds != null && bounds.isValid()) {
            normalBounds = bounds;
        }
    }

    /**
     * Captures the current restored bounds. Maximized and minimized geometry is
     * deliberately ignored because JavaFX can expose transient native bounds.
     */
    public boolean captureNormalBounds() {
        if (host.isMaximized() || host.isIconified()) return false;

        WindowBounds current = host.bounds();
        if (current == null || !current.isValid()) return false;

        normalBounds = current;
        return true;
    }

    /**
     * Returns the geometry that should be persisted. While maximized, the last
     * restored bounds are preserved instead of storing the full-screen rectangle.
     */
    public Optional<WindowBounds> persistenceBounds() {
        if (host.isMaximized() && normalBounds != null && normalBounds.isValid()) {
            return Optional.of(normalBounds);
        }

        WindowBounds current = host.bounds();
        if (current != null && current.isValid()) {
            if (!host.isIconified()) {
                normalBounds = current;
            }
            return Optional.of(current);
        }

        return Optional.ofNullable(normalBounds);
    }

    /**
     * Applies a known restored rectangle without maximizing the window.
     */
    public boolean applyRestoredBounds(WindowBounds bounds) {
        if (bounds == null || !bounds.isValid()) return false;

        if (host.isMaximized()) {
            host.setMaximized(false);
        }
        host.setBounds(bounds);
        normalBounds = bounds;
        return true;
    }

    public boolean minimize() {
        if (!host.isShowing() || host.isIconified()) return false;

        captureNormalBounds();
        host.setIconified(true);
        cancelPointerOperation();
        return true;
    }

    public boolean restoreFromMinimized() {
        if (!host.isIconified()) return false;

        host.setIconified(false);
        return true;
    }

    public boolean maximize() {
        if (!host.isShowing() || host.isMaximized()) return false;

        captureNormalBounds();
        host.setMaximized(true);
        cancelPointerOperation();
        return true;
    }

    public boolean restore() {
        if (!host.isMaximized()) return false;

        host.setMaximized(false);
        if (normalBounds != null && normalBounds.isValid()) {
            host.setBounds(normalBounds);
        }
        cancelPointerOperation();
        return true;
    }

    public boolean toggleMaximized() {
        return host.isMaximized() ? restore() : maximize();
    }

    public boolean handleTitleBarDoubleClick() {
        return toggleMaximized();
    }

    /**
     * Starts title-bar dragging only in restored mode. Dragging a maximized
     * window into restored mode is intentionally reserved for R2.3.
     */
    public boolean beginDrag(double pointerScreenX, double pointerScreenY) {
        if (!host.isShowing() || host.isIconified() || host.isMaximized() || resizing) {
            dragging = false;
            return false;
        }
        if (!Double.isFinite(pointerScreenX) || !Double.isFinite(pointerScreenY)) {
            dragging = false;
            return false;
        }

        WindowBounds current = host.bounds();
        if (current == null || !current.isValid()) {
            dragging = false;
            return false;
        }

        dragOffsetX = pointerScreenX - current.x();
        dragOffsetY = pointerScreenY - current.y();
        dragging = true;
        return true;
    }

    public boolean dragTo(double pointerScreenX, double pointerScreenY) {
        if (!dragging) return false;
        if (!Double.isFinite(pointerScreenX) || !Double.isFinite(pointerScreenY)) {
            return false;
        }

        host.setPosition(
                pointerScreenX - dragOffsetX,
                pointerScreenY - dragOffsetY
        );
        return true;
    }

    public void endDrag() {
        if (!dragging) return;

        dragging = false;
        captureNormalBounds();
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean beginResize(
            ResizeEdge edge,
            double pointerScreenX,
            double pointerScreenY,
            double minWidth,
            double minHeight
    ) {
        if (edge == null || edge == ResizeEdge.NONE) return false;
        if (!host.isShowing() || host.isIconified() || host.isMaximized() || dragging) {
            resizing = false;
            return false;
        }
        if (!Double.isFinite(pointerScreenX) || !Double.isFinite(pointerScreenY)) {
            resizing = false;
            return false;
        }

        WindowBounds current = host.bounds();
        if (current == null || !current.isValid()) {
            resizing = false;
            return false;
        }

        resizeEdge = edge;
        resizeStartBounds = current;
        resizeStartPointerX = pointerScreenX;
        resizeStartPointerY = pointerScreenY;
        resizeMinWidth = normalizeMinimum(minWidth);
        resizeMinHeight = normalizeMinimum(minHeight);
        resizing = true;
        return true;
    }

    public boolean resizeTo(double pointerScreenX, double pointerScreenY) {
        if (!resizing || resizeStartBounds == null) return false;
        if (!Double.isFinite(pointerScreenX) || !Double.isFinite(pointerScreenY)) {
            return false;
        }

        double deltaX = pointerScreenX - resizeStartPointerX;
        double deltaY = pointerScreenY - resizeStartPointerY;

        WindowBounds resized = resizedBounds(
                resizeStartBounds,
                resizeEdge,
                deltaX,
                deltaY,
                resizeMinWidth,
                resizeMinHeight
        );
        host.setBounds(resized);
        return true;
    }

    public void endResize() {
        if (!resizing) return;

        resizing = false;
        resizeEdge = ResizeEdge.NONE;
        resizeStartBounds = null;
        captureNormalBounds();
    }

    public boolean isResizing() {
        return resizing;
    }

    public ResizeEdge resizeEdge() {
        return resizeEdge;
    }

    /**
     * Installs manual edge resizing required by StageStyle.UNDECORATED.
     */
    public void installResizeSupport(
            Scene scene,
            double edgeThickness,
            double minWidth,
            double minHeight
    ) {
        Objects.requireNonNull(scene, "scene");

        double safeThickness = Math.max(1.0, edgeThickness);
        double safeMinWidth = normalizeMinimum(minWidth);
        double safeMinHeight = normalizeMinimum(minHeight);

        scene.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            if (isMaximized() || isDragging() || isResizing()) {
                if (!isResizing()) scene.setCursor(Cursor.DEFAULT);
                return;
            }

            ResizeEdge edge = resizeEdgeFor(
                    event.getSceneX(),
                    event.getSceneY(),
                    scene.getWidth(),
                    scene.getHeight(),
                    safeThickness
            );
            scene.setCursor(cursorFor(edge));
        });

        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || isMaximized()) return;

            ResizeEdge edge = resizeEdgeFor(
                    event.getSceneX(),
                    event.getSceneY(),
                    scene.getWidth(),
                    scene.getHeight(),
                    safeThickness
            );

            if (beginResize(
                    edge,
                    event.getScreenX(),
                    event.getScreenY(),
                    safeMinWidth,
                    safeMinHeight
            )) {
                scene.setCursor(cursorFor(edge));
                event.consume();
            }
        });

        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!isResizing()) return;

            resizeTo(event.getScreenX(), event.getScreenY());
            event.consume();
        });

        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (!isResizing()) return;

            endResize();
            ResizeEdge edge = resizeEdgeFor(
                    event.getSceneX(),
                    event.getSceneY(),
                    scene.getWidth(),
                    scene.getHeight(),
                    safeThickness
            );
            scene.setCursor(cursorFor(edge));
            event.consume();
        });

        scene.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
            if (!isResizing()) scene.setCursor(Cursor.DEFAULT);
        });
    }

    public void closeToBackground() {
        cancelPointerOperation();
        closeToBackground.run();
    }

    static ResizeEdge resizeEdgeFor(
            double sceneX,
            double sceneY,
            double sceneWidth,
            double sceneHeight,
            double edgeThickness
    ) {
        if (!Double.isFinite(sceneX)
                || !Double.isFinite(sceneY)
                || !Double.isFinite(sceneWidth)
                || !Double.isFinite(sceneHeight)
                || !Double.isFinite(edgeThickness)
                || sceneWidth <= 0
                || sceneHeight <= 0
                || edgeThickness <= 0) {
            return ResizeEdge.NONE;
        }

        boolean left = sceneX >= 0 && sceneX <= edgeThickness;
        boolean right = sceneX <= sceneWidth && sceneX >= sceneWidth - edgeThickness;
        boolean top = sceneY >= 0 && sceneY <= edgeThickness;
        boolean bottom = sceneY <= sceneHeight && sceneY >= sceneHeight - edgeThickness;

        if (top && left) return ResizeEdge.NORTH_WEST;
        if (top && right) return ResizeEdge.NORTH_EAST;
        if (bottom && left) return ResizeEdge.SOUTH_WEST;
        if (bottom && right) return ResizeEdge.SOUTH_EAST;
        if (top) return ResizeEdge.NORTH;
        if (bottom) return ResizeEdge.SOUTH;
        if (left) return ResizeEdge.WEST;
        if (right) return ResizeEdge.EAST;
        return ResizeEdge.NONE;
    }

    static WindowBounds resizedBounds(
            WindowBounds start,
            ResizeEdge edge,
            double deltaX,
            double deltaY,
            double minWidth,
            double minHeight
    ) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(edge, "edge");

        double safeMinWidth = normalizeMinimum(minWidth);
        double safeMinHeight = normalizeMinimum(minHeight);

        double x = start.x();
        double y = start.y();
        double width = start.width();
        double height = start.height();

        boolean west = edge == ResizeEdge.WEST
                || edge == ResizeEdge.NORTH_WEST
                || edge == ResizeEdge.SOUTH_WEST;
        boolean east = edge == ResizeEdge.EAST
                || edge == ResizeEdge.NORTH_EAST
                || edge == ResizeEdge.SOUTH_EAST;
        boolean north = edge == ResizeEdge.NORTH
                || edge == ResizeEdge.NORTH_WEST
                || edge == ResizeEdge.NORTH_EAST;
        boolean south = edge == ResizeEdge.SOUTH
                || edge == ResizeEdge.SOUTH_WEST
                || edge == ResizeEdge.SOUTH_EAST;

        if (west) {
            double candidateWidth = width - deltaX;
            if (candidateWidth < safeMinWidth) {
                x = start.x() + start.width() - safeMinWidth;
                width = safeMinWidth;
            } else {
                x = start.x() + deltaX;
                width = candidateWidth;
            }
        } else if (east) {
            width = Math.max(safeMinWidth, width + deltaX);
        }

        if (north) {
            double candidateHeight = height - deltaY;
            if (candidateHeight < safeMinHeight) {
                y = start.y() + start.height() - safeMinHeight;
                height = safeMinHeight;
            } else {
                y = start.y() + deltaY;
                height = candidateHeight;
            }
        } else if (south) {
            height = Math.max(safeMinHeight, height + deltaY);
        }

        return new WindowBounds(x, y, width, height);
    }

    private void cancelPointerOperation() {
        dragging = false;
        resizing = false;
        resizeEdge = ResizeEdge.NONE;
        resizeStartBounds = null;
    }

    private static double normalizeMinimum(double value) {
        return Double.isFinite(value) && value > 0 ? value : 1.0;
    }

    private static Cursor cursorFor(ResizeEdge edge) {
        return switch (edge) {
            case NORTH -> Cursor.N_RESIZE;
            case SOUTH -> Cursor.S_RESIZE;
            case EAST -> Cursor.E_RESIZE;
            case WEST -> Cursor.W_RESIZE;
            case NORTH_EAST -> Cursor.NE_RESIZE;
            case NORTH_WEST -> Cursor.NW_RESIZE;
            case SOUTH_EAST -> Cursor.SE_RESIZE;
            case SOUTH_WEST -> Cursor.SW_RESIZE;
            case NONE -> Cursor.DEFAULT;
        };
    }

    private static final class StageWindowHost implements WindowHost {

        private final Stage stage;

        private StageWindowHost(Stage stage) {
            this.stage = stage;
        }

        @Override
        public WindowBounds bounds() {
            return new WindowBounds(
                    stage.getX(),
                    stage.getY(),
                    stage.getWidth(),
                    stage.getHeight()
            );
        }

        @Override
        public void setBounds(WindowBounds bounds) {
            stage.setWidth(bounds.width());
            stage.setHeight(bounds.height());
            stage.setX(bounds.x());
            stage.setY(bounds.y());
        }

        @Override
        public void setPosition(double x, double y) {
            stage.setX(x);
            stage.setY(y);
        }

        @Override
        public boolean isShowing() {
            return stage.isShowing();
        }

        @Override
        public boolean isIconified() {
            return stage.isIconified();
        }

        @Override
        public void setIconified(boolean iconified) {
            stage.setIconified(iconified);
        }

        @Override
        public boolean isMaximized() {
            return stage.isMaximized();
        }

        @Override
        public void setMaximized(boolean maximized) {
            stage.setMaximized(maximized);
        }

        @Override
        public WindowBounds visualBounds() {
            double width = Math.max(1.0, stage.getWidth());
            double height = Math.max(1.0, stage.getHeight());

            List<Screen> screens = Screen.getScreensForRectangle(
                    stage.getX(),
                    stage.getY(),
                    width,
                    height
            );
            Screen screen = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
            Rectangle2D bounds = screen.getVisualBounds();

            return new WindowBounds(
                    bounds.getMinX(),
                    bounds.getMinY(),
                    bounds.getWidth(),
                    bounds.getHeight()
            );
        }
    }
}
