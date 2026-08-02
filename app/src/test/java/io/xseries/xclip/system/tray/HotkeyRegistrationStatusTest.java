/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.tray;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotkeyRegistrationStatusTest {

    @Test
    void everyStatusHasStableProductFacingCopy() {
        for (HotkeyRegistrationStatus status : HotkeyRegistrationStatus.values()) {
            assertFalse(status.label().isBlank());
            assertFalse(status.detail().isBlank());
        }
    }

    @Test
    void onlyRegistrationFailuresAreMarkedAsProblems() {
        assertTrue(HotkeyRegistrationStatus.CONFLICT.problem());
        assertTrue(HotkeyRegistrationStatus.UNSUPPORTED.problem());
        assertTrue(HotkeyRegistrationStatus.FAILED.problem());
        assertFalse(HotkeyRegistrationStatus.ACTIVE.problem());
        assertFalse(HotkeyRegistrationStatus.STOPPED.problem());
    }
}
