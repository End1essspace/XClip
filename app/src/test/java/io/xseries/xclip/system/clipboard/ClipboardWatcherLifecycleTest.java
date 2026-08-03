

/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.clipboard;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipboardWatcherLifecycleTest {

    @Test
    void closeReleasesWorkerOwnedResourcesOnWatcherThreadExactlyOnce() throws InterruptedException {
        AtomicInteger cleanupCount = new AtomicInteger();
        AtomicReference<Thread> cleanupThread = new AtomicReference<>();

        ClipboardWatcher watcher = new ClipboardWatcher(
                new ClipboardAccess(),
                ignored -> {},
                () -> false,
                ignored -> true,
                () -> {
                    cleanupThread.set(Thread.currentThread());
                    cleanupCount.incrementAndGet();
                }
        );

        watcher.close();
        watcher.close();

        assertEquals(1, cleanupCount.get());
        Thread worker = cleanupThread.get();
        assertNotNull(worker);
        assertTrue(worker.getName().startsWith("xclip-clipboard-watcher"));
        worker.join(2_000);
        assertFalse(worker.isAlive());
    }
    @Test
    void resumeRecoveryRecreatesEnabledWatcherAndReleasesOldWorker() {
        AtomicInteger cleanupCount = new AtomicInteger();
        WatcherController controller = new WatcherController(
                new ClipboardAccess(),
                ignored -> {},
                () -> false,
                ignored -> true,
                cleanupCount::incrementAndGet
        );

        controller.enable();
        controller.recoverAfterSystemResume();
        controller.close();

        assertEquals(2, cleanupCount.get());
        assertFalse(controller.isEnabled());
    }

}
