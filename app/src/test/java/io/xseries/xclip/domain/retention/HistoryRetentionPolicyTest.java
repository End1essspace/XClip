/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.retention;

import io.xseries.xclip.domain.model.ClipContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HistoryRetentionPolicyTest {

    @Test
    void defaultsDoNotDeleteHistory() {
        HistoryRetentionPolicy policy = HistoryRetentionPolicy.defaults();

        assertFalse(policy.anyAgeRuleEnabled());
        assertFalse(policy.clearRecentOnExit());
        assertTrue(policy.effectiveMaxAgeDays(ClipContentType.TEXT).isEmpty());
    }

    @Test
    void shorterTypeOverrideWinsOverGeneralRule() {
        HistoryRetentionPolicy policy = new HistoryRetentionPolicy(
                true,
                30,
                Map.of(ClipContentType.URL, 7, ClipContentType.CODE, 90),
                false
        );

        assertEquals(7, policy.effectiveMaxAgeDays(ClipContentType.URL).orElseThrow());
        assertEquals(30, policy.effectiveMaxAgeDays(ClipContentType.CODE).orElseThrow());
        assertEquals(30, policy.effectiveMaxAgeDays(ClipContentType.TEXT).orElseThrow());
    }

    @Test
    void perTypeRuleWorksWithoutGeneralRule() {
        HistoryRetentionPolicy policy = new HistoryRetentionPolicy(
                false,
                30,
                Map.of(ClipContentType.COMMAND, 2),
                false
        );
        long now = 10L * HistoryRetentionPolicy.MILLIS_PER_DAY;

        assertTrue(policy.shouldDelete(
                ClipContentType.COMMAND,
                now - 3L * HistoryRetentionPolicy.MILLIS_PER_DAY,
                now
        ));
        assertFalse(policy.shouldDelete(
                ClipContentType.TEXT,
                0,
                now
        ));
    }

    @Test
    void exactBoundaryIsKeptBecauseRuleMeansOlderThan() {
        HistoryRetentionPolicy policy = new HistoryRetentionPolicy(
                true,
                5,
                Map.of(),
                false
        );
        long now = 20L * HistoryRetentionPolicy.MILLIS_PER_DAY;
        long boundary = now - 5L * HistoryRetentionPolicy.MILLIS_PER_DAY;

        assertFalse(policy.shouldDelete(ClipContentType.TEXT, boundary, now));
        assertTrue(policy.shouldDelete(ClipContentType.TEXT, boundary - 1, now));
    }

    @Test
    void candidateCutoffUsesShortestEnabledRule() {
        HistoryRetentionPolicy policy = new HistoryRetentionPolicy(
                true,
                30,
                Map.of(ClipContentType.URL, 7, ClipContentType.CODE, 60),
                false
        );
        long now = 100L * HistoryRetentionPolicy.MILLIS_PER_DAY;

        assertEquals(
                now - 7L * HistoryRetentionPolicy.MILLIS_PER_DAY,
                policy.candidateCutoffExclusive(now).orElseThrow()
        );
    }
}
