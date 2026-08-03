/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.lifecycle;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.util.Locale;

/**
 * Best-effort Windows lock-state probe using the switchable input desktop.
 * Failures are fail-open so a native API issue cannot disable XClip runtime.
 */
final class WindowsSessionStateProbe {

    private static final int DESKTOP_SWITCHDESKTOP = 0x0100;

    private interface DesktopApi extends Library {
        DesktopApi INSTANCE = Native.load("user32", DesktopApi.class);

        Pointer OpenInputDesktop(int flags, boolean inherit, int desiredAccess);
        boolean SwitchDesktop(Pointer desktop);
        boolean CloseDesktop(Pointer desktop);
    }

    boolean isSessionUnlocked() {
        if (!isWindows()) return true;

        Pointer desktop = null;
        try {
            desktop = DesktopApi.INSTANCE.OpenInputDesktop(
                    0,
                    false,
                    DESKTOP_SWITCHDESKTOP
            );
            return desktop != null && DesktopApi.INSTANCE.SwitchDesktop(desktop);
        } catch (Throwable ignored) {
            return true;
        } finally {
            if (desktop != null) {
                try {
                    DesktopApi.INSTANCE.CloseDesktop(desktop);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }
}
