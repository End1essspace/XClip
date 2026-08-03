/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.search;

import java.util.Objects;

/**
 * Non-fatal parser diagnostic.
 *
 * Invalid operator fragments fall back to ordinary text instead of making
 * search unusable. The diagnostic is exposed to the search UI.
 */
public record SearchQueryIssue(
        Code code,
        String message,
        int startIndex,
        int endIndex
) {
    public SearchQueryIssue {
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNullElse(message, "").trim();
        if (message.isEmpty()) throw new IllegalArgumentException("message is required");
        if (startIndex < 0) throw new IllegalArgumentException("startIndex cannot be negative");
        if (endIndex < startIndex) throw new IllegalArgumentException("endIndex cannot precede startIndex");
    }

    public enum Code {
        UNTERMINATED_QUOTE,
        MISSING_VALUE,
        INVALID_VALUE,
        UNSUPPORTED_NEGATION
    }
}
