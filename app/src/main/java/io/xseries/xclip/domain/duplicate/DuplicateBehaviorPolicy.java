

/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.duplicate;

import io.xseries.xclip.util.TextValues;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable duplicate-detection and duplicate-position policy.
 *
 * The model remains independent from configuration persistence, SQLite, and JavaFX.
 */
public record DuplicateBehaviorPolicy(
        RecentDuplicatePosition recentDuplicatePosition,
        PinnedDuplicatePosition pinnedDuplicatePosition,
        WhitespaceMode whitespaceMode,
        CaseSensitivity caseSensitivity,
        long duplicateWindowMillis,
        boolean exactContentMode
) {

    /**
     * A zero window means that matching is not limited by age.
     */
    public static final long UNLIMITED_WINDOW = 0L;

    public DuplicateBehaviorPolicy {
        recentDuplicatePosition = Objects.requireNonNull(
                recentDuplicatePosition,
                "recentDuplicatePosition"
        );
        pinnedDuplicatePosition = Objects.requireNonNull(
                pinnedDuplicatePosition,
                "pinnedDuplicatePosition"
        );
        whitespaceMode = Objects.requireNonNull(whitespaceMode, "whitespaceMode");
        caseSensitivity = Objects.requireNonNull(caseSensitivity, "caseSensitivity");
        if (duplicateWindowMillis < 0) {
            throw new IllegalArgumentException("duplicateWindowMillis cannot be negative");
        }
    }

    /**
     * Preserves the behavior that existed before configurable duplicate policies:
     * normalized whitespace, case-sensitive matching, unlimited duplicate age,
     * RECENT duplicates move to the top, and PINNED duplicates keep manual order.
     */
    public static DuplicateBehaviorPolicy defaults() {
        return new DuplicateBehaviorPolicy(
                RecentDuplicatePosition.MOVE_TO_TOP,
                PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                WhitespaceMode.NORMALIZE,
                CaseSensitivity.SENSITIVE,
                UNLIMITED_WINDOW,
                false
        );
    }

    /**
     * Produces the deterministic equality key used by the domain policy.
     *
     * Exact-content mode intentionally overrides whitespace and case options.
     */
    public String canonicalKey(String content) {
        String value = Objects.requireNonNull(content, "content");

        if (exactContentMode) {
            return value;
        }

        if (whitespaceMode == WhitespaceMode.NORMALIZE) {
            value = normalizeWhitespace(value);
        }

        if (caseSensitivity == CaseSensitivity.INSENSITIVE) {
            value = value.toLowerCase(Locale.ROOT);
        }

        return value;
    }

    public boolean matches(String existingContent, String incomingContent) {
        return canonicalKey(existingContent).equals(canonicalKey(incomingContent));
    }

    /**
     * Returns true when an existing match is young enough to be treated as one
     * duplicate occurrence. The boundary is inclusive.
     */
    public boolean withinDuplicateWindow(long existingLastCopiedAt, long now) {
        if (existingLastCopiedAt < 0 || now < 0) {
            throw new IllegalArgumentException("timestamps cannot be negative");
        }
        if (duplicateWindowMillis == UNLIMITED_WINDOW) {
            return true;
        }
        if (now < existingLastCopiedAt) {
            return false;
        }
        return now - existingLastCopiedAt <= duplicateWindowMillis;
    }
    public static String normalizeWhitespace(String value) {
        return TextValues.collapseWhitespace(value);
    }

    public enum RecentDuplicatePosition {
        MOVE_TO_TOP,
        PRESERVE_EXISTING_POSITION
    }

    public enum PinnedDuplicatePosition {
        PRESERVE_PIN_POSITION,
        MOVE_PIN_TO_TOP
    }

    public enum WhitespaceMode {
        NORMALIZE,
        PRESERVE
    }

    public enum CaseSensitivity {
        SENSITIVE,
        INSENSITIVE
    }
}
