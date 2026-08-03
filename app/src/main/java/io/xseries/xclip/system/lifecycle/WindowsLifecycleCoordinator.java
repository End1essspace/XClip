/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.lifecycle;

import io.xseries.xclip.system.clipboard.WatcherController;
import io.xseries.xclip.system.tray.TrayController;
import io.xseries.xclip.ui.PopupWindow;
import javafx.application.Platform;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Self-healing packaged-runtime lifecycle coordinator.
 *
 * It detects suspend/resume gaps, lock/unlock transitions, display/DPI changes,
 * and missing tray/hotkey surfaces without owning any user data.
 */
public final class WindowsLifecycleCoordinator implements AutoCloseable {

    public static final long HEARTBEAT_INTERVAL_SECONDS = 2L;

    private final WatcherController watcherController;
    private final TrayController trayController;
    private final PopupWindow popupWindow;
    private final WindowsLifecycleStateMachine stateMachine;
    private final WindowsSessionStateProbe sessionProbe;
    private final WindowsShellStateProbe shellProbe;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public WindowsLifecycleCoordinator(
            WatcherController watcherController,
            TrayController trayController,
            PopupWindow popupWindow
    ) {
        this.watcherController = Objects.requireNonNull(
                watcherController,
                "watcherController"
        );
        this.trayController = Objects.requireNonNull(
                trayController,
                "trayController"
        );
        this.popupWindow = Objects.requireNonNull(popupWindow, "popupWindow");
        this.stateMachine = new WindowsLifecycleStateMachine();
        this.sessionProbe = new WindowsSessionStateProbe();
        this.shellProbe = new WindowsShellStateProbe();
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "xclip-windows-lifecycle");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (closed.get() || !started.compareAndSet(false, true)) return;
        executor.scheduleWithFixedDelay(
                this::tickSafely,
                0L,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void tickSafely() {
        if (closed.get()) return;

        try {
            Set<WindowsLifecycleStateMachine.Action> actions = stateMachine.observe(
                    System.currentTimeMillis(),
                    sessionProbe.isSessionUnlocked(),
                    DisplayTopologySnapshot.capture(),
                    shellProbe.shellProcessId()
            );
            apply(actions);
        } catch (Throwable ignored) {
            // Lifecycle recovery is best effort and must never terminate XClip.
        }
    }

    private void apply(Set<WindowsLifecycleStateMachine.Action> actions) {
        if (actions.contains(
                WindowsLifecycleStateMachine.Action.RESTART_CLIPBOARD_WATCHER
        )) {
            try {
                watcherController.recoverAfterSystemResume();
            } catch (Throwable ignored) {
            }
        }

        try {
            if (actions.contains(
                    WindowsLifecycleStateMachine.Action.REINSTALL_RUNTIME_SURFACES
            )) {
                trayController.recoverAfterShellOrResume();
            } else if (actions.contains(
                    WindowsLifecycleStateMachine.Action.ENSURE_RUNTIME_SURFACES
            )) {
                trayController.ensureRuntimeHealthy();
            }
        } catch (Throwable ignored) {
        }

        boolean clearTarget = actions.contains(
                WindowsLifecycleStateMachine.Action.CLEAR_DIRECT_PASTE_TARGET
        );
        boolean recoverWindow = actions.contains(
                WindowsLifecycleStateMachine.Action.RECOVER_WINDOW_TOPOLOGY
        );
        if (clearTarget || recoverWindow) {
            try {
                Platform.runLater(() -> {
                    if (clearTarget) {
                        try {
                            popupWindow.clearLifecycleSensitiveState();
                        } catch (Throwable ignored) {
                        }
                    }
                    if (recoverWindow) {
                        try {
                            popupWindow.recoverForCurrentDisplayTopology();
                        } catch (Throwable ignored) {
                        }
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        executor.shutdownNow();
        try {
            executor.awaitTermination(1L, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
