
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.duplicate;

import io.xseries.xclip.util.TextValues;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Objects;

/**
 * Policy-independent SHA-256 keys persisted for duplicate lookup.
 *
 * All four supported equality modes are stored so changing duplicate settings
 * never requires rewriting existing clipboard rows.
 */
public record DuplicateContentKeys(
        String exactHash,
        String exactCaseInsensitiveHash,
        String normalizedHash,
        String normalizedCaseInsensitiveHash
) {

    private static final ThreadLocal<MessageDigest> SHA256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    });

    public DuplicateContentKeys {
        exactHash = TextValues.requireNonBlank(exactHash, "exactHash");
        exactCaseInsensitiveHash = TextValues.requireNonBlank(
                exactCaseInsensitiveHash,
                "exactCaseInsensitiveHash"
        );
        normalizedHash = TextValues.requireNonBlank(normalizedHash, "normalizedHash");
        normalizedCaseInsensitiveHash = TextValues.requireNonBlank(
                normalizedCaseInsensitiveHash,
                "normalizedCaseInsensitiveHash"
        );
    }

    public static DuplicateContentKeys from(String content) {
        String value = Objects.requireNonNull(content, "content");
        String normalized = DuplicateBehaviorPolicy.normalizeWhitespace(value);

        return new DuplicateContentKeys(
                sha256Hex(value),
                sha256Hex(value.toLowerCase(Locale.ROOT)),
                sha256Hex(normalized),
                sha256Hex(normalized.toLowerCase(Locale.ROOT))
        );
    }

    public KeyKind selectedKind(DuplicateBehaviorPolicy policy) {
        DuplicateBehaviorPolicy effective = Objects.requireNonNull(policy, "policy");

        if (effective.exactContentMode()) {
            return KeyKind.EXACT;
        }
        if (effective.whitespaceMode() == DuplicateBehaviorPolicy.WhitespaceMode.PRESERVE) {
            return effective.caseSensitivity() == DuplicateBehaviorPolicy.CaseSensitivity.SENSITIVE
                    ? KeyKind.EXACT
                    : KeyKind.EXACT_CASE_INSENSITIVE;
        }
        return effective.caseSensitivity() == DuplicateBehaviorPolicy.CaseSensitivity.SENSITIVE
                ? KeyKind.NORMALIZED
                : KeyKind.NORMALIZED_CASE_INSENSITIVE;
    }

    public String selectedHash(DuplicateBehaviorPolicy policy) {
        return switch (selectedKind(policy)) {
            case EXACT -> exactHash;
            case EXACT_CASE_INSENSITIVE -> exactCaseInsensitiveHash;
            case NORMALIZED -> normalizedHash;
            case NORMALIZED_CASE_INSENSITIVE -> normalizedCaseInsensitiveHash;
        };
    }

    private static String sha256Hex(String value) {
        MessageDigest digest = SHA256.get();
        digest.reset();
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));

        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte valueByte : bytes) {
            out.append(Character.forDigit((valueByte >>> 4) & 0x0f, 16));
            out.append(Character.forDigit(valueByte & 0x0f, 16));
        }
        return out.toString();
    }


    public enum KeyKind {
        EXACT,
        EXACT_CASE_INSENSITIVE,
        NORMALIZED,
        NORMALIZED_CASE_INSENSITIVE
    }
}
