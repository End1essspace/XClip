/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.privacy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForegroundApplicationResolverTest {

    @Test
    void extractsQuotedExecutableFromCommandLine() {
        assertEquals(
                "C:\\Program Files\\Example App\\example.exe",
                ForegroundApplicationResolver.executableFromCommandLine(
                        "\"C:\\Program Files\\Example App\\example.exe\" --private"
                ).orElseThrow()
        );
    }

    @Test
    void extractsUnquotedExecutableFromCommandLine() {
        assertEquals(
                "notepad.exe",
                ForegroundApplicationResolver.executableFromCommandLine(
                        "notepad.exe C:\\notes.txt"
                ).orElseThrow()
        );
    }

    @Test
    void blankOrUnclosedCommandLineReturnsEmpty() {
        assertTrue(
                ForegroundApplicationResolver.executableFromCommandLine("   ").isEmpty()
        );
        assertTrue(
                ForegroundApplicationResolver.executableFromCommandLine(
                        "\"C:\\broken path.exe --flag"
                ).isEmpty()
        );
    }
}
