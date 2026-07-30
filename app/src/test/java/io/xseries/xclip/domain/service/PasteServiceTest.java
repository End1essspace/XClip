/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasteServiceTest {

    private PasteService service;

    @AfterEach
    void tearDown() {
        if (service != null) service.close();
    }

    @Test
    void copiesOnlyWhenNoExternalTargetExists() {
        AtomicReference<String> marked = new AtomicReference<>();
        AtomicReference<String> clipboard = new AtomicReference<>();
        AtomicBoolean hidden = new AtomicBoolean(false);
        AtomicInteger shortcuts = new AtomicInteger();

        FakeTarget target = new FakeTarget(false);
        service = new PasteService(
                marked::set,
                text -> {
                    clipboard.set(text);
                    return true;
                },
                target,
                () -> {
                    shortcuts.incrementAndGet();
                    return true;
                },
                newExecutor(),
                0,
                0
        );

        PasteService.StartResult result = service.paste("alpha", () -> hidden.set(true));

        assertEquals(PasteService.StartResult.COPIED_ONLY, result);
        assertEquals("alpha", marked.get());
        assertEquals("alpha", clipboard.get());
        assertTrue(hidden.get());
        assertEquals(0, target.restoreCount.get());
        assertEquals(0, shortcuts.get());
        assertFalse(target.hasTarget());
    }

    @Test
    void restoresCapturedTargetThenSendsPasteShortcut() throws Exception {
        AtomicReference<String> clipboard = new AtomicReference<>();
        AtomicBoolean hidden = new AtomicBoolean(false);
        CountDownLatch shortcutSent = new CountDownLatch(1);

        FakeTarget target = new FakeTarget(true);
        service = new PasteService(
                text -> {},
                text -> {
                    clipboard.set(text);
                    return true;
                },
                target,
                () -> {
                    shortcutSent.countDown();
                    return true;
                },
                newExecutor(),
                0,
                0
        );

        PasteService.StartResult result = service.paste("beta", () -> hidden.set(true));

        assertEquals(PasteService.StartResult.SCHEDULED, result);
        assertTrue(hidden.get());
        assertTrue(shortcutSent.await(2, TimeUnit.SECONDS));
        assertTrue(waitUntil(() -> !target.hasTarget(), 2_000));
        assertEquals("beta", clipboard.get());
        assertEquals(1, target.restoreCount.get());
    }

    @Test
    void leavesPopupOpenWhenClipboardWriteFails() {
        AtomicBoolean hidden = new AtomicBoolean(false);
        FakeTarget target = new FakeTarget(true);

        service = new PasteService(
                text -> {},
                text -> false,
                target,
                () -> true,
                newExecutor(),
                0,
                0
        );

        PasteService.StartResult result = service.paste("gamma", () -> hidden.set(true));

        assertEquals(PasteService.StartResult.CLIPBOARD_UNAVAILABLE, result);
        assertFalse(hidden.get());
        assertEquals(0, target.restoreCount.get());
        assertTrue(target.hasTarget());
    }

    private boolean waitUntil(java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    private ScheduledExecutorService newExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "paste-service-test");
            t.setDaemon(true);
            return t;
        });
    }

    private static final class FakeTarget implements PasteService.TargetController {
        private final AtomicBoolean present;
        private final AtomicInteger restoreCount = new AtomicInteger();

        private FakeTarget(boolean present) {
            this.present = new AtomicBoolean(present);
        }

        @Override
        public boolean capture() {
            present.set(true);
            return true;
        }

        @Override
        public boolean hasTarget() {
            return present.get();
        }

        @Override
        public boolean restore() {
            restoreCount.incrementAndGet();
            return present.get();
        }

        @Override
        public void clear() {
            present.set(false);
        }
    }
}
