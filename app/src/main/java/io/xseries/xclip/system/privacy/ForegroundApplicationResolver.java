/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.privacy;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import io.xseries.xclip.domain.privacy.ExcludedApplicationPolicy;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the Windows foreground process together with its window title.
 *
 * Resolution is best effort. Permission failures, transient window destruction,
 * unsupported operating systems, and unavailable process metadata return an empty
 * or partially identified result rather than blocking clipboard capture.
 */
public final class ForegroundApplicationResolver {

    public Optional<ForegroundApplication> resolve() {
        if (!isWindows()) return Optional.empty();

        try {
            WinDef.HWND window = User32.INSTANCE.GetForegroundWindow();
            if (window == null || !User32.INSTANCE.IsWindow(window)) {
                return Optional.empty();
            }

            IntByReference processId = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(window, processId);
            int pid = processId.getValue();
            if (pid <= 0) return Optional.empty();

            String executableName = resolveExecutableName(pid).orElse(null);
            String windowTitle = resolveWindowTitle(window);

            return Optional.of(new ForegroundApplication(
                    pid,
                    executableName,
                    windowTitle
            ));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    static Optional<String> resolveExecutableName(int processId) {
        if (processId <= 0) return Optional.empty();

        try {
            Optional<ProcessHandle> process = ProcessHandle.of(processId);
            if (process.isEmpty()) return Optional.empty();

            ProcessHandle.Info info = process.get().info();
            Optional<String> command = info.command();
            if (command.isPresent()) {
                return ExcludedApplicationPolicy.normalizeExecutableName(command.get());
            }

            return info.commandLine()
                    .flatMap(ForegroundApplicationResolver::executableFromCommandLine)
                    .flatMap(ExcludedApplicationPolicy::normalizeExecutableName);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    static Optional<String> executableFromCommandLine(String commandLine) {
        if (commandLine == null) return Optional.empty();
        String value = commandLine.strip();
        if (value.isEmpty()) return Optional.empty();

        if (value.charAt(0) == '"') {
            int closingQuote = value.indexOf('"', 1);
            if (closingQuote <= 1) return Optional.empty();
            return Optional.of(value.substring(1, closingQuote));
        }

        int firstWhitespace = -1;
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                firstWhitespace = index;
                break;
            }
        }

        return Optional.of(firstWhitespace < 0
                ? value
                : value.substring(0, firstWhitespace));
    }

    private static String resolveWindowTitle(WinDef.HWND window) {
        try {
            int length = User32.INSTANCE.GetWindowTextLength(window);
            if (length <= 0) return "";

            char[] buffer = new char[Math.min(length + 1, 32_768)];
            int copied = User32.INSTANCE.GetWindowText(window, buffer, buffer.length);
            return copied <= 0 ? "" : new String(buffer, 0, copied).strip();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }

    public record ForegroundApplication(
            long processId,
            String executableName,
            String windowTitle
    ) {
        public ForegroundApplication {
            if (processId <= 0) {
                throw new IllegalArgumentException("processId must be positive");
            }
            executableName = ExcludedApplicationPolicy
                    .normalizeExecutableName(executableName)
                    .orElse(null);
            windowTitle = Objects.requireNonNullElse(windowTitle, "").strip();
        }

        public boolean hasExecutableName() {
            return executableName != null && !executableName.isBlank();
        }
    }
}
