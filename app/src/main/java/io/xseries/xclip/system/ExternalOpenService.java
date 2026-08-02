
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system;

import io.xseries.xclip.util.TextValues;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Performs the two safe external actions supported by XClip:
 * opening HTTP(S) URLs and revealing filesystem paths.
 *
 * Files are revealed in Explorer instead of being launched. This prevents a
 * copied executable or script path from being executed by a type action.
 */
public final class ExternalOpenService {

    public enum OpenResult {
        OPENED,
        INVALID_INPUT,
        NOT_FOUND,
        UNSUPPORTED,
        FAILED
    }

    private static final Pattern WINDOWS_ENV_TOKEN =
            Pattern.compile("%([A-Za-z_][A-Za-z0-9_]*)%");

    public OpenResult openUrl(String rawUrl) {
        URI uri = normalizeHttpUri(rawUrl);
        if (uri == null) return OpenResult.INVALID_INPUT;

        try {
            if (!Desktop.isDesktopSupported()) return OpenResult.UNSUPPORTED;
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) return OpenResult.UNSUPPORTED;

            desktop.browse(uri);
            return OpenResult.OPENED;
        } catch (Exception ignored) {
            return OpenResult.FAILED;
        }
    }

    public OpenResult revealPath(String rawPath) {
        Path path = resolvePath(rawPath, System.getenv());
        if (path == null) return OpenResult.INVALID_INPUT;
        if (!Files.exists(path)) return OpenResult.NOT_FOUND;

        try {
            if (isWindows()) {
                if (Files.isDirectory(path)) {
                    new ProcessBuilder("explorer.exe", path.toString()).start();
                } else {
                    new ProcessBuilder("explorer.exe", "/select," + path).start();
                }
                return OpenResult.OPENED;
            }

            if (!Desktop.isDesktopSupported()) return OpenResult.UNSUPPORTED;
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.OPEN)) return OpenResult.UNSUPPORTED;

            Path target = Files.isDirectory(path) ? path : path.getParent();
            if (target == null) return OpenResult.INVALID_INPUT;
            desktop.open(target.toFile());
            return OpenResult.OPENED;
        } catch (Exception ignored) {
            return OpenResult.FAILED;
        }
    }

    static URI normalizeHttpUri(String rawUrl) {
        String value = stripOuterQuotes(rawUrl);
        if (value == null || value.isBlank() || TextValues.containsLineBreak(value) || value.indexOf(' ') >= 0) {
            return null;
        }

        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null) return null;

            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
                return null;
            }

            if (uri.getHost() == null || uri.getHost().isBlank()) return null;
            return uri;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static Path resolvePath(String rawPath, Map<String, String> environment) {
        String value = stripOuterQuotes(rawPath);
        if (value == null || value.isBlank() || TextValues.containsLineBreak(value)) return null;

        try {
            String expanded = expandWindowsEnvironment(value.trim(), environment);
            Path path;

            if (expanded.regionMatches(true, 0, "file:", 0, 5)) {
                path = Path.of(URI.create(expanded));
            } else {
                path = Path.of(expanded);
            }

            return path.toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String expandWindowsEnvironment(String value, Map<String, String> environment) {
        if (environment == null || environment.isEmpty() || value.indexOf('%') < 0) {
            return value;
        }

        Matcher matcher = WINDOWS_ENV_TOKEN.matcher(value);
        StringBuffer out = new StringBuffer(value.length());

        while (matcher.find()) {
            String replacement = findEnvironmentValue(environment, matcher.group(1));
            if (replacement == null) replacement = matcher.group(0);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String findEnvironmentValue(Map<String, String> environment, String name) {
        String exact = environment.get(name);
        if (exact != null) return exact;

        for (Map.Entry<String, String> entry : environment.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private static String stripOuterQuotes(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }
}
