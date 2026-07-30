/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.service;

import io.xseries.xclip.system.clipboard.ClipboardAccess;
import io.xseries.xclip.system.input.KeyboardInputService;
import io.xseries.xclip.system.window.ForegroundWindowService;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Coordinates direct paste:
 * 1. writes text to the clipboard;
 * 2. hides XClip;
 * 3. restores the previously active external window;
 * 4. sends Ctrl+V.
 *
 * If no valid external target exists, the text is still copied to the clipboard.
 */
public final class PasteService implements AutoCloseable {

    public enum StartResult {
        SCHEDULED,
        COPIED_ONLY,
        CLIPBOARD_UNAVAILABLE
    }

    @FunctionalInterface
    interface ClipboardWriter {
        boolean write(String text);
    }

    interface TargetController {
        boolean capture();
        boolean hasTarget();
        boolean restore();
        void clear();
    }

    @FunctionalInterface
    interface ShortcutSender {
        boolean sendPasteShortcut();
    }

    private static final long DEFAULT_RESTORE_DELAY_MS = 70;
    private static final long DEFAULT_PASTE_DELAY_MS = 70;

    private final Consumer<String> markPushedByApp;
    private final ClipboardWriter clipboardWriter;
    private final TargetController targetController;
    private final ShortcutSender shortcutSender;
    private final ScheduledExecutorService executor;
    private final long restoreDelayMs;
    private final long pasteDelayMs;

    public static PasteService createDefault(ClipboardAccess clipboard, ClipService clipService) {
        Objects.requireNonNull(clipboard);
        Objects.requireNonNull(clipService);

        ForegroundWindowService foreground = new ForegroundWindowService();
        KeyboardInputService keyboard = new KeyboardInputService();

        TargetController target = new TargetController() {
            @Override
            public boolean capture() {
                return foreground.captureCurrentExternalWindow();
            }

            @Override
            public boolean hasTarget() {
                return foreground.hasCapturedTarget();
            }

            @Override
            public boolean restore() {
                return foreground.restoreCapturedTarget();
            }

            @Override
            public void clear() {
                foreground.clearCapturedTarget();
            }
        };

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "xclip-direct-paste");
            t.setDaemon(true);
            return t;
        });

        return new PasteService(
                clipService::markPushedByApp,
                clipboard::setTextSafely,
                target,
                keyboard::sendPasteShortcut,
                executor,
                DEFAULT_RESTORE_DELAY_MS,
                DEFAULT_PASTE_DELAY_MS
        );
    }

    PasteService(
            Consumer<String> markPushedByApp,
            ClipboardWriter clipboardWriter,
            TargetController targetController,
            ShortcutSender shortcutSender,
            ScheduledExecutorService executor,
            long restoreDelayMs,
            long pasteDelayMs
    ) {
        this.markPushedByApp = Objects.requireNonNull(markPushedByApp);
        this.clipboardWriter = Objects.requireNonNull(clipboardWriter);
        this.targetController = Objects.requireNonNull(targetController);
        this.shortcutSender = Objects.requireNonNull(shortcutSender);
        this.executor = Objects.requireNonNull(executor);
        this.restoreDelayMs = Math.max(0, restoreDelayMs);
        this.pasteDelayMs = Math.max(0, pasteDelayMs);
    }

    public boolean prepareTargetForPaste() {
        return targetController.capture();
    }

    public boolean hasTarget() {
        return targetController.hasTarget();
    }

    public void clearTarget() {
        targetController.clear();
    }

    public StartResult paste(String text, Runnable hidePopup) {
        if (text == null || text.isEmpty()) {
            return StartResult.CLIPBOARD_UNAVAILABLE;
        }

        try {
            markPushedByApp.accept(text);
        } catch (Throwable ignored) {
            // Clipboard write can still proceed. Self-copy suppression is best effort.
        }

        if (!clipboardWriter.write(text)) {
            return StartResult.CLIPBOARD_UNAVAILABLE;
        }

        Runnable hide = hidePopup != null ? hidePopup : () -> {};

        if (!targetController.hasTarget()) {
            targetController.clear();
            hide.run();
            return StartResult.COPIED_ONLY;
        }

        hide.run();

        try {
            executor.schedule(this::restoreAndSchedulePaste, restoreDelayMs, TimeUnit.MILLISECONDS);
            return StartResult.SCHEDULED;
        } catch (RejectedExecutionException ignored) {
            targetController.clear();
            return StartResult.COPIED_ONLY;
        }
    }

    private void restoreAndSchedulePaste() {
        if (!targetController.restore()) {
            targetController.clear();
            return;
        }

        try {
            executor.schedule(() -> {
                try {
                    shortcutSender.sendPasteShortcut();
                } finally {
                    targetController.clear();
                }
            }, pasteDelayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            targetController.clear();
        }
    }

    @Override
    public void close() {
        targetController.clear();
        executor.shutdownNow();
    }
}
