/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.window;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WindowChromeControllerTest {

    @Test
    void preservesRestoredBoundsWhileMaximized() {
        FakeWindowHost host = new FakeWindowHost(
                new WindowChromeController.WindowBounds(120, 80, 900, 640)
        );
        WindowChromeController controller =
                new WindowChromeController(host, () -> {});

        assertTrue(controller.captureNormalBounds());
        assertTrue(controller.maximize());
        assertTrue(host.maximized);

        host.bounds = new WindowChromeController.WindowBounds(0, 0, 1920, 1040);

        assertEquals(
                new WindowChromeController.WindowBounds(120, 80, 900, 640),
                controller.persistenceBounds().orElseThrow()
        );
    }

    @Test
    void appliesAndRefreshesRestoredBounds() {
        FakeWindowHost host = new FakeWindowHost(
                new WindowChromeController.WindowBounds(20, 30, 700, 500)
        );
        host.maximized = true;

        WindowChromeController controller =
                new WindowChromeController(host, () -> {});
        WindowChromeController.WindowBounds restored =
                new WindowChromeController.WindowBounds(-1100, 40, 960, 720);

        assertTrue(controller.applyRestoredBounds(restored));
        assertFalse(host.maximized);
        assertEquals(restored, host.bounds);
        assertEquals(restored, controller.normalBounds().orElseThrow());

        host.bounds = new WindowChromeController.WindowBounds(-1050, 60, 980, 740);
        assertTrue(controller.captureNormalBounds());
        assertEquals(host.bounds, controller.persistenceBounds().orElseThrow());
    }

    @Test
    void supportsMinimizeRestoreAndCloseToBackgroundHooks() {
        FakeWindowHost host = new FakeWindowHost(
                new WindowChromeController.WindowBounds(100, 100, 800, 600)
        );
        AtomicInteger closes = new AtomicInteger();
        WindowChromeController controller =
                new WindowChromeController(host, closes::incrementAndGet);

        assertTrue(controller.minimize());
        assertTrue(host.iconified);
        assertFalse(controller.minimize());

        assertTrue(controller.restoreFromMinimized());
        assertFalse(host.iconified);
        assertFalse(controller.restoreFromMinimized());

        controller.closeToBackground();
        assertEquals(1, closes.get());
    }

    @Test
    void togglesMaximizeThroughFutureTitleBarHook() {
        FakeWindowHost host = new FakeWindowHost(
                new WindowChromeController.WindowBounds(100, 100, 800, 600)
        );
        WindowChromeController controller =
                new WindowChromeController(host, () -> {});

        assertTrue(controller.handleTitleBarDoubleClick());
        assertTrue(host.maximized);

        assertTrue(controller.handleTitleBarDoubleClick());
        assertFalse(host.maximized);
    }

    @Test
    void dragsOnlyRestoredWindowsAndUpdatesNormalBounds() {
        FakeWindowHost host = new FakeWindowHost(
                new WindowChromeController.WindowBounds(100, 80, 800, 600)
        );
        WindowChromeController controller =
                new WindowChromeController(host, () -> {});

        assertTrue(controller.beginDrag(150, 110));
        assertTrue(controller.isDragging());
        assertTrue(controller.dragTo(430, 360));
        assertEquals(380, host.bounds.x());
        assertEquals(330, host.bounds.y());

        controller.endDrag();

        assertFalse(controller.isDragging());
        assertEquals(host.bounds, controller.normalBounds().orElseThrow());

        host.maximized = true;
        assertFalse(controller.beginDrag(200, 200));
        assertFalse(controller.dragTo(300, 300));
    }

    @Test
    void rejectsInvalidBoundsAndExposesVisualBounds() {
        FakeWindowHost host = new FakeWindowHost(
                new WindowChromeController.WindowBounds(10, 20, 600, 400)
        );
        WindowChromeController controller =
                new WindowChromeController(host, () -> {});

        controller.rememberNormalBounds(
                new WindowChromeController.WindowBounds(0, 0, -1, 500)
        );
        assertTrue(controller.normalBounds().isEmpty());

        WindowChromeController.WindowBounds visual =
                new WindowChromeController.WindowBounds(-1920, 0, 1920, 1040);
        host.visualBounds = visual;

        assertEquals(visual, controller.currentVisualBounds());
    }

    @Test
    void detectsResizeEdgesWithCornerPriority() {
        assertEquals(
                WindowChromeController.ResizeEdge.NORTH_WEST,
                WindowChromeController.resizeEdgeFor(2, 2, 800, 600, 6)
        );
        assertEquals(
                WindowChromeController.ResizeEdge.SOUTH_EAST,
                WindowChromeController.resizeEdgeFor(798, 598, 800, 600, 6)
        );
        assertEquals(
                WindowChromeController.ResizeEdge.EAST,
                WindowChromeController.resizeEdgeFor(799, 300, 800, 600, 6)
        );
        assertEquals(
                WindowChromeController.ResizeEdge.NONE,
                WindowChromeController.resizeEdgeFor(400, 300, 800, 600, 6)
        );
    }

    @Test
    void resizesFromSouthEastAndPersistsResult() {
        FakeWindowHost host = new FakeWindowHost(
                new WindowChromeController.WindowBounds(100, 80, 800, 600)
        );
        WindowChromeController controller =
                new WindowChromeController(host, () -> {});

        assertTrue(controller.beginResize(
                WindowChromeController.ResizeEdge.SOUTH_EAST,
                900,
                680,
                500,
                300
        ));
        assertTrue(controller.isResizing());
        assertTrue(controller.resizeTo(1040, 790));

        assertEquals(
                new WindowChromeController.WindowBounds(100, 80, 940, 710),
                host.bounds
        );

        controller.endResize();

        assertFalse(controller.isResizing());
        assertEquals(host.bounds, controller.normalBounds().orElseThrow());
    }

    @Test
    void clampsNorthWestResizeToMinimumSize() {
        WindowChromeController.WindowBounds resized =
                WindowChromeController.resizedBounds(
                        new WindowChromeController.WindowBounds(100, 100, 800, 600),
                        WindowChromeController.ResizeEdge.NORTH_WEST,
                        700,
                        500,
                        500,
                        300
                );

        assertEquals(
                new WindowChromeController.WindowBounds(400, 400, 500, 300),
                resized
        );
    }

    @Test
    void rejectsResizeWhileMaximizedOrDragging() {
        FakeWindowHost host = new FakeWindowHost(
                new WindowChromeController.WindowBounds(100, 80, 800, 600)
        );
        WindowChromeController controller =
                new WindowChromeController(host, () -> {});

        host.maximized = true;
        assertFalse(controller.beginResize(
                WindowChromeController.ResizeEdge.EAST,
                900,
                300,
                500,
                300
        ));

        host.maximized = false;
        assertTrue(controller.beginDrag(150, 100));
        assertFalse(controller.beginResize(
                WindowChromeController.ResizeEdge.EAST,
                900,
                300,
                500,
                300
        ));
    }

    private static final class FakeWindowHost
            implements WindowChromeController.WindowHost {

        private WindowChromeController.WindowBounds bounds;
        private WindowChromeController.WindowBounds visualBounds =
                new WindowChromeController.WindowBounds(0, 0, 1920, 1040);
        private boolean showing = true;
        private boolean iconified;
        private boolean maximized;

        private FakeWindowHost(WindowChromeController.WindowBounds bounds) {
            this.bounds = bounds;
        }

        @Override
        public WindowChromeController.WindowBounds bounds() {
            return bounds;
        }

        @Override
        public void setBounds(WindowChromeController.WindowBounds bounds) {
            this.bounds = bounds;
        }

        @Override
        public void setPosition(double x, double y) {
            bounds = new WindowChromeController.WindowBounds(
                    x,
                    y,
                    bounds.width(),
                    bounds.height()
            );
        }

        @Override
        public boolean isShowing() {
            return showing;
        }

        @Override
        public boolean isIconified() {
            return iconified;
        }

        @Override
        public void setIconified(boolean iconified) {
            this.iconified = iconified;
        }

        @Override
        public boolean isMaximized() {
            return maximized;
        }

        @Override
        public void setMaximized(boolean maximized) {
            this.maximized = maximized;
        }

        @Override
        public WindowChromeController.WindowBounds visualBounds() {
            return visualBounds;
        }
    }
}
