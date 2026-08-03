
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
import static io.xseries.xclip.domain.duplicate.DuplicatePolicyEngine.Decision;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicatePolicyEngineTest {

    @Test
    void missingOrNonMatchingCandidateCreatesNewEntry() {
        DuplicateBehaviorPolicy policy = DuplicateBehaviorPolicy.defaults();

        assertEquals(
                Decision.CREATE_NEW_ENTRY,
                DuplicatePolicyEngine.evaluate(policy, null, "alpha", 2_000)
        );
        assertEquals(
                Decision.CREATE_NEW_ENTRY,
                DuplicatePolicyEngine.evaluate(
                        policy,
                        new DuplicatePolicyEngine.ExistingClip("beta", false, 1_000),
                        "alpha",
                        2_000
                )
        );
    }

    @Test
    void defaultRecentDuplicateMovesToTop() {
        Decision decision = DuplicatePolicyEngine.evaluate(
                DuplicateBehaviorPolicy.defaults(),
                new DuplicatePolicyEngine.ExistingClip("alpha beta", false, 1_000),
                " alpha\tbeta ",
                2_000
        );

        assertEquals(Decision.UPDATE_EXISTING_MOVE_RECENT_TO_TOP, decision);
        assertTrue(decision.duplicate());
        assertTrue(decision.updateLastCopiedAt());
        assertFalse(decision.movePinnedToTop());
    }

    @Test
    void recentDuplicateCanPreserveExistingPosition() {
        DuplicateBehaviorPolicy policy = new DuplicateBehaviorPolicy(
                RecentDuplicatePosition.PRESERVE_EXISTING_POSITION,
                PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                WhitespaceMode.NORMALIZE,
                CaseSensitivity.SENSITIVE,
                0,
                false
        );

        Decision decision = DuplicatePolicyEngine.evaluate(
                policy,
                new DuplicatePolicyEngine.ExistingClip("alpha", false, 1_000),
                "alpha",
                2_000
        );

        assertEquals(Decision.UPDATE_EXISTING_PRESERVE_RECENT_POSITION, decision);
        assertTrue(decision.duplicate());
        assertFalse(decision.updateLastCopiedAt());
    }

    @Test
    void defaultPinnedDuplicatePreservesManualPinPosition() {
        Decision decision = DuplicatePolicyEngine.evaluate(
                DuplicateBehaviorPolicy.defaults(),
                new DuplicatePolicyEngine.ExistingClip("alpha", true, 1_000),
                "alpha",
                2_000
        );

        assertEquals(Decision.UPDATE_EXISTING_PRESERVE_PIN_POSITION, decision);
        assertTrue(decision.duplicate());
        assertTrue(decision.updateLastCopiedAt());
        assertFalse(decision.movePinnedToTop());
    }

    @Test
    void pinnedDuplicateCanMoveToTop() {
        DuplicateBehaviorPolicy policy = new DuplicateBehaviorPolicy(
                RecentDuplicatePosition.PRESERVE_EXISTING_POSITION,
                PinnedDuplicatePosition.MOVE_PIN_TO_TOP,
                WhitespaceMode.NORMALIZE,
                CaseSensitivity.SENSITIVE,
                0,
                false
        );

        Decision decision = DuplicatePolicyEngine.evaluate(
                policy,
                new DuplicatePolicyEngine.ExistingClip("alpha", true, 1_000),
                "alpha",
                2_000
        );

        assertEquals(Decision.UPDATE_EXISTING_MOVE_PIN_TO_TOP, decision);
        assertTrue(decision.updateLastCopiedAt());
        assertTrue(decision.movePinnedToTop());
    }

    @Test
    void matchingContentOutsideWindowCreatesNewEntry() {
        DuplicateBehaviorPolicy policy = new DuplicateBehaviorPolicy(
                RecentDuplicatePosition.MOVE_TO_TOP,
                PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                WhitespaceMode.NORMALIZE,
                CaseSensitivity.SENSITIVE,
                500,
                false
        );

        assertEquals(
                Decision.CREATE_NEW_ENTRY,
                DuplicatePolicyEngine.evaluate(
                        policy,
                        new DuplicatePolicyEngine.ExistingClip("alpha", false, 1_000),
                        "alpha",
                        1_501
                )
        );
    }

    @Test
    void caseAndExactModesProduceDeterministicDecisions() {
        DuplicateBehaviorPolicy insensitive = new DuplicateBehaviorPolicy(
                RecentDuplicatePosition.MOVE_TO_TOP,
                PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                WhitespaceMode.NORMALIZE,
                CaseSensitivity.INSENSITIVE,
                0,
                false
        );
        DuplicateBehaviorPolicy exact = new DuplicateBehaviorPolicy(
                RecentDuplicatePosition.MOVE_TO_TOP,
                PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                WhitespaceMode.NORMALIZE,
                CaseSensitivity.INSENSITIVE,
                0,
                true
        );
        var existing = new DuplicatePolicyEngine.ExistingClip("Alpha Value", false, 1_000);

        assertEquals(
                Decision.UPDATE_EXISTING_MOVE_RECENT_TO_TOP,
                DuplicatePolicyEngine.evaluate(insensitive, existing, "alpha value", 2_000)
        );
        assertEquals(
                Decision.CREATE_NEW_ENTRY,
                DuplicatePolicyEngine.evaluate(exact, existing, "alpha value", 2_000)
        );
    }

    @Test
    void canonicalEvaluationMatchesPublicEvaluationWithoutIncomingReprocessing() {
        DuplicateBehaviorPolicy policy = new DuplicateBehaviorPolicy(
                RecentDuplicatePosition.MOVE_TO_TOP,
                PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                WhitespaceMode.NORMALIZE,
                CaseSensitivity.INSENSITIVE,
                0,
                false
        );
        String incoming = " alpha\tvalue ";
        String canonical = policy.canonicalKey(incoming);

        assertEquals(
                DuplicatePolicyEngine.evaluate(
                        policy,
                        new DuplicatePolicyEngine.ExistingClip(
                                "Alpha Value",
                                false,
                                1_000
                        ),
                        incoming,
                        2_000
                ),
                DuplicatePolicyEngine.evaluateCanonical(
                        policy,
                        "Alpha Value",
                        false,
                        1_000,
                        canonical,
                        2_000
                )
        );

        assertEquals(
                Decision.CREATE_NEW_ENTRY,
                DuplicatePolicyEngine.evaluateCanonical(
                        policy,
                        "different",
                        false,
                        1_000,
                        canonical,
                        2_000
                )
        );
    }

}
