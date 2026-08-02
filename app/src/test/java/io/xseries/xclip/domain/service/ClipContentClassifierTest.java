
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.service;

import io.xseries.xclip.domain.model.ClipContentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClipContentClassifierTest {

    @Test
    void detectsWebUrlsWithoutTreatingFileUrisAsWebUrls() {
        assertEquals(
                ClipContentType.URL,
                ClipContentClassifier.classify("https://github.com/End1essspace/XClip?tab=readme")
        );
        assertEquals(
                ClipContentType.PATH,
                ClipContentClassifier.classify("file:///C:/Users/Test/Documents/readme.txt")
        );
    }

    @Test
    void detectsWindowsUncEnvironmentAndUnixPaths() {
        assertEquals(
                ClipContentType.PATH,
                ClipContentClassifier.classify("D:\\projects\\GitHub\\XC\\app\\build.gradle.kts")
        );
        assertEquals(
                ClipContentType.PATH,
                ClipContentClassifier.classify("\\\\server\\share\\folder\\report.txt")
        );
        assertEquals(
                ClipContentType.PATH,
                ClipContentClassifier.classify("%USERPROFILE%\\Desktop\\report.txt")
        );
        assertEquals(
                ClipContentType.PATH,
                ClipContentClassifier.classify("/usr/local/bin/python3")
        );
    }

    @Test
    void validatesJsonInsteadOfOnlyCheckingOuterBrackets() {
        assertEquals(
                ClipContentType.JSON,
                ClipContentClassifier.classify("""
                        {
                          "name": "XClip",
                          "enabled": true,
                          "limits": [50, 200, null]
                        }
                        """)
        );
        assertEquals(
                ClipContentType.JSON,
                ClipContentClassifier.classify("[1, 2, {\"ok\": false}]")
        );
        assertEquals(
                ClipContentType.TEXT,
                ClipContentClassifier.classify("{not valid json}")
        );
        assertEquals(
                ClipContentType.TEXT,
                ClipContentClassifier.classify("[1, 2,]")
        );
    }

    @Test
    void detectsShellAndPowerShellCommandsUsingStrongSignatures() {
        assertEquals(
                ClipContentType.COMMAND,
                ClipContentClassifier.classify("git status --short")
        );
        assertEquals(
                ClipContentType.COMMAND,
                ClipContentClassifier.classify(".\\gradlew.bat clean test --no-daemon")
        );
        assertEquals(
                ClipContentType.COMMAND,
                ClipContentClassifier.classify("Get-Process | Sort-Object CPU -Descending")
        );
        assertEquals(
                ClipContentType.COMMAND,
                ClipContentClassifier.classify("PS D:\\projects\\GitHub\\XC> git pull --rebase origin main")
        );
    }

    @Test
    void detectsRepresentativeProgrammingAndSqlSnippets() {
        assertEquals(
                ClipContentType.CODE,
                ClipContentClassifier.classify("""
                        public final class Example {
                            public int value() {
                                return 42;
                            }
                        }
                        """)
        );
        assertEquals(
                ClipContentType.CODE,
                ClipContentClassifier.classify("""
                        def greet(name):
                            return f"Hello, {name}"
                        """)
        );
        assertEquals(
                ClipContentType.CODE,
                ClipContentClassifier.classify("SELECT id, content FROM clip_entries WHERE is_favorite = 1;")
        );
    }

    @Test
    void keepsCommandAndCodeHeuristicsStableWithoutPerCallRegexCompilation() {
        assertEquals(
                ClipContentType.COMMAND,
                ClipContentClassifier.classify("git\tstatus\t--short")
        );
        assertEquals(
                ClipContentType.COMMAND,
                ClipContentClassifier.classify("npm\tinstall\txclip")
        );
        assertEquals(
                ClipContentType.TEXT,
                ClipContentClassifier.classify("git explain")
        );
        assertEquals(
                ClipContentType.CODE,
                ClipContentClassifier.classify("""
                        if (ready) {
                          run();
                        }
                        """)
        );
        assertEquals(
                ClipContentType.TEXT,
                ClipContentClassifier.classify("https://example.com\nnext line")
        );
    }

    @Test
    void keepsOrdinarySentencesAsText() {
        assertEquals(
                ClipContentType.TEXT,
                ClipContentClassifier.classify("Git is a distributed version control system.")
        );
        assertEquals(
                ClipContentType.TEXT,
                ClipContentClassifier.classify("Please send the report after the meeting.")
        );
        assertEquals(
                ClipContentType.TEXT,
                ClipContentClassifier.classify("")
        );
    }
}
