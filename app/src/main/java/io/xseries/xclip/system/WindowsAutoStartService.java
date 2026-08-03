/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages Windows startup registration using the per-user HKCU Run key.
 *
 * Enabling is self-healing: a stale path left by an MSI upgrade or moved
 * installation is replaced with the current launcher command.
 */
public final class WindowsAutoStartService {

    private static final String RUN_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE_NAME = "XClip";

    private WindowsAutoStartService() {}

    public static void enable(Path launchPath) throws IOException, InterruptedException {
        String expected = buildLaunchCommand(launchPath);
        Optional<String> registered = registeredCommand();
        if (registered.isPresent()
                && commandsEquivalent(registered.get(), expected)) {
            return;
        }

        Process process = new ProcessBuilder(
                "reg",
                "add",
                RUN_KEY,
                "/v", VALUE_NAME,
                "/t", "REG_SZ",
                "/d", expected,
                "/f"
        ).redirectErrorStream(true).start();
        int exit = process.waitFor();
        if (exit != 0) {
            String output = readProcessOutput(process);
            throw new IOException(
                    "Failed to enable autostart (exit=" + exit + "): " + output
            );
        }
    }

    public static void disable() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "reg",
                "delete",
                RUN_KEY,
                "/v", VALUE_NAME,
                "/f"
        ).redirectErrorStream(true).start();
        process.waitFor();
        // A missing value is already the desired state.
    }

    public static boolean isEnabled() {
        return registeredCommand().isPresent();
    }

    public static boolean pointsTo(Path launchPath) {
        try {
            String expected = buildLaunchCommand(launchPath);
            return registeredCommand()
                    .map(actual -> commandsEquivalent(actual, expected))
                    .orElse(false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static RegistrationStatus inspect(Path launchPath) {
        String expected = buildLaunchCommand(launchPath);
        Optional<String> actual = registeredCommand();
        return new RegistrationStatus(
                actual.isPresent(),
                actual.map(value -> commandsEquivalent(value, expected)).orElse(false),
                actual.orElse(""),
                expected
        );
    }

    static Optional<String> registeredCommand() {
        try {
            Process process = new ProcessBuilder(
                    "reg",
                    "query",
                    RUN_KEY,
                    "/v", VALUE_NAME
            ).redirectErrorStream(true).start();
            String output = readProcessOutput(process);
            int exit = process.waitFor();
            return exit == 0
                    ? parseRegisteredCommand(output)
                    : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    static Optional<String> parseRegisteredCommand(String output) {
        if (output == null || output.isBlank()) return Optional.empty();

        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.toLowerCase(Locale.ROOT)
                    .startsWith(VALUE_NAME.toLowerCase(Locale.ROOT))) {
                continue;
            }

            int type = indexOfRegistryType(trimmed);
            if (type < 0) continue;
            int valueStart = type;
            while (valueStart < trimmed.length()
                    && !Character.isWhitespace(trimmed.charAt(valueStart))) {
                valueStart++;
            }
            while (valueStart < trimmed.length()
                    && Character.isWhitespace(trimmed.charAt(valueStart))) {
                valueStart++;
            }
            if (valueStart < trimmed.length()) {
                return Optional.of(trimmed.substring(valueStart).trim());
            }
        }
        return Optional.empty();
    }

    private static int indexOfRegistryType(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        int regular = upper.indexOf("REG_SZ");
        int expanded = upper.indexOf("REG_EXPAND_SZ");
        if (regular < 0) return expanded;
        if (expanded < 0) return regular;
        return Math.min(regular, expanded);
    }

    static String buildLaunchCommand(Path launchPath) {
        File file = Objects.requireNonNull(launchPath, "launchPath")
                .toAbsolutePath()
                .normalize()
                .toFile();

        if (!file.exists()) {
            throw new IllegalStateException("Launch file does not exist: " + launchPath);
        }

        String absolute = file.getAbsolutePath();
        if (absolute.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            String javaHome = System.getProperty("java.home");
            File javaw = Path.of(javaHome, "bin", "javaw.exe").toFile();
            String javawPath = javaw.exists()
                    ? quote(javaw.getAbsolutePath())
                    : "javaw";
            return javawPath + " -jar " + quote(absolute);
        }

        return quote(absolute);
    }

    static boolean commandsEquivalent(String first, String second) {
        return normalizeCommand(first).equalsIgnoreCase(normalizeCommand(second));
    }

    private static String normalizeCommand(String command) {
        if (command == null) return "";
        return command.trim()
                .replace('/', '\\')
                .replaceAll("\\s+", " ");
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    private static String readProcessOutput(Process process) throws IOException {
        return new String(
                process.getInputStream().readAllBytes(),
                Charset.defaultCharset()
        ).trim();
    }

    public record RegistrationStatus(
            boolean present,
            boolean current,
            String registeredCommand,
            String expectedCommand
    ) {}
}
