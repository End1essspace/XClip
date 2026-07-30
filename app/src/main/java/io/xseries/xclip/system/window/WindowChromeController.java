/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.window;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Compatibility-safe window-state controller used by the popup shell.
 *
 * R2.1 keeps the native decorated title bar. This controller centralizes the
 * state transitions and restored-window bounds that the later custom title bar
 * will call, without changing the current visual shell.
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
        dragging = false;
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
        dragging = false;
        return true;
    }

    public boolean restore() {
        if (!host.isMaximized()) return false;

        host.setMaximized(false);
        dragging = false;
        return true;
    }

    public boolean toggleMaximized() {
        return host.isMaximized() ? restore() : maximize();
    }

    /**
     * Hook for the future custom title-bar double-click handler.
     */
    public boolean handleTitleBarDoubleClick() {
        return toggleMaximized();
    }

    /**
     * Starts dragging only in restored mode. R2.2 will wire this hook to the
     * custom title region.
     */
    public boolean beginDrag(double pointerScreenX, double pointerScreenY) {
        if (!host.isShowing() || host.isIconified() || host.isMaximized()) {
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

    public void closeToBackground() {
        dragging = false;
        closeToBackground.run();
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
