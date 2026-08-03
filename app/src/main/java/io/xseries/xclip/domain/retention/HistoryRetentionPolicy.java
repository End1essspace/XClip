/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.retention;

import io.xseries.xclip.domain.model.ClipContentType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Immutable age-based cleanup policy for unpinned clipboard history.
 *
 * PINNED clips are outside this policy by contract. A per-type value of zero
 * disables that type-specific override. When both the general rule and a type
 * override are enabled, the shorter age wins.
 */
public record HistoryRetentionPolicy(
        boolean autoDeleteRecentEnabled,
        int recentMaxAgeDays,
        Map<ClipContentType, Integer> perTypeMaxAgeDays,
        boolean clearRecentOnExit
) {

    public static final int DEFAULT_RECENT_MAX_AGE_DAYS = 30;
    public static final int MIN_MAX_AGE_DAYS = 1;
    public static final int MAX_MAX_AGE_DAYS = 3_650;
    public static final int TYPE_RULE_DISABLED = 0;
    public static final long MILLIS_PER_DAY = 86_400_000L;

    public HistoryRetentionPolicy {
        if (recentMaxAgeDays < MIN_MAX_AGE_DAYS
                || recentMaxAgeDays > MAX_MAX_AGE_DAYS) {
            throw new IllegalArgumentException(
                    "recentMaxAgeDays must be between "
                            + MIN_MAX_AGE_DAYS + " and " + MAX_MAX_AGE_DAYS
            );
        }

        EnumMap<ClipContentType, Integer> normalized =
                new EnumMap<>(ClipContentType.class);
        Map<ClipContentType, Integer> source = Objects.requireNonNullElse(
                perTypeMaxAgeDays,
                Map.of()
        );

        for (ClipContentType type : ClipContentType.values()) {
            int days = source.getOrDefault(type, TYPE_RULE_DISABLED);
            if (days < TYPE_RULE_DISABLED || days > MAX_MAX_AGE_DAYS) {
                throw new IllegalArgumentException(
                        "Per-type retention for " + type + " must be between 0 and "
                                + MAX_MAX_AGE_DAYS
                );
            }
            normalized.put(type, days);
        }
        perTypeMaxAgeDays = Collections.unmodifiableMap(normalized);
    }

    public static HistoryRetentionPolicy defaults() {
        return new HistoryRetentionPolicy(
                false,
                DEFAULT_RECENT_MAX_AGE_DAYS,
                Map.of(),
                false
        );
    }

    public int maxAgeDaysFor(ClipContentType type) {
        return perTypeMaxAgeDays.getOrDefault(
                Objects.requireNonNull(type, "type"),
                TYPE_RULE_DISABLED
        );
    }

    public boolean anyAgeRuleEnabled() {
        if (autoDeleteRecentEnabled) return true;
        for (Integer days : perTypeMaxAgeDays.values()) {
            if (days != null && days > TYPE_RULE_DISABLED) return true;
        }
        return false;
    }

    /**
     * Effective age for one derived content type. The shortest enabled rule wins.
     */
    public OptionalInt effectiveMaxAgeDays(ClipContentType type) {
        Objects.requireNonNull(type, "type");

        int effective = autoDeleteRecentEnabled
                ? recentMaxAgeDays
                : Integer.MAX_VALUE;
        int typeDays = maxAgeDaysFor(type);
        if (typeDays > TYPE_RULE_DISABLED) {
            effective = Math.min(effective, typeDays);
        }
        return effective == Integer.MAX_VALUE
                ? OptionalInt.empty()
                : OptionalInt.of(effective);
    }

    /**
     * Largest cutoff needed to load every row that could match any enabled rule.
     */
    public OptionalLong candidateCutoffExclusive(long nowMillis) {
        if (nowMillis < 0) {
            throw new IllegalArgumentException("nowMillis cannot be negative");
        }
        if (!anyAgeRuleEnabled()) return OptionalLong.empty();

        int shortestDays = autoDeleteRecentEnabled
                ? recentMaxAgeDays
                : Integer.MAX_VALUE;
        for (Integer days : perTypeMaxAgeDays.values()) {
            if (days != null && days > TYPE_RULE_DISABLED) {
                shortestDays = Math.min(shortestDays, days);
            }
        }
        return OptionalLong.of(cutoff(nowMillis, shortestDays));
    }

    public boolean shouldDelete(
            ClipContentType type,
            long lastCopiedAt,
            long nowMillis
    ) {
        if (lastCopiedAt < 0 || nowMillis < 0 || lastCopiedAt > nowMillis) return false;

        OptionalInt days = effectiveMaxAgeDays(type);
        if (days.isEmpty()) return false;
        return lastCopiedAt < cutoff(nowMillis, days.getAsInt());
    }

    private static long cutoff(long nowMillis, int days) {
        long ageMillis = days * MILLIS_PER_DAY;
        return Math.max(0L, nowMillis - ageMillis);
    }
}
