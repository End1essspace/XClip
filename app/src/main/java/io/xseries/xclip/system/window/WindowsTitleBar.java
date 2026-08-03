/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.window;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public final class WindowsTitleBar {

    private WindowsTitleBar() {}

    private interface DwmApi extends com.sun.jna.Library {
        DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class);

        int DwmSetWindowAttribute(
                WinDef.HWND hwnd,
                int dwAttribute,
                IntByReference pvAttribute,
                int cbAttribute
        );
    }

    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19;
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;

    // Windows 11 attributes. Safe to try; ignored on unsupported builds.
    private static final int DWMWA_BORDER_COLOR = 34;
    private static final int DWMWA_CAPTION_COLOR = 35;
    private static final int DWMWA_TEXT_COLOR = 36;

    // COLORREF format: 0x00BBGGRR
    private static final int COLOR_CAPTION_DARK = 0x00221915; // #151922
    private static final int COLOR_BORDER_DARK  = 0x0042342D; // #2D3442
    private static final int COLOR_TEXT_LIGHT   = 0x00F3ECE8; // #E8ECF3

    public static void applyDarkTitleBar(Stage stage) {
        if (stage == null) return;
        if (!isWindows()) return;

        // Stage handle can become available only after the native window is fully shown.
        Platform.runLater(() -> applyNow(stage));
    }

    private static void applyNow(Stage stage) {
        try {
            WinDef.HWND hwnd = findWindowHandle(stage);
            if (hwnd == null) return;

            setIntAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, 1);
            setIntAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, 1);

            // Extra polish for Windows 11.
            setIntAttribute(hwnd, DWMWA_CAPTION_COLOR, COLOR_CAPTION_DARK);
            setIntAttribute(hwnd, DWMWA_BORDER_COLOR, COLOR_BORDER_DARK);
            setIntAttribute(hwnd, DWMWA_TEXT_COLOR, COLOR_TEXT_LIGHT);

        } catch (Throwable ignored) {
            // Visual enhancement only.
            // Never break application startup or window opening.
        }
    }

    private static void setIntAttribute(WinDef.HWND hwnd, int attribute, int value) {
        try {
            IntByReference ref = new IntByReference(value);
            DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, ref, Integer.BYTES);
        } catch (Throwable ignored) {
        }
    }

    private static WinDef.HWND findWindowHandle(Stage stage) {
        String expectedTitle = stage.getTitle();
        if (expectedTitle == null || expectedTitle.isBlank()) return null;

        int currentPid = Kernel32.INSTANCE.GetCurrentProcessId();
        AtomicReference<WinDef.HWND> found = new AtomicReference<>();

        User32.INSTANCE.EnumWindows(new WinUser.WNDENUMPROC() {
            @Override
            public boolean callback(WinDef.HWND hwnd, Pointer data) {
                if (hwnd == null) return true;
                if (!User32.INSTANCE.IsWindowVisible(hwnd)) return true;

                IntByReference pidRef = new IntByReference();
                User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);

                if (pidRef.getValue() != currentPid) {
                    return true;
                }

                String title = getWindowTitle(hwnd);
                if (expectedTitle.equals(title)) {
                    found.set(hwnd);
                    return false;
                }

                return true;
            }
        }, null);

        return found.get();
    }

    private static String getWindowTitle(WinDef.HWND hwnd) {
        char[] buffer = new char[512];
        User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
        return Native.toString(buffer);
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }
}