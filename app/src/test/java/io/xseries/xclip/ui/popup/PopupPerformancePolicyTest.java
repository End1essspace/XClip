/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PopupPerformancePolicyTest {

    @Test
    void typeFilteringUsesBoundedCandidateScanInsteadOfWholeHistory() {
        assertEquals(200, PopupPerformancePolicy.candidateLimit(200, false));
        assertEquals(5_000, PopupPerformancePolicy.candidateLimit(200, true));
        assertEquals(5_000, PopupPerformancePolicy.candidateLimit(5_000, true));
        assertEquals(7_000, PopupPerformancePolicy.candidateLimit(7_000, true));
    }

    @Test
    void fingerprintDoesNotRetainClipboardString() {
        var componentTypes = Arrays.stream(
                        PopupPerformancePolicy.ContentFingerprint.class.getRecordComponents()
                )
                .map(component -> component.getType())
                .toList();

        assertFalse(componentTypes.contains(String.class));
    }

    @Test
    void fingerprintHandlesFiveHundredThousandCharacterClip() {
        String content = "x".repeat(500_000);
        PopupPerformancePolicy.ContentFingerprint fingerprint =
                PopupPerformancePolicy.fingerprint(content);

        assertEquals(500_000, fingerprint.length());
        assertEquals('x', fingerprint.firstChar());
        assertEquals('x', fingerprint.lastChar());
        assertEquals(fingerprint, PopupPerformancePolicy.fingerprint(content));
    }
}
