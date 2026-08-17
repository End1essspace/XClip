

# XClip M8 — Windows Lifecycle Hardening

> **Version-line status — 2026-08-17:** v1.3.0 is the historical 2026-06-05 release. The active development/release target and Gradle metadata are **v1.4.0**. The current machine-readable UI contract is `ui-contract-v1.4.0.properties` revision 19; the v1.3.0 resource remains frozen only for historical R11 evidence.



> **v1.4.0 release decision — 2026-08-17:** clean MSI packaging and installed packaged smoke validation passed. The release owner waived the separate formal 18-case M8 evidence run for v1.4.0. This waiver does not mark unexecuted cases PASS and does not create or imply `PASS.txt`.

Implementation status: runtime hardening and automated assets implemented
Release status: packaged MSI smoke PASS; formal 18-case evidence WAIVED for v1.4.0
Formal evidence rule: M8 is evidence-complete only when validated `PASS.txt` exists; v1.4.0 does not claim formal evidence completion

Version: 1.4.0
Config schema: 5
SQLite schema: 6
UI contract: 19

## Purpose

M8 validates the packaged Windows lifecycle rather than adding clipboard
features. Runtime recovery must preserve user data, avoid duplicate shell
surfaces, and never reuse a stale Direct Paste target after a session boundary.

## Runtime hardening

### Single instance

- Ownership is loopback-only on port `32145`.
- A failed bind is accepted as a running XClip instance only after the primary
  process returns the `XCLIP_OK` protocol acknowledgement.
- An unrelated process occupying the port opens a visible startup error and
  aborts launch instead of silently suppressing XClip.
- The primary server socket is closed during normal and JVM shutdown.

### Explorer, tray, and hotkey recovery

- A two-second lifecycle heartbeat observes the current Explorer shell PID.
- A changed shell PID forces idempotent tray reinstallation.
- The tray controller never intentionally owns more than one `TrayIcon` object.
- Ctrl+Shift+V registration is restarted after Explorer/session recovery unless
  Windows reports an explicit hotkey conflict.
- Ordinary health checks use a retry cooldown for failed registrations.

### Sleep, lock, and unlock

- A heartbeat gap of at least ten seconds is treated as suspend/resume.
- Windows lock state is detected through the switchable input desktop.
- On lock, unlock, or resume, Direct Paste target state is cleared.
- On unlock/resume, an enabled clipboard watcher is recreated and snapshots the
  current clipboard before polling. Clipboard changes made while unavailable
  are therefore not ingested later as new history.

### Display topology and DPI

- The lifecycle fingerprint includes display bounds, scale transform, and DPI.
- Monitor connect/disconnect and DPI changes re-run the existing visible-screen
  recovery policy.
- Valid negative-coordinate windows remain valid; off-screen rectangles are
  moved back to a visible work area and persisted.

### Shutdown and logoff

- A JVM shutdown hook closes backend lifecycle resources if JavaFX stop is not
  reached normally.
- Shutdown remains idempotent.
- Watcher, popup workers, exit cleanup, tray/hotkey, single-instance socket,
  DAOs, and database are closed in explicit order.
- Clear-RECENT-on-exit has a hard three-second execution deadline. Timeout is
  reported as `TIMED_OUT`; shutdown continues instead of waiting indefinitely.

### Autostart and MSI lifecycle

- Enabled autostart compares the HKCU Run command with the current launcher.
- A stale path is overwritten on startup or Settings Apply.
- MSI packaging retains the fixed upgrade UUID
  `1322455b-12c4-4363-b896-12cd27ac3e3d` and per-user install mode.
- User data remains under `%USERPROFILE%\.xclip`, outside the MSI installation
  directory. Upgrade, uninstall, and reinstall remain part of the optional formal
  packaged lifecycle suite; v1.4.0 does not claim those unexecuted cases as PASS.

## Automated gates

Run:

```powershell
.\gradlew.bat test --tests "io.xseries.xclip.system.*" --tests "io.xseries.xclip.system.lifecycle.*" --tests "io.xseries.xclip.system.clipboard.ClipboardWatcherLifecycleTest" --tests "io.xseries.xclip.domain.service.HistoryCleanupServiceTest" --tests "io.xseries.xclip.ui.UiContractFreezeTest" --no-daemon
```

Then:

```powershell
.\gradlew.bat clean m8WindowsLifecycleGate --no-daemon
```

The automated gate verifies runtime policy, single-instance acknowledgement,
autostart command parsing, bounded exit cleanup, frozen contract, packaging
arguments, and all previous M7/M6/C8 gates.

## Optional formal packaged evidence

Create a fresh evidence directory:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start_m8_windows_lifecycle_validation.ps1
```

Build the MSI before package scenarios:

```powershell
.\gradlew.bat clean packageMsi --no-daemon
```

To claim formal M8 evidence completion, execute all 18 cases in `results.csv`.
Set every executed row to `PASS` only after observing the expected packaged-app
behavior and add a short objective note or evidence filename. Then validate the
completed set:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\validate_m8_windows_lifecycle_evidence.ps1 -EvidenceDirectory "<evidence-directory>"
```

A successful formal validation creates `PASS.txt` in the evidence directory. No such file is claimed for the v1.4.0 release decision described above.

## Safety rules

- Create an XClip backup before uninstall/reinstall testing.
- Do not delete `%USERPROFILE%\.xclip` during M8.
- Use a disposable conflicting hotkey process only for the conflict case.
- Save evidence outside the installation directory and outside `.xclip`.
- Mark a case PASS only after observing the expected result in the packaged app.
