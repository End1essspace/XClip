/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.clipboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipboardObservationStateTest {

    @Test
    void suppressedValueRemainsObservedAfterForegroundSwitch() {
        ClipboardObservationState state = new ClipboardObservationState();

        assertTrue(state.markIfChanged("sensitive-value"));
        // Runtime privacy gate suppresses ingest after this point.
        assertFalse(state.markIfChanged("sensitive-value"));
        assertEquals("sensitive-value", state.lastSeenText());
    }

    @Test
    void startupAndPauseSnapshotsPreventDelayedCapture() {
        ClipboardObservationState state = new ClipboardObservationState();

        state.snapshot("existing clipboard");
        assertFalse(state.markIfChanged("existing clipboard"));
        assertTrue(state.markIfChanged("new clipboard"));
    }

    @Test
    void blankSnapshotDoesNotReplaceLastMeaningfulValue() {
        ClipboardObservationState state = new ClipboardObservationState();

        state.snapshot("meaningful");
        state.snapshot("   ");
        assertEquals("meaningful", state.lastSeenText());
    }
}
