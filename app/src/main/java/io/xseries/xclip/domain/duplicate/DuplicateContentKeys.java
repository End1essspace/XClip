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
        return calculate(value, DuplicateBehaviorPolicy.normalizeWhitespace(value)).keys();
    }

    /**
     * Builds all persisted equality hashes from an already prepared normalized value.
     *
     * The overload is used by ingest and legacy DAO paths to avoid normalizing the
     * same clipboard text more than once.
     */
    public static DuplicateContentKeys from(
            String content,
            String normalizedContent
    ) {
        return calculate(
                Objects.requireNonNull(content, "content"),
                Objects.requireNonNull(normalizedContent, "normalizedContent")
        ).keys();
    }

    /**
     * Prepares one clipboard value for the complete duplicate ingest path.
     *
     * Normalized text, all persisted hashes, the selected lookup hash, and the
     * canonical collision-check key are derived from one shared set of strings.
     */
    public static Prepared prepare(
            String content,
            DuplicateBehaviorPolicy policy
    ) {
        String value = Objects.requireNonNull(content, "content");
        DuplicateBehaviorPolicy effectivePolicy =
                Objects.requireNonNull(policy, "policy");

        String normalized = DuplicateBehaviorPolicy.normalizeWhitespace(value);
        HashMaterial material = calculate(value, normalized);
        KeyKind selectedKind = material.keys().selectedKind(effectivePolicy);

        String canonicalKey = switch (selectedKind) {
            case EXACT -> value;
            case EXACT_CASE_INSENSITIVE -> material.exactCaseInsensitive();
            case NORMALIZED -> normalized;
            case NORMALIZED_CASE_INSENSITIVE -> material.normalizedCaseInsensitive();
        };

        return new Prepared(
                normalized,
                material.keys(),
                selectedKind,
                material.keys().hashFor(selectedKind),
                canonicalKey
        );
    }

    /**
     * Computes only the active policy hash.
     *
     * App-originated clipboard writes need one short-lived suppression key and do
     * not need all four persisted hashes.
     */
    public static String selectedHashFor(
            String content,
            DuplicateBehaviorPolicy policy
    ) {
        String canonical = Objects.requireNonNull(policy, "policy")
                .canonicalKey(Objects.requireNonNull(content, "content"));
        return sha256Hex(canonical);
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
        return hashFor(selectedKind(policy));
    }

    public String hashFor(KeyKind keyKind) {
        return switch (Objects.requireNonNull(keyKind, "keyKind")) {
            case EXACT -> exactHash;
            case EXACT_CASE_INSENSITIVE -> exactCaseInsensitiveHash;
            case NORMALIZED -> normalizedHash;
            case NORMALIZED_CASE_INSENSITIVE -> normalizedCaseInsensitiveHash;
        };
    }

    private static HashMaterial calculate(
            String value,
            String normalized
    ) {
        String exactCaseInsensitive = value.toLowerCase(Locale.ROOT);
        String normalizedCaseInsensitive = normalized.toLowerCase(Locale.ROOT);

        String exactHash = sha256Hex(value);
        String exactCaseInsensitiveHash = exactCaseInsensitive.equals(value)
                ? exactHash
                : sha256Hex(exactCaseInsensitive);

        String normalizedHash = normalized.equals(value)
                ? exactHash
                : sha256Hex(normalized);

        String normalizedCaseInsensitiveHash;
        if (normalizedCaseInsensitive.equals(normalized)) {
            normalizedCaseInsensitiveHash = normalizedHash;
        } else if (normalized.equals(value)) {
            normalizedCaseInsensitiveHash = exactCaseInsensitiveHash;
        } else {
            normalizedCaseInsensitiveHash = sha256Hex(normalizedCaseInsensitive);
        }

        return new HashMaterial(
                exactCaseInsensitive,
                normalizedCaseInsensitive,
                new DuplicateContentKeys(
                        exactHash,
                        exactCaseInsensitiveHash,
                        normalizedHash,
                        normalizedCaseInsensitiveHash
                )
        );
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

    private record HashMaterial(
            String exactCaseInsensitive,
            String normalizedCaseInsensitive,
            DuplicateContentKeys keys
    ) {}

    public record Prepared(
            String normalizedContent,
            DuplicateContentKeys keys,
            KeyKind selectedKind,
            String selectedHash,
            String canonicalKey
    ) {
        public Prepared {
            normalizedContent = Objects.requireNonNull(
                    normalizedContent,
                    "normalizedContent"
            );
            keys = Objects.requireNonNull(keys, "keys");
            selectedKind = Objects.requireNonNull(selectedKind, "selectedKind");
            selectedHash = TextValues.requireNonBlank(selectedHash, "selectedHash");
            canonicalKey = Objects.requireNonNull(canonicalKey, "canonicalKey");
        }
    }

    public enum KeyKind {
        EXACT,
        EXACT_CASE_INSENSITIVE,
        NORMALIZED,
        NORMALIZED_CASE_INSENSITIVE
    }
}
