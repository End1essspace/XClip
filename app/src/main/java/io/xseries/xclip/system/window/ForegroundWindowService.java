/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.window;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Captures the external foreground window that was active before XClip opened
 * and restores it immediately before a direct paste.
 */
public final class ForegroundWindowService {

    private final AtomicReference<WinDef.HWND> capturedTarget = new AtomicReference<>();

    public boolean captureCurrentExternalWindow() {
        if (!isWindows()) {
            clearCapturedTarget();
            return false;
        }

        try {
            WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
            if (!isUsableExternalWindow(hwnd)) {
                clearCapturedTarget();
                return false;
            }

            capturedTarget.set(hwnd);
            return true;
        } catch (Throwable ignored) {
            clearCapturedTarget();
            return false;
        }
    }

    public boolean hasCapturedTarget() {
        WinDef.HWND hwnd = capturedTarget.get();
        return isUsableExternalWindow(hwnd);
    }

    public boolean restoreCapturedTarget() {
        WinDef.HWND hwnd = capturedTarget.get();
        if (!isUsableExternalWindow(hwnd)) {
            return false;
        }

        try {
            // A captured foreground window cannot be minimized at capture time,
            // so activation is sufficient here and avoids changing its show state.
            return User32.INSTANCE.SetForegroundWindow(hwnd);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void clearCapturedTarget() {
        capturedTarget.set(null);
    }

    private boolean isUsableExternalWindow(WinDef.HWND hwnd) {
        if (!isWindows() || hwnd == null) return false;

        try {
            if (!User32.INSTANCE.IsWindow(hwnd)) return false;
            if (!User32.INSTANCE.IsWindowVisible(hwnd)) return false;

            IntByReference pidRef = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);

            int pid = pidRef.getValue();
            return pid > 0 && pid != Kernel32.INSTANCE.GetCurrentProcessId();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }
}
