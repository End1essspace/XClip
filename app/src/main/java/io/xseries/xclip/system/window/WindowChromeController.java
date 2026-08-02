/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.window;

import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
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
 * R2.3 hardens undecorated XClip window shells for persisted geometry,
 * multi-monitor topologies, maximized drag restore, and manual edge resizing
 * while keeping the geometry rules independently testable.
 */
public final class WindowChromeController {

    private static final double MIN_VISIBLE_WIDTH = 96.0;
    private static final double MIN_VISIBLE_HEIGHT = 48.0;
    private static final double MAX_RESTORE_DRAG_TOP_OFFSET = 40.0;
    private static final String RESIZE_SUPPRESSION_STYLE = "window-control-hit-target";

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

        default WindowBounds visualBoundsAt(double pointerX, double pointerY) {
            return visualBounds();
        }
    }

    private final WindowHost host;
    private final Runnable closeToBackground;

    private WindowBounds normalBounds;

    private boolean dragging;
    private boolean maximizedDragPending;
    private double dragOffsetX;
    private double dragOffsetY;
    private double dragRestoreRatioX;
    private double dragRestoreOffsetY;

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
     * Arms title-bar dragging. In maximized mode the window stays maximized
     * until the pointer actually moves, which preserves normal double-click
     * maximize/restore behavior.
     */
    public boolean beginDrag(double pointerScreenX, double pointerScreenY) {
        if (!host.isShowing() || host.isIconified() || resizing) {
            cancelDrag();
            return false;
        }
        if (!Double.isFinite(pointerScreenX) || !Double.isFinite(pointerScreenY)) {
            cancelDrag();
            return false;
        }

        if (host.isMaximized()) {
            WindowBounds visual = host.visualBounds();
            WindowBounds restored = restoredBoundsForDrag();
            if (visual == null || !visual.isValid() || restored == null || !restored.isValid()) {
                cancelDrag();
                return false;
            }

            dragRestoreRatioX = clamp(
                    (pointerScreenX - visual.x()) / visual.width(),
                    0.0,
                    1.0
            );
            dragRestoreOffsetY = clamp(
                    pointerScreenY - visual.y(),
                    0.0,
                    Math.min(MAX_RESTORE_DRAG_TOP_OFFSET, restored.height())
            );
            maximizedDragPending = true;
            dragging = true;
            return true;
        }

        WindowBounds current = host.bounds();
        if (current == null || !current.isValid()) {
            cancelDrag();
            return false;
        }

        dragOffsetX = pointerScreenX - current.x();
        dragOffsetY = pointerScreenY - current.y();
        maximizedDragPending = false;
        dragging = true;
        return true;
    }

    public boolean dragTo(double pointerScreenX, double pointerScreenY) {
        if (!dragging) return false;
        if (!Double.isFinite(pointerScreenX) || !Double.isFinite(pointerScreenY)) {
            return false;
        }

        if (maximizedDragPending) {
            WindowBounds restored = restoredBoundsForDrag();
            if (restored == null || !restored.isValid()) {
                cancelDrag();
                return false;
            }

            WindowBounds targetVisual =
                    host.visualBoundsAt(pointerScreenX, pointerScreenY);
            double restoredWidth = restored.width();
            double restoredHeight = restored.height();
            if (targetVisual != null && targetVisual.isValid()) {
                restoredWidth = Math.min(restoredWidth, targetVisual.width());
                restoredHeight = Math.min(restoredHeight, targetVisual.height());
            }

            dragOffsetX = restoredWidth * dragRestoreRatioX;
            dragOffsetY = Math.min(dragRestoreOffsetY, restoredHeight);

            WindowBounds moved = new WindowBounds(
                    pointerScreenX - dragOffsetX,
                    pointerScreenY - dragOffsetY,
                    restoredWidth,
                    restoredHeight
            );

            host.setMaximized(false);
            host.setBounds(moved);
            normalBounds = moved;
            maximizedDragPending = false;
            return true;
        }

        host.setPosition(
                pointerScreenX - dragOffsetX,
                pointerScreenY - dragOffsetY
        );
        return true;
    }

    public void endDrag() {
        if (!dragging) return;

        boolean restoredDuringDrag = !maximizedDragPending;
        cancelDrag();
        if (restoredDuringDrag) {
            captureNormalBounds();
        }
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
            if (resizeSuppressedFor(event.getTarget())) {
                if (!isResizing()) scene.setCursor(Cursor.DEFAULT);
                return;
            }
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
            if (event.getButton() != MouseButton.PRIMARY
                    || isMaximized()
                    || resizeSuppressedFor(event.getTarget())) {
                return;
            }

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

    /**
     * Returns a geometry that remains recoverable on the current monitor
     * topology. Valid negative coordinates are preserved. A rectangle is moved
     * only when its draggable area is effectively lost or when it is larger
     * than the selected monitor's visual bounds.
     */
    public static Optional<WindowBounds> recoverToVisibleScreens(
            WindowBounds requested,
            List<WindowBounds> visualScreens
    ) {
        if (requested == null || !requested.isValid()) return Optional.empty();

        List<WindowBounds> screens = validBounds(visualScreens);
        if (screens.isEmpty()) return Optional.empty();

        WindowBounds target = bestScreenForWindow(requested, screens);
        if (target == null) return Optional.empty();

        boolean oversized = requested.width() > target.width()
                || requested.height() > target.height();
        if (!oversized && isSufficientlyVisible(requested, screens)) {
            return Optional.of(requested);
        }

        double width = Math.min(requested.width(), target.width());
        double height = Math.min(requested.height(), target.height());
        double x = clamp(
                requested.x(),
                target.x(),
                target.x() + target.width() - width
        );
        double y = clamp(
                requested.y(),
                target.y(),
                target.y() + target.height() - height
        );

        return Optional.of(new WindowBounds(x, y, width, height));
    }

    /**
     * Selects the visual screen containing a logical JavaFX pointer coordinate.
     * When the point is outside all screens, the nearest screen is returned.
     */
    public static Optional<WindowBounds> screenForPoint(
            double pointerX,
            double pointerY,
            List<WindowBounds> visualScreens
    ) {
        if (!Double.isFinite(pointerX) || !Double.isFinite(pointerY)) {
            return Optional.empty();
        }

        List<WindowBounds> screens = validBounds(visualScreens);
        if (screens.isEmpty()) return Optional.empty();

        for (WindowBounds screen : screens) {
            if (pointerX >= screen.x()
                    && pointerX < screen.x() + screen.width()
                    && pointerY >= screen.y()
                    && pointerY < screen.y() + screen.height()) {
                return Optional.of(screen);
            }
        }

        WindowBounds nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (WindowBounds screen : screens) {
            double distance = squaredDistanceToRectangle(pointerX, pointerY, screen);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = screen;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private WindowBounds restoredBoundsForDrag() {
        if (normalBounds != null && normalBounds.isValid()) {
            return normalBounds;
        }

        WindowBounds visual = host.visualBounds();
        if (visual == null || !visual.isValid()) return null;

        double width = Math.max(1.0, visual.width() * 0.75);
        double height = Math.max(1.0, visual.height() * 0.75);
        return new WindowBounds(
                visual.x() + (visual.width() - width) / 2.0,
                visual.y() + (visual.height() - height) / 2.0,
                width,
                height
        );
    }

    private static List<WindowBounds> validBounds(List<WindowBounds> bounds) {
        if (bounds == null || bounds.isEmpty()) return List.of();

        java.util.ArrayList<WindowBounds> valid = new java.util.ArrayList<>(bounds.size());
        for (WindowBounds value : bounds) {
            if (value != null && value.isValid()) valid.add(value);
        }
        return List.copyOf(valid);
    }

    private static boolean isSufficientlyVisible(
            WindowBounds window,
            List<WindowBounds> screens
    ) {
        double requiredWidth = Math.min(MIN_VISIBLE_WIDTH, window.width());
        double requiredHeight = Math.min(MIN_VISIBLE_HEIGHT, window.height());

        for (WindowBounds screen : screens) {
            double overlapWidth = overlap(
                    window.x(),
                    window.x() + window.width(),
                    screen.x(),
                    screen.x() + screen.width()
            );
            double overlapHeight = overlap(
                    window.y(),
                    window.y() + window.height(),
                    screen.y(),
                    screen.y() + screen.height()
            );
            if (overlapWidth >= requiredWidth && overlapHeight >= requiredHeight) {
                return true;
            }
        }
        return false;
    }

    private static WindowBounds bestScreenForWindow(
            WindowBounds window,
            List<WindowBounds> screens
    ) {
        WindowBounds best = null;
        double bestIntersection = -1.0;
        double bestDistance = Double.POSITIVE_INFINITY;
        double windowCenterX = window.x() + window.width() / 2.0;
        double windowCenterY = window.y() + window.height() / 2.0;

        for (WindowBounds screen : screens) {
            double intersection = intersectionArea(window, screen);
            double distance = squaredDistanceToRectangle(
                    windowCenterX,
                    windowCenterY,
                    screen
            );

            if (intersection > bestIntersection
                    || (intersection == bestIntersection && distance < bestDistance)) {
                best = screen;
                bestIntersection = intersection;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static double intersectionArea(WindowBounds a, WindowBounds b) {
        double width = overlap(
                a.x(),
                a.x() + a.width(),
                b.x(),
                b.x() + b.width()
        );
        double height = overlap(
                a.y(),
                a.y() + a.height(),
                b.y(),
                b.y() + b.height()
        );
        return width * height;
    }

    private static double overlap(
            double firstMin,
            double firstMax,
            double secondMin,
            double secondMax
    ) {
        return Math.max(0.0, Math.min(firstMax, secondMax) - Math.max(firstMin, secondMin));
    }

    private static double squaredDistanceToRectangle(
            double x,
            double y,
            WindowBounds rectangle
    ) {
        double dx = 0.0;
        if (x < rectangle.x()) {
            dx = rectangle.x() - x;
        } else if (x > rectangle.x() + rectangle.width()) {
            dx = x - (rectangle.x() + rectangle.width());
        }

        double dy = 0.0;
        if (y < rectangle.y()) {
            dy = rectangle.y() - y;
        } else if (y > rectangle.y() + rectangle.height()) {
            dy = y - (rectangle.y() + rectangle.height());
        }

        return dx * dx + dy * dy;
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (maximum < minimum) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }

    static boolean resizeSuppressedFor(Object eventTarget) {
        if (!(eventTarget instanceof Node node)) return false;

        Node current = node;
        while (current != null) {
            if (current.getStyleClass().contains(RESIZE_SUPPRESSION_STYLE)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
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
        cancelDrag();
        resizing = false;
        resizeEdge = ResizeEdge.NONE;
        resizeStartBounds = null;
    }

    private void cancelDrag() {
        dragging = false;
        maximizedDragPending = false;
        dragOffsetX = 0.0;
        dragOffsetY = 0.0;
        dragRestoreRatioX = 0.0;
        dragRestoreOffsetY = 0.0;
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
            WindowBounds current = bounds();
            List<WindowBounds> screens = visualScreens();

            WindowBounds selected = bestScreenForWindow(current, validBounds(screens));
            if (selected != null) return selected;

            return toWindowBounds(Screen.getPrimary().getVisualBounds());
        }

        @Override
        public WindowBounds visualBoundsAt(double pointerX, double pointerY) {
            return screenForPoint(pointerX, pointerY, visualScreens())
                    .orElseGet(this::visualBounds);
        }

        private static List<WindowBounds> visualScreens() {
            return Screen.getScreens().stream()
                    .map(Screen::getVisualBounds)
                    .map(StageWindowHost::toWindowBounds)
                    .toList();
        }

        private static WindowBounds toWindowBounds(Rectangle2D bounds) {
            return new WindowBounds(
                    bounds.getMinX(),
                    bounds.getMinY(),
                    bounds.getWidth(),
                    bounds.getHeight()
            );
        }
    }
}
