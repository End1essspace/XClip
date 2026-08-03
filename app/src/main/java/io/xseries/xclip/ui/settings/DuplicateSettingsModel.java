/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Pure mapping and validation contract for the duplicate Settings section.
 *
 * JavaFX controls delegate duration parsing and user-facing labels here so the
 * persisted policy and the visible UI cannot drift apart.
 */
public final class DuplicateSettingsModel {

    private DuplicateSettingsModel() {}

    public enum WindowPreset {
        UNLIMITED("Unlimited", 0L),
        TWO_SECONDS("2 seconds", 2_000L),
        TEN_SECONDS("10 seconds", 10_000L),
        THIRTY_SECONDS("30 seconds", 30_000L),
        ONE_MINUTE("1 minute", 60_000L),
        FIVE_MINUTES("5 minutes", 300_000L),
        THIRTY_MINUTES("30 minutes", 1_800_000L),
        CUSTOM("Custom…", -1L);

        private final String label;
        private final long millis;

        WindowPreset(String label, long millis) {
            this.label = label;
            this.millis = millis;
        }

        public String label() {
            return label;
        }

        public long millis() {
            return millis;
        }

        public boolean custom() {
            return this == CUSTOM;
        }
    }

    public static List<WindowPreset> windowPresets() {
        return List.of(WindowPreset.values());
    }

    public static WindowPreset presetFor(long duplicateWindowMillis) {
        if (duplicateWindowMillis < 0) {
            throw new IllegalArgumentException("duplicateWindowMillis cannot be negative");
        }

        return Arrays.stream(WindowPreset.values())
                .filter(preset -> !preset.custom())
                .filter(preset -> preset.millis() == duplicateWindowMillis)
                .findFirst()
                .orElse(WindowPreset.CUSTOM);
    }

    public static String customWindowText(long duplicateWindowMillis) {
        return presetFor(duplicateWindowMillis).custom()
                ? Long.toString(duplicateWindowMillis)
                : "";
    }

    public static long resolveWindowMillis(
            WindowPreset preset,
            String customWindowMillis
    ) {
        WindowPreset selected = Objects.requireNonNull(preset, "preset");
        if (!selected.custom()) return selected.millis();

        String value = Objects.requireNonNullElse(customWindowMillis, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Custom duplicate window is required");
        }
        if (!value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException(
                    "Custom duplicate window must contain digits only"
            );
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Custom duplicate window is too large", error);
        }
    }

    public static DuplicateBehaviorPolicy toPolicy(
            DuplicateBehaviorPolicy.RecentDuplicatePosition recentPosition,
            DuplicateBehaviorPolicy.PinnedDuplicatePosition pinnedPosition,
            DuplicateBehaviorPolicy.WhitespaceMode whitespaceMode,
            DuplicateBehaviorPolicy.CaseSensitivity caseSensitivity,
            WindowPreset windowPreset,
            String customWindowMillis,
            boolean exactContentMode
    ) {
        return new DuplicateBehaviorPolicy(
                Objects.requireNonNull(recentPosition, "recentPosition"),
                Objects.requireNonNull(pinnedPosition, "pinnedPosition"),
                Objects.requireNonNull(whitespaceMode, "whitespaceMode"),
                Objects.requireNonNull(caseSensitivity, "caseSensitivity"),
                resolveWindowMillis(windowPreset, customWindowMillis),
                exactContentMode
        );
    }

    public static String recentPositionLabel(
            DuplicateBehaviorPolicy.RecentDuplicatePosition value
    ) {
        return switch (Objects.requireNonNull(value, "value")) {
            case MOVE_TO_TOP -> "Move duplicate to top";
            case PRESERVE_EXISTING_POSITION -> "Keep existing position";
        };
    }

    public static String pinnedPositionLabel(
            DuplicateBehaviorPolicy.PinnedDuplicatePosition value
    ) {
        return switch (Objects.requireNonNull(value, "value")) {
            case PRESERVE_PIN_POSITION -> "Keep pinned position";
            case MOVE_PIN_TO_TOP -> "Move pinned clip to top";
        };
    }

    public static String whitespaceLabel(DuplicateBehaviorPolicy.WhitespaceMode value) {
        return switch (Objects.requireNonNull(value, "value")) {
            case NORMALIZE -> "Normalize whitespace";
            case PRESERVE -> "Preserve whitespace";
        };
    }

    public static String caseSensitivityLabel(
            DuplicateBehaviorPolicy.CaseSensitivity value
    ) {
        return switch (Objects.requireNonNull(value, "value")) {
            case SENSITIVE -> "Case-sensitive";
            case INSENSITIVE -> "Ignore case";
        };
    }
}
