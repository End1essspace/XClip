/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.xseries.xclip.system.lifecycle.WindowsLifecycleStateMachine.Action.CLEAR_DIRECT_PASTE_TARGET;
import static io.xseries.xclip.system.lifecycle.WindowsLifecycleStateMachine.Action.ENSURE_RUNTIME_SURFACES;
import static io.xseries.xclip.system.lifecycle.WindowsLifecycleStateMachine.Action.RECOVER_WINDOW_TOPOLOGY;
import static io.xseries.xclip.system.lifecycle.WindowsLifecycleStateMachine.Action.REINSTALL_RUNTIME_SURFACES;
import static io.xseries.xclip.system.lifecycle.WindowsLifecycleStateMachine.Action.RESTART_CLIPBOARD_WATCHER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsLifecycleStateMachineTest {

    @Test
    void initialObservationOnlyEnsuresRuntime() {
        WindowsLifecycleStateMachine machine = new WindowsLifecycleStateMachine(2_000L);

        assertEquals(
                Set.of(ENSURE_RUNTIME_SURFACES),
                machine.observe(1_000L, true, "display-a", 10L)
        );
    }

    @Test
    void lockClearsPasteTargetAndUnlockPerformsFullRecovery() {
        WindowsLifecycleStateMachine machine = new WindowsLifecycleStateMachine(5_000L);
        machine.observe(1_000L, true, "display-a", 10L);

        Set<WindowsLifecycleStateMachine.Action> locked = machine.observe(
                2_000L,
                false,
                "display-a",
                10L
        );
        assertTrue(locked.contains(CLEAR_DIRECT_PASTE_TARGET));
        assertTrue(locked.contains(ENSURE_RUNTIME_SURFACES));

        Set<WindowsLifecycleStateMachine.Action> unlocked = machine.observe(
                3_000L,
                true,
                "display-a",
                10L
        );
        assertTrue(unlocked.contains(RESTART_CLIPBOARD_WATCHER));
        assertTrue(unlocked.contains(REINSTALL_RUNTIME_SURFACES));
        assertTrue(unlocked.contains(RECOVER_WINDOW_TOPOLOGY));
    }

    @Test
    void heartbeatGapIsTreatedAsResume() {
        WindowsLifecycleStateMachine machine = new WindowsLifecycleStateMachine(2_000L);
        machine.observe(1_000L, true, "display-a", 10L);

        Set<WindowsLifecycleStateMachine.Action> actions = machine.observe(
                4_000L,
                true,
                "display-a",
                10L
        );

        assertTrue(actions.contains(RESTART_CLIPBOARD_WATCHER));
        assertTrue(actions.contains(REINSTALL_RUNTIME_SURFACES));
        assertTrue(actions.contains(CLEAR_DIRECT_PASTE_TARGET));
    }

    @Test
    void displayAndExplorerChangesProduceTargetedRecovery() {
        WindowsLifecycleStateMachine machine = new WindowsLifecycleStateMachine(5_000L);
        machine.observe(1_000L, true, "display-a", 10L);

        Set<WindowsLifecycleStateMachine.Action> display = machine.observe(
                2_000L,
                true,
                "display-b",
                10L
        );
        assertTrue(display.contains(RECOVER_WINDOW_TOPOLOGY));
        assertTrue(display.contains(CLEAR_DIRECT_PASTE_TARGET));

        Set<WindowsLifecycleStateMachine.Action> explorer = machine.observe(
                3_000L,
                true,
                "display-b",
                11L
        );
        assertTrue(explorer.contains(REINSTALL_RUNTIME_SURFACES));
        assertTrue(explorer.contains(CLEAR_DIRECT_PASTE_TARGET));
    }
    @Test
    void transientMissingShellDoesNotHideExplorerRestart() {
        WindowsLifecycleStateMachine machine = new WindowsLifecycleStateMachine(5_000L);
        machine.observe(1_000L, true, "display-a", 10L);

        Set<WindowsLifecycleStateMachine.Action> missing = machine.observe(
                2_000L,
                true,
                "display-a",
                0L
        );
        assertTrue(!missing.contains(REINSTALL_RUNTIME_SURFACES));

        Set<WindowsLifecycleStateMachine.Action> restarted = machine.observe(
                3_000L,
                true,
                "display-a",
                11L
        );
        assertTrue(restarted.contains(REINSTALL_RUNTIME_SURFACES));
        assertTrue(restarted.contains(CLEAR_DIRECT_PASTE_TARGET));
    }

}
