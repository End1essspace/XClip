/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.validation;

import java.util.List;

/**
 * Frozen M7.3 data volumes and deliberately conservative release budgets.
 *
 * The heavy validation harness runs in a separate JVM with a bounded heap and
 * writes machine-readable evidence. Normal unit tests do not execute the large
 * data matrix.
 */
public final class LargeDataValidationPolicy {

    public static final List<Integer> DATASET_SIZES = List.of(1_000, 10_000, 50_000);

    public static final int LARGE_CLIP_CHARACTERS = 500_000;
    public static final int MANY_PINNED_COUNT = 1_000;
    public static final int MANY_TAGS_COUNT = 256;
    public static final int DUPLICATE_CANDIDATE_COUNT = 2_000;
    public static final int RETENTION_ELIGIBLE_COUNT = 25_000;
    public static final int RAPID_SEARCH_CHURN_ITERATIONS = 120;
    public static final int POPUP_RESULT_LIMIT = 200;
    public static final int MEASUREMENT_SAMPLES = 15;

    public static final long MAX_HEAP_MIB = 768L;
    public static final long MAX_USED_HEAP_MIB = 700L;
    public static final long MAX_DATABASE_MIB = 512L;

    public static final long FIXTURE_1K_MAX_MILLIS = 15_000L;
    public static final long FIXTURE_10K_MAX_MILLIS = 30_000L;
    public static final long FIXTURE_50K_MAX_MILLIS = 90_000L;

    public static final long STARTUP_1K_P95_MAX_MILLIS = 1_500L;
    public static final long STARTUP_10K_P95_MAX_MILLIS = 2_500L;
    public static final long STARTUP_50K_P95_MAX_MILLIS = 5_000L;

    public static final long POPUP_PIPELINE_P95_MAX_MILLIS = 1_000L;
    public static final long POPUP_FX_MATERIALIZATION_P95_MAX_MILLIS = 500L;
    public static final long POPUP_OPEN_COMPOSITE_P95_MAX_MILLIS = 1_500L;
    public static final long SEARCH_P95_MAX_MILLIS = 1_500L;
    public static final long TAG_SEARCH_P95_MAX_MILLIS = 2_000L;
    public static final long TYPE_FILTER_P95_MAX_MILLIS = 2_000L;
    public static final long DUPLICATE_LOOKUP_P95_MAX_MILLIS = 1_500L;
    public static final long ROW_BUILD_P95_MAX_MILLIS = 250L;
    public static final long LARGE_CLIP_POLICY_MAX_MILLIS = 500L;
    public static final long RETENTION_CLEANUP_MAX_MILLIS = 20_000L;
    public static final long SEARCH_CHURN_TOTAL_MAX_MILLIS = 45_000L;
    public static final long FX_QUEUE_P95_MAX_MILLIS = 250L;
    public static final long FX_QUEUE_MAX_STALL_MILLIS = 1_000L;

    private LargeDataValidationPolicy() {}

    public static long fixtureBudgetMillis(int size) {
        return switch (size) {
            case 1_000 -> FIXTURE_1K_MAX_MILLIS;
            case 10_000 -> FIXTURE_10K_MAX_MILLIS;
            case 50_000 -> FIXTURE_50K_MAX_MILLIS;
            default -> throw new IllegalArgumentException("Unsupported dataset size: " + size);
        };
    }

    public static long startupBudgetMillis(int size) {
        return switch (size) {
            case 1_000 -> STARTUP_1K_P95_MAX_MILLIS;
            case 10_000 -> STARTUP_10K_P95_MAX_MILLIS;
            case 50_000 -> STARTUP_50K_P95_MAX_MILLIS;
            default -> throw new IllegalArgumentException("Unsupported dataset size: " + size);
        };
    }
}
