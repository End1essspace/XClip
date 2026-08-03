/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReloadRequestGateTest {

    @Test
    void onlyLatestReloadMayUpdateTheUi() {
        ReloadRequestGate gate = new ReloadRequestGate();

        long first = gate.nextRequest();
        long second = gate.nextRequest();

        assertFalse(gate.isCurrent(first));
        assertTrue(gate.isCurrent(second));
    }

    @Test
    void shutdownInvalidationRejectsPendingResult() {
        ReloadRequestGate gate = new ReloadRequestGate();

        long request = gate.nextRequest();
        gate.invalidate();

        assertFalse(gate.isCurrent(request));
    }
}
