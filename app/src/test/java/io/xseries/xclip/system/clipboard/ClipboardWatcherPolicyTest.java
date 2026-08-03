/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.clipboard;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipboardWatcherPolicyTest {

    @Test
    void exactChangedContentIsPassedToCapturePolicy() {
        AtomicReference<String> inspected = new AtomicReference<>();

        boolean allowed = ClipboardWatcher.captureAllowedFailOpen(content -> {
            inspected.set(content);
            return false;
        }, "  exact clipboard text  ");

        assertFalse(allowed);
        assertEquals("  exact clipboard text  ", inspected.get());
    }

    @Test
    void policyFailureAndMissingPolicyAreFailOpen() {
        assertTrue(ClipboardWatcher.captureAllowedFailOpen(null, "text"));
        assertTrue(ClipboardWatcher.captureAllowedFailOpen(content -> {
            throw new IllegalStateException("inspection failed");
        }, "text"));
    }
}
