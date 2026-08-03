/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.lifecycle;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import java.util.Locale;

/** Best-effort identity of the current Windows Explorer shell process. */
final class WindowsShellStateProbe {

    private interface ShellApi extends Library {
        ShellApi INSTANCE = Native.load("user32", ShellApi.class);

        Pointer GetShellWindow();
        int GetWindowThreadProcessId(Pointer window, IntByReference processId);
    }

    long shellProcessId() {
        if (!isWindows()) return 0L;
        try {
            Pointer shellWindow = ShellApi.INSTANCE.GetShellWindow();
            if (shellWindow == null) return 0L;

            IntByReference processId = new IntByReference();
            ShellApi.INSTANCE.GetWindowThreadProcessId(shellWindow, processId);
            return Integer.toUnsignedLong(processId.getValue());
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }
}
