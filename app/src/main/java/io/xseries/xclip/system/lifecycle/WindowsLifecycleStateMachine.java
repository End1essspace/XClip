/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.lifecycle;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Pure transition policy for Windows session, suspend, and display changes.
 */
public final class WindowsLifecycleStateMachine {

    public static final long DEFAULT_RESUME_GAP_MILLIS = 10_000L;

    private final long resumeGapMillis;
    private boolean initialized;
    private long lastHeartbeatMillis;
    private boolean sessionUnlocked;
    private String displayFingerprint = "";
    private long shellProcessId;

    public WindowsLifecycleStateMachine() {
        this(DEFAULT_RESUME_GAP_MILLIS);
    }

    WindowsLifecycleStateMachine(long resumeGapMillis) {
        if (resumeGapMillis < 1_000L) {
            throw new IllegalArgumentException("resumeGapMillis must be at least 1000");
        }
        this.resumeGapMillis = resumeGapMillis;
    }

    public Set<Action> observe(
            long nowMillis,
            boolean unlocked,
            String currentDisplayFingerprint,
            long currentShellProcessId
    ) {
        if (nowMillis < 0L) {
            throw new IllegalArgumentException("nowMillis cannot be negative");
        }
        String display = Objects.requireNonNullElse(
                currentDisplayFingerprint,
                "unknown"
        );

        EnumSet<Action> actions = EnumSet.noneOf(Action.class);
        if (!initialized) {
            initialized = true;
            lastHeartbeatMillis = nowMillis;
            sessionUnlocked = unlocked;
            displayFingerprint = display;
            shellProcessId = currentShellProcessId;
            actions.add(Action.ENSURE_RUNTIME_SURFACES);
            if (!unlocked) actions.add(Action.CLEAR_DIRECT_PASTE_TARGET);
            return Set.copyOf(actions);
        }

        long gap = nowMillis >= lastHeartbeatMillis
                ? nowMillis - lastHeartbeatMillis
                : Long.MAX_VALUE;
        if (gap >= resumeGapMillis) {
            addRuntimeRecovery(actions);
        }

        if (sessionUnlocked != unlocked) {
            actions.add(Action.CLEAR_DIRECT_PASTE_TARGET);
            if (unlocked) addRuntimeRecovery(actions);
        }

        if (!displayFingerprint.equals(display)) {
            actions.add(Action.CLEAR_DIRECT_PASTE_TARGET);
            actions.add(Action.RECOVER_WINDOW_TOPOLOGY);
        }

        if (shellProcessId > 0L
                && currentShellProcessId > 0L
                && shellProcessId != currentShellProcessId) {
            actions.add(Action.CLEAR_DIRECT_PASTE_TARGET);
            actions.add(Action.REINSTALL_RUNTIME_SURFACES);
        }

        lastHeartbeatMillis = nowMillis;
        sessionUnlocked = unlocked;
        displayFingerprint = display;
        if (currentShellProcessId > 0L) {
            shellProcessId = currentShellProcessId;
        }
        actions.add(Action.ENSURE_RUNTIME_SURFACES);
        return Set.copyOf(actions);
    }

    private void addRuntimeRecovery(EnumSet<Action> actions) {
        actions.add(Action.CLEAR_DIRECT_PASTE_TARGET);
        actions.add(Action.RESTART_CLIPBOARD_WATCHER);
        actions.add(Action.RECOVER_WINDOW_TOPOLOGY);
        actions.add(Action.ENSURE_RUNTIME_SURFACES);
        actions.add(Action.REINSTALL_RUNTIME_SURFACES);
    }

    public enum Action {
        CLEAR_DIRECT_PASTE_TARGET,
        RESTART_CLIPBOARD_WATCHER,
        RECOVER_WINDOW_TOPOLOGY,
        ENSURE_RUNTIME_SURFACES,
        REINSTALL_RUNTIME_SURFACES
    }
}
