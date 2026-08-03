/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsAutoStartServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void executableLaunchCommandIsQuotedAndNormalized() throws Exception {
        Path executable = Files.createFile(tempDir.resolve("XClip App.exe"));
        String command = WindowsAutoStartService.buildLaunchCommand(executable);

        assertEquals(
                "\"" + executable.toAbsolutePath().normalize() + "\"",
                command
        );
        assertTrue(WindowsAutoStartService.commandsEquivalent(
                command.toUpperCase().replace('\\', '/'),
                "  " + command + "  "
        ));
    }

    @Test
    void registryQueryParserExtractsFullCommand() {
        String output = """
                HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run
                    XClip    REG_SZ    \"C:\\Program Files\\XClip\\XClip.exe\"
                """;

        assertEquals(
                "\"C:\\Program Files\\XClip\\XClip.exe\"",
                WindowsAutoStartService.parseRegisteredCommand(output).orElseThrow()
        );
    }
}
