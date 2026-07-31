/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.service;

import java.util.Locale;
import java.util.Objects;

/**
 * Shared normalization and validation contract for user-defined tag names.
 *
 * The persistence layer and the tag editor deliberately use the same policy so
 * inline validation can never accept a value that SQLite later rejects.
 */
public final class TagNamePolicy {

    public static final int MAX_NAME_LENGTH = 64;

    private TagNamePolicy() {}

    public static NormalizedTagName normalize(String rawName) {
        if (rawName == null) {
            throw new IllegalArgumentException("Tag name is required");
        }

        StringBuilder out = new StringBuilder(rawName.length());
        boolean pendingSpace = false;

        for (int index = 0; index < rawName.length(); index++) {
            char value = rawName.charAt(index);

            if (Character.isWhitespace(value)) {
                pendingSpace = out.length() > 0;
                continue;
            }
            if (Character.isISOControl(value)) {
                throw new IllegalArgumentException("Tag name contains a control character");
            }

            if (pendingSpace) out.append(' ');
            pendingSpace = false;
            out.append(value);
        }

        String displayName = out.toString().strip();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("Tag name cannot be blank");
        }
        if (displayName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Tag name cannot exceed " + MAX_NAME_LENGTH + " characters"
            );
        }

        return new NormalizedTagName(
                displayName,
                displayName.toLowerCase(Locale.ROOT)
        );
    }

    public record NormalizedTagName(String displayName, String identity) {
        public NormalizedTagName {
            displayName = requireText(displayName, "displayName");
            identity = requireText(identity, "identity");
        }

        private static String requireText(String value, String field) {
            String normalized = Objects.requireNonNullElse(value, "").trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(field + " is required");
            }
            return normalized;
        }
    }
}
