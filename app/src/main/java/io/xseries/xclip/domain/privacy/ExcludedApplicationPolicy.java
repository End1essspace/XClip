/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.privacy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Immutable process-name exclusion policy for clipboard capture.
 *
 * Matching is intentionally limited to the case-insensitive executable basename.
 * Paths are accepted as input but only their final executable name is retained.
 */
public record ExcludedApplicationPolicy(List<String> executableNames) {

    public static final int MAX_APPLICATIONS = 128;
    public static final int MAX_EXECUTABLE_NAME_LENGTH = 260;

    public ExcludedApplicationPolicy {
        Collection<String> source = executableNames == null ? List.of() : executableNames;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();

        for (String entry : source) {
            Optional<String> name = normalizeExecutableName(entry);
            name.ifPresent(normalized::add);
            if (normalized.size() > MAX_APPLICATIONS) {
                throw new IllegalArgumentException(
                        "Excluded applications cannot exceed " + MAX_APPLICATIONS + " entries"
                );
            }
        }

        executableNames = List.copyOf(normalized);
    }

    public static ExcludedApplicationPolicy defaults() {
        return new ExcludedApplicationPolicy(List.of());
    }

    /**
     * Best-effort normalization for persisted config values. Invalid entries are
     * discarded individually and excess entries are ignored, preserving all other
     * configuration fields during migration from malformed or manually edited JSON.
     */
    public static ExcludedApplicationPolicy sanitized(Collection<String> entries) {
        if (entries == null || entries.isEmpty()) return defaults();

        List<String> valid = new ArrayList<>();
        for (String entry : entries) {
            try {
                normalizeExecutableName(entry).ifPresent(valid::add);
            } catch (IllegalArgumentException ignored) {
                // Invalid persisted entries must not invalidate the complete config.
            }
            if (valid.size() >= MAX_APPLICATIONS) break;
        }
        return new ExcludedApplicationPolicy(valid);
    }

    public static ExcludedApplicationPolicy fromMultilineText(String text) {
        if (text == null || text.isBlank()) return defaults();

        String[] lines = text.split("\\R", -1);
        List<String> entries = new ArrayList<>(lines.length);
        for (String line : lines) entries.add(line);
        return new ExcludedApplicationPolicy(entries);
    }

    public boolean excludes(String executableOrPath) {
        return normalizeExecutableName(executableOrPath)
                .map(executableNames::contains)
                .orElse(false);
    }

    public boolean empty() {
        return executableNames.isEmpty();
    }

    public String toMultilineText() {
        return String.join(System.lineSeparator(), executableNames);
    }

    /**
     * Converts an executable name or path to a stable lower-case basename.
     * A missing extension is treated as a Windows executable name and receives .exe.
     */
    public static Optional<String> normalizeExecutableName(String rawValue) {
        if (rawValue == null) return Optional.empty();

        String value = rawValue.strip();
        if (value.isEmpty()) return Optional.empty();

        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1).strip();
            }
        }
        if (value.isEmpty()) return Optional.empty();

        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(
                        "Excluded application contains a control character"
                );
            }
        }

        String pathNormalized = value.replace('\\', '/');
        int slash = pathNormalized.lastIndexOf('/');
        String basename = slash >= 0
                ? pathNormalized.substring(slash + 1).strip()
                : pathNormalized;

        if (basename.isEmpty()) return Optional.empty();
        if (basename.indexOf('*') >= 0 || basename.indexOf('?') >= 0) {
            throw new IllegalArgumentException(
                    "Excluded applications do not support wildcard names"
            );
        }
        if (basename.length() > MAX_EXECUTABLE_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Executable name cannot exceed "
                            + MAX_EXECUTABLE_NAME_LENGTH
                            + " characters"
            );
        }

        String normalized = basename.toLowerCase(Locale.ROOT);
        if (normalized.indexOf('.') < 0) normalized += ".exe";
        return Optional.of(normalized);
    }
}
