/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.privacy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcludedApplicationPolicyTest {

    @Test
    void normalizesPathsQuotesCaseExtensionsAndDuplicates() {
        ExcludedApplicationPolicy policy = new ExcludedApplicationPolicy(List.of(
                " Chrome ",
                "C:\\Program Files\\Google\\Chrome.EXE",
                "\"C:\\Tools\\KeePassXC.exe\"",
                "notepad.exe"
        ));

        assertEquals(
                List.of("chrome.exe", "keepassxc.exe", "notepad.exe"),
                policy.executableNames()
        );
        assertTrue(policy.excludes("CHROME.EXE"));
        assertTrue(policy.excludes("C:\\Windows\\notepad.exe"));
        assertFalse(policy.excludes("firefox.exe"));
    }

    @Test
    void multilineRoundTripIsDeterministic() {
        ExcludedApplicationPolicy policy =
                ExcludedApplicationPolicy.fromMultilineText(
                        "1Password.exe\r\n\r\nKeePassXC\nnotepad.exe"
                );

        assertEquals(
                List.of("1password.exe", "keepassxc.exe", "notepad.exe"),
                policy.executableNames()
        );
        assertEquals(policy, ExcludedApplicationPolicy.fromMultilineText(
                policy.toMultilineText()
        ));
    }

    @Test
    void strictInputRejectsWildcardsAndExcessEntries() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExcludedApplicationPolicy(List.of("*.exe"))
        );

        List<String> tooMany = new ArrayList<>();
        for (int index = 0; index <= ExcludedApplicationPolicy.MAX_APPLICATIONS; index++) {
            tooMany.add("app" + index + ".exe");
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExcludedApplicationPolicy(tooMany)
        );
    }

    @Test
    void persistedSanitizerPreservesValidEntries() {
        ExcludedApplicationPolicy policy = ExcludedApplicationPolicy.sanitized(List.of(
                "chrome.exe",
                "*.exe",
                "C:\\Tools\\KeePassXC.exe"
        ));

        assertEquals(
                List.of("chrome.exe", "keepassxc.exe"),
                policy.executableNames()
        );
    }
}
