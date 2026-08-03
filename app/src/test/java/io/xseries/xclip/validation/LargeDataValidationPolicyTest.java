/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.validation;

import io.xseries.xclip.config.Config;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LargeDataValidationPolicyTest {

    @Test
    void matrixMatchesFrozenRoadmapVolumes() {
        assertEquals(List.of(1_000, 10_000, 50_000), LargeDataValidationPolicy.DATASET_SIZES);
        assertEquals(500_000, LargeDataValidationPolicy.LARGE_CLIP_CHARACTERS);
        assertEquals(1_000, LargeDataValidationPolicy.MANY_PINNED_COUNT);
        assertEquals(256, LargeDataValidationPolicy.MANY_TAGS_COUNT);
        assertEquals(2_000, LargeDataValidationPolicy.DUPLICATE_CANDIDATE_COUNT);
        assertEquals(25_000, LargeDataValidationPolicy.RETENTION_ELIGIBLE_COUNT);
        assertEquals(120, LargeDataValidationPolicy.RAPID_SEARCH_CHURN_ITERATIONS);
    }

    @Test
    void popupLimitMatchesProductionDefaultAndBudgetsArePositive() {
        assertEquals(Config.DEFAULT_UI_CLIP_LIMIT, LargeDataValidationPolicy.POPUP_RESULT_LIMIT);
        for (int size : LargeDataValidationPolicy.DATASET_SIZES) {
            assertTrue(LargeDataValidationPolicy.fixtureBudgetMillis(size) > 0L);
            assertTrue(LargeDataValidationPolicy.startupBudgetMillis(size) > 0L);
        }
        assertTrue(LargeDataValidationPolicy.MAX_USED_HEAP_MIB
                < LargeDataValidationPolicy.MAX_HEAP_MIB);
    }

    @Test
    void unsupportedDatasetSizeIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LargeDataValidationPolicy.fixtureBudgetMillis(5_000)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> LargeDataValidationPolicy.startupBudgetMillis(5_000)
        );
    }
}
