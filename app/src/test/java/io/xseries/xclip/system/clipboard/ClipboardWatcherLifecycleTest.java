/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.clipboard;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipboardWatcherLifecycleTest {

    @Test
    void closeReleasesWorkerOwnedResourcesOnWatcherThreadExactlyOnce() {
        AtomicInteger cleanupCount = new AtomicInteger();
        AtomicReference<String> cleanupThread = new AtomicReference<>();

        ClipboardWatcher watcher = new ClipboardWatcher(
                new ClipboardAccess(),
                ignored -> {},
                () -> false,
                ignored -> true,
                () -> {
                    cleanupThread.set(Thread.currentThread().getName());
                    cleanupCount.incrementAndGet();
                }
        );

        watcher.close();
        watcher.close();

        assertEquals(1, cleanupCount.get());
        assertTrue(cleanupThread.get().startsWith("xclip-clipboard-watcher"));
    }
}
