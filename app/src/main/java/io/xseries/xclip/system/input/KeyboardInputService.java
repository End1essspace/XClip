/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.input;

import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.event.KeyEvent;

/**
 * Sends the standard Windows paste shortcut to the currently focused window.
 */
public final class KeyboardInputService {

    public boolean sendPasteShortcut() {
        if (GraphicsEnvironment.isHeadless()) return false;

        try {
            Robot robot = new Robot();
            robot.setAutoDelay(8);

            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_CONTROL);

            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
