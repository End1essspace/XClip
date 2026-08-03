/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.privacy;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveContentPolicyTest {

    @Test
    void defaultsCaptureEverythingAndDoNotScanUnnecessarily() {
        SensitiveContentPolicy policy = SensitiveContentPolicy.defaults();

        assertTrue(policy.empty());
        assertTrue(policy.allowsCapture("4111 1111 1111 1111"));
        assertTrue(policy.allowsCapture("Your OTP is 123456"));
    }

    @Test
    void detectsLuhnValidCardCandidatesWithCommonSeparators() {
        assertEquals(
                Set.of(SensitiveContentPolicy.SensitiveKind.PAYMENT_CARD),
                SensitiveContentPolicy.detect("Card: 4111 1111 1111 1111")
        );
        assertTrue(SensitiveContentPolicy.containsPaymentCard(
                "Use 5555-5555-5555-4444 for the synthetic test"
        ));
        assertTrue(SensitiveContentPolicy.containsPaymentCard("378282246310005"));
    }

    @Test
    void rejectsInvalidOrClearlyNonCardDigitSequences() {
        assertFalse(SensitiveContentPolicy.containsPaymentCard("4111 1111 1111 1112"));
        assertFalse(SensitiveContentPolicy.containsPaymentCard("1111111111111111"));
        assertFalse(SensitiveContentPolicy.containsPaymentCard("order4111111111111111x"));
        assertFalse(SensitiveContentPolicy.containsPaymentCard("123456789012"));
    }

    @Test
    void otpDetectionRequiresExplicitNearbyContext() {
        assertTrue(SensitiveContentPolicy.containsContextualOneTimeCode(
                "Your verification code is 482913. It expires soon."
        ));
        assertTrue(SensitiveContentPolicy.containsContextualOneTimeCode(
                "Код подтверждения: 7341"
        ));
        assertTrue(SensitiveContentPolicy.containsContextualOneTimeCode(
                "Tasdiqlash kodi 992244"
        ));
        assertFalse(SensitiveContentPolicy.containsContextualOneTimeCode("482913"));
        assertFalse(SensitiveContentPolicy.containsContextualOneTimeCode(
                "Invoice 482913 was paid"
        ));
        assertFalse(SensitiveContentPolicy.containsContextualOneTimeCode(
                "Meeting date 20260802"
        ));
    }

    @Test
    void eachRuleBlocksOnlyItsOwnDetectedKind() {
        SensitiveContentPolicy cardsOnly = new SensitiveContentPolicy(
                SensitiveContentPolicy.RuleAction.SKIP,
                SensitiveContentPolicy.RuleAction.CAPTURE
        );
        SensitiveContentPolicy otpOnly = new SensitiveContentPolicy(
                SensitiveContentPolicy.RuleAction.CAPTURE,
                SensitiveContentPolicy.RuleAction.SKIP
        );

        assertFalse(cardsOnly.allowsCapture("4111111111111111"));
        assertTrue(cardsOnly.allowsCapture("Your OTP is 123456"));
        assertTrue(otpOnly.allowsCapture("4111111111111111"));
        assertFalse(otpOnly.allowsCapture("Your OTP is 123456"));
    }

    @Test
    void mixedContentReportsBothKindsButUsesStableRulePriority() {
        String content = "Card 4111111111111111; verification code 123456";

        assertEquals(
                Set.of(
                        SensitiveContentPolicy.SensitiveKind.PAYMENT_CARD,
                        SensitiveContentPolicy.SensitiveKind.ONE_TIME_CODE
                ),
                SensitiveContentPolicy.detect(content)
        );

        SensitiveContentPolicy policy = new SensitiveContentPolicy(
                SensitiveContentPolicy.RuleAction.SKIP,
                SensitiveContentPolicy.RuleAction.SKIP
        );
        assertEquals(
                SensitiveContentPolicy.SensitiveKind.PAYMENT_CARD,
                policy.firstBlockedKind(content).orElseThrow()
        );
    }
}
