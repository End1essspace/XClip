/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Monotonic stale-result gate for asynchronous popup reloads.
 */
public final class ReloadRequestGate {

    private final AtomicLong generation = new AtomicLong();

    public long nextRequest() {
        return generation.incrementAndGet();
    }

    public boolean isCurrent(long requestGeneration) {
        return requestGeneration > 0 && generation.get() == requestGeneration;
    }

    public long invalidate() {
        return generation.incrementAndGet();
    }

    public long currentGeneration() {
        return generation.get();
    }
}
