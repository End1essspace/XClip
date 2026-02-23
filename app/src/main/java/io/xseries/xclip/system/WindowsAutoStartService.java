/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Manages Windows startup registration using HKCU Run key.
 * No admin rights required.
 */
public final class WindowsAutoStartService {

    private static final String RUN_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    private static final String VALUE_NAME = "XClip";

    private WindowsAutoStartService() {}

    public static void enable(Path launchPath) throws IOException, InterruptedException {
        String command = buildLaunchCommand(launchPath);

        ProcessBuilder pb = new ProcessBuilder(
                "reg",
                "add",
                RUN_KEY,
                "/v", VALUE_NAME,
                "/t", "REG_SZ",
                "/d", command,
                "/f"
        );

        Process p = pb.start();
        int exit = p.waitFor();

        if (exit != 0) {
            throw new IOException("Failed to enable autostart (exit=" + exit + ")");
        }
    }

    public static void disable() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "reg",
                "delete",
                RUN_KEY,
                "/v", VALUE_NAME,
                "/f"
        );

        Process p = pb.start();
        p.waitFor(); // ignore exit code if value does not exist
    }

    public static boolean isEnabled() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "reg",
                    "query",
                    RUN_KEY,
                    "/v", VALUE_NAME
            );

            Process p = pb.start();
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String buildLaunchCommand(Path launchPath) {
        File file = launchPath.toFile();

        if (!file.exists()) {
            throw new IllegalStateException("Launch file does not exist: " + launchPath);
        }

        String absolute = file.getAbsolutePath();

        if (absolute.endsWith(".jar")) {
            // Use absolute javaw.exe so Windows autostart doesn't depend on PATH
            String javaHome = System.getProperty("java.home");
            File javaw = Path.of(javaHome, "bin", "javaw.exe").toFile();

            String javawPath = javaw.exists()
                    ? "\"" + javaw.getAbsolutePath() + "\""
                    : "javaw"; // fallback (should rarely happen)

            return javawPath + " -jar \"" + absolute + "\"";
        }

        // Assume exe
        return "\"" + absolute + "\"";
    }
}
