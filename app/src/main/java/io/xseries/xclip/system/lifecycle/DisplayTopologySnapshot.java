/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.lifecycle;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Stable display/DPI fingerprint used to detect topology transitions. */
final class DisplayTopologySnapshot {

    private DisplayTopologySnapshot() {}

    static String capture() {
        try {
            if (GraphicsEnvironment.isHeadless()) return "headless";

            List<String> displays = new ArrayList<>();
            for (GraphicsDevice device : GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getScreenDevices()) {
                GraphicsConfiguration configuration = device.getDefaultConfiguration();
                Rectangle bounds = configuration.getBounds();
                AffineTransform transform = configuration.getDefaultTransform();
                displays.add(
                        device.getIDstring()
                                + ':' + bounds.x
                                + ':' + bounds.y
                                + ':' + bounds.width
                                + ':' + bounds.height
                                + ':' + transform.getScaleX()
                                + ':' + transform.getScaleY()
                );
            }
            displays.sort(Comparator.naturalOrder());
            return Toolkit.getDefaultToolkit().getScreenResolution()
                    + "|" + String.join("|", displays);
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }
}
