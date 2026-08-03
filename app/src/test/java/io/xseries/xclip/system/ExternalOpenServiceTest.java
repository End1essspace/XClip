/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class ExternalOpenServiceTest {

    @Test
    void acceptsOnlyHttpAndHttpsUrlsWithHosts() {
        assertEquals(URI.create("https://example.com/path?q=1"),
                ExternalOpenService.normalizeHttpUri("https://example.com/path?q=1"));
        assertEquals(URI.create("http://example.com"),
                ExternalOpenService.normalizeHttpUri("\"http://example.com\""));

        assertNull(ExternalOpenService.normalizeHttpUri("file:///C:/Temp/test.txt"));
        assertNull(ExternalOpenService.normalizeHttpUri("javascript:alert(1)"));
        assertNull(ExternalOpenService.normalizeHttpUri("https://example.com/a b"));
        assertNull(ExternalOpenService.normalizeHttpUri("https:///missing-host"));
    }

    @Test
    void resolvesQuotedAndFileUriPathsWithoutOpeningThem() {
        Path quoted = ExternalOpenService.resolvePath("\"build/output.txt\"", Map.of());
        assertNotNull(quoted);
        assertTrue(quoted.isAbsolute());
        assertTrue(quoted.endsWith(Path.of("build", "output.txt")));

        Path expected = Path.of(System.getProperty("java.io.tmpdir"), "xclip-test.txt")
                .toAbsolutePath().normalize();
        Path fromUri = ExternalOpenService.resolvePath(expected.toUri().toString(), Map.of());
        assertEquals(expected, fromUri);
    }

    @Test
    void expandsWindowsStyleEnvironmentTokensCaseInsensitively() {
        Path resolved = ExternalOpenService.resolvePath(
                "%USERPROFILE%\\Documents\\XClip.txt",
                Map.of("UserProfile", "C:\\Users\\Tester")
        );

        assertNotNull(resolved);
        assertTrue(resolved.toString().replace('/', '\\')
                .endsWith("C:\\Users\\Tester\\Documents\\XClip.txt"));
    }

    @Test
    void rejectsMultilineAndMalformedPaths() {
        assertNull(ExternalOpenService.resolvePath("C:\\Temp\ncalc.exe", Map.of()));
        assertNull(ExternalOpenService.resolvePath("\u0000", Map.of()));
    }
}
