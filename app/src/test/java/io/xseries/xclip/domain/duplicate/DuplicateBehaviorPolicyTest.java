/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.duplicate;

import org.junit.jupiter.api.Test;

import static io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy.CaseSensitivity;
import static io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy.PinnedDuplicatePosition;
import static io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy.RecentDuplicatePosition;
import static io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy.WhitespaceMode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateBehaviorPolicyTest {

    @Test
    void defaultsPreserveCurrentXClipBehavior() {
        DuplicateBehaviorPolicy policy = DuplicateBehaviorPolicy.defaults();

        assertEquals(RecentDuplicatePosition.MOVE_TO_TOP, policy.recentDuplicatePosition());
        assertEquals(PinnedDuplicatePosition.PRESERVE_PIN_POSITION, policy.pinnedDuplicatePosition());
        assertEquals(WhitespaceMode.NORMALIZE, policy.whitespaceMode());
        assertEquals(CaseSensitivity.SENSITIVE, policy.caseSensitivity());
        assertEquals(DuplicateBehaviorPolicy.UNLIMITED_WINDOW, policy.duplicateWindowMillis());
        assertFalse(policy.exactContentMode());
    }

    @Test
    void normalizedWhitespaceCollapsesRunsAndTrimsEdges() {
        DuplicateBehaviorPolicy policy = DuplicateBehaviorPolicy.defaults();

        assertEquals("alpha beta gamma", policy.canonicalKey(" \talpha \n beta   gamma\r\n"));
        assertTrue(policy.matches("alpha beta", " alpha\t\nbeta "));
    }

    @Test
    void preserveWhitespaceKeepsEveryCharacter() {
        DuplicateBehaviorPolicy policy = new DuplicateBehaviorPolicy(
                RecentDuplicatePosition.MOVE_TO_TOP,
                PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                WhitespaceMode.PRESERVE,
                CaseSensitivity.SENSITIVE,
                0,
                false
        );

        assertFalse(policy.matches("alpha beta", "alpha  beta"));
        assertFalse(policy.matches("alpha", " alpha "));
    }

    @Test
    void insensitiveModeUsesLocaleIndependentCaseFolding() {
        DuplicateBehaviorPolicy policy = new DuplicateBehaviorPolicy(
                RecentDuplicatePosition.MOVE_TO_TOP,
                PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                WhitespaceMode.NORMALIZE,
                CaseSensitivity.INSENSITIVE,
                0,
                false
        );

        assertTrue(policy.matches("Git STATUS", "git status"));
    }

    @Test
    void exactContentModeOverridesWhitespaceAndCaseOptions() {
        DuplicateBehaviorPolicy policy = new DuplicateBehaviorPolicy(
                RecentDuplicatePosition.MOVE_TO_TOP,
                PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                WhitespaceMode.NORMALIZE,
                CaseSensitivity.INSENSITIVE,
                0,
                true
        );

        assertTrue(policy.matches("Exact Value", "Exact Value"));
        assertFalse(policy.matches("Exact Value", "exact value"));
        assertFalse(policy.matches("Exact Value", "Exact  Value"));
        assertFalse(policy.matches("Exact Value", " Exact Value "));
    }

    @Test
    void duplicateWindowIsInclusiveAndZeroMeansUnlimited() {
        DuplicateBehaviorPolicy finite = new DuplicateBehaviorPolicy(
                RecentDuplicatePosition.MOVE_TO_TOP,
                PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                WhitespaceMode.NORMALIZE,
                CaseSensitivity.SENSITIVE,
                5_000,
                false
        );

        assertTrue(finite.withinDuplicateWindow(10_000, 15_000));
        assertFalse(finite.withinDuplicateWindow(10_000, 15_001));
        assertTrue(DuplicateBehaviorPolicy.defaults().withinDuplicateWindow(1, Long.MAX_VALUE));
    }

    @Test
    void rejectsInvalidPolicyValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DuplicateBehaviorPolicy(
                        RecentDuplicatePosition.MOVE_TO_TOP,
                        PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                        WhitespaceMode.NORMALIZE,
                        CaseSensitivity.SENSITIVE,
                        -1,
                        false
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new DuplicateBehaviorPolicy(
                        null,
                        PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                        WhitespaceMode.NORMALIZE,
                        CaseSensitivity.SENSITIVE,
                        0,
                        false
                )
        );
    }
}
