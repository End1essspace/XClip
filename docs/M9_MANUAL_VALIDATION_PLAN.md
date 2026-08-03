# XClip 1.3.0 — Manual Validation Plan

Status: prepared for later execution
Scope: manual product, data, Windows lifecycle, packaging, and release validation
Important: this document does not claim that any manual case has already passed.

## 1. Sequence

The remaining work must be executed in this order:

```text
1. Review and commit documentation
2. Create a safety backup
3. Run automated gates
4. Perform functional manual validation
5. Perform database backup/restore validation
6. Review large-data evidence
7. Build and install the MSI
8. Complete all 18 Windows lifecycle cases
9. Validate upgrade/uninstall/reinstall behavior
10. Final release checks, checksums, tag, and GitHub Release
```

Packaging, tagging, and release must not begin merely because documentation is complete.

## 2. Safety preparation

Before destructive or installer testing:

1. Exit XClip.
2. Copy `%USERPROFILE%\.xclip` to a safe directory.
3. Keep an additional `.xclip-backup` outside `.xclip`.
4. Record the current installed version and installer source.
5. Preserve representative PINNED clips, titles, tags, and settings for upgrade checks.
6. Store evidence outside both the installation directory and `.xclip`.

## 3. Documentation review

Verify:

- README links resolve;
- English and Russian guides describe the same contracts;
- application version is `1.3.0`;
- config schema is `5`;
- SQLite schema is `6`;
- UI contract is `18`;
- backup format is `1`;
- no document claims packaged/manual PASS before evidence exists;
- Lucide ISC attribution and GPL-3.0-only references remain present;
- data location is consistently `%USERPROFILE%\.xclip`;
- release notes remain marked DRAFT.

Documentation screenshots are intentionally deferred until the manually validated installed build is available.

## 4. Automated gates

Run later from the repository root:

```powershell
.\gradlew.bat clean test --no-daemon
```

```powershell
.\gradlew.bat build --no-daemon
```

```powershell
.\gradlew.bat clean m7LargeDataGate --no-daemon
```

```powershell
.\gradlew.bat clean m8WindowsLifecycleGate --no-daemon
```

```powershell
git diff --check
```

Expected evidence includes:

```text
app\build\reports\m7-large-data\PASS.txt
```

Do not continue after a failing gate. Fix and re-run the failing scope first.

## 5. Core functional validation

### Clipboard capture

- Watcher captures ordinary text.
- Minimum length and maximum size policies work.
- Pause/resume prevents and restores capture.
- App-originated Copy/Direct Paste does not immediately create an unwanted duplicate.

### Duplicate behavior

Validate all configured branches:

- RECENT move to top;
- RECENT preserve position;
- PINNED preserve order;
- PINNED move to top;
- whitespace normalize/preserve;
- case sensitive/insensitive;
- finite/unlimited window;
- exact-content mode.

### Popup and keyboard

- Popup opens from hotkey and tray.
- Search receives expected focus.
- Arrow, Home, End, Enter, Escape, Tab, and Shift+Tab paths work.
- Multi-selection, range selection, and selection persistence behave correctly.
- Compact and large-window layouts have no clipping.
- Long clips remain bounded.
- No unintended full-content hover tooltip appears.

### Direct Paste

- Paste reaches the previously active supported target.
- Copy fallback works.
- Target is cleared after lock/unlock, sleep/resume, Explorer restart, and display change.
- XClip never executes COMMAND content.

### Types and filters

- TEXT, CODE, URL, PATH, JSON, and COMMAND classification is reasonable.
- Scope, type, and tag filters combine correctly.
- URL/path/JSON/code/command actions are safe and correct.
- Empty states and contradictory filters do not corrupt selection.

### Search

Validate plain text and:

```text
type:url
type:code
is:pinned
is:recent
tag:work
tag:"Project Work"
-tag:private
-type:text
```

Also verify invalid syntax diagnostics and rapid query changes.

### PINNED and tags

- Pin/unpin.
- Manual order.
- Title edit.
- Single and multi-selection tag assignment.
- Mixed tri-state edit.
- Rename/delete.
- Usage count.
- Cleanup unused.
- Case-insensitive collision handling.

## 6. Settings validation

Validate every page:

```text
General
Capture
History
Duplicate behavior
Privacy
Appearance
Shortcuts
Data
About
```

Check:

- selected page persistence during the session;
- Apply only for valid dirty state;
- Cancel and window close discard unapplied changes;
- scoped reset;
- navigation by mouse, arrows, Home, and End;
- complete Tab/Shift+Tab traversal;
- visible focus and accessible labels;
- validation link by mouse, Enter, and Space;
- compact/standard/wide layout;
- no footer or action clipping;
- no UI freeze during long data operations.

## 7. Privacy and retention

### Excluded applications

- Listed executable suppresses capture while foreground.
- Matching ignores case and path prefix.
- Removing the executable restores capture.
- Resolver failure does not silently discard ordinary data.

### Sensitive rules

- Default CAPTURE behavior remains unchanged.
- Luhn-valid card-like test value is skipped only when enabled.
- Invalid/non-card values remain capturable.
- Contextual OTP is skipped only when enabled.
- Standalone ordinary numbers remain capturable.
- Existing history remains untouched.

### Retention

- General and per-type age rules.
- Shorter enabled rule wins.
- Exact N-day boundary is preserved.
- PINNED never deleted.
- Startup, Apply, manual, periodic, and exit outcomes display correctly.
- `TIMED_OUT` is represented without blocking shutdown indefinitely.

## 8. Database maintenance and recovery

### Status and integrity

- Status loads without blocking UI.
- Schema and journal mode are correct.
- Size information is plausible.
- `PRAGMA integrity_check` returns OK on a healthy database.

### Checkpoint and optimize

- WAL checkpoint succeeds.
- Popup and watcher still function afterward.
- Optimize reports before/after values.
- Database integrity remains OK.

### Backup

- Destination inside `.xclip` is rejected.
- Valid `.xclip-backup` contains only the three documented root entries.
- Backup includes persisted settings, history, PINNED, titles, and tags.
- Unapplied Settings draft is not included.

### Restore

1. Create a backup.
2. Add a recognizable clip and change a setting.
3. Cancel restore once and confirm nothing changes.
4. Restore the backup.
5. Confirm XClip exits.
6. Restart.
7. Confirm backed-up history/settings returned.
8. Confirm post-backup changes are absent.
9. Confirm PINNED order, titles, and tags.
10. Confirm integrity after restore.

Reject and preserve live data for:

- damaged ZIP;
- renamed/missing entry;
- additional entry;
- duplicate entry;
- future database schema;
- future config schema;
- failed SQLite integrity.

### Clear actions

- Clear RECENT preserves PINNED and configuration.
- Clear ALL requires confirmation.
- Use backup before Clear ALL.
- No database sidecar remains unexpectedly after terminal deletion.

## 9. Large-data review

Run the explicit M7.3 gate and retain:

```text
summary.json
metrics.csv
environment.properties
PASS.txt
```

Review actual values rather than treating budgets as marketing claims.

The matrix covers:

- 1,000 clips;
- 10,000 clips;
- 50,000 clips;
- 500,000-character clip;
- 1,000 PINNED;
- 256 tags;
- 2,000 duplicate candidates;
- 25,000 retention-eligible RECENT;
- 120 search/filter requests.

Manually open a representative large history and check popup, search, scrolling, tags, and cleanup responsiveness.

## 10. MSI and packaged lifecycle

Build later:

```powershell
.\gradlew.bat clean packageMsi --no-daemon
```

Create the evidence directory:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start_m8_windows_lifecycle_validation.ps1
```

Execute all cases from `docs/M8_WINDOWS_LIFECYCLE_MATRIX.csv` and the generated `results.csv`.

The 18 areas are:

1. clean Windows start;
2. autostart;
3. start minimized;
4. tray lifecycle;
5. secondary launch;
6. Explorer restart;
7. sleep/resume;
8. lock/unlock;
9. display topology change;
10. monitor disconnect;
11. DPI change;
12. user logoff;
13. shutdown;
14. hotkey conflict;
15. stale autostart entry;
16. MSI upgrade;
17. uninstall;
18. reinstall.

Validate evidence:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\validate_m8_windows_lifecycle_evidence.ps1 -EvidenceDirectory "<evidence-directory>"
```

A complete evidence set must produce `PASS.txt`.

## 11. Upgrade, uninstall, and reinstall

Verify with real packages:

- fixed Upgrade UUID performs an upgrade rather than side-by-side installation;
- upgrade preserves `%USERPROFILE%\.xclip`;
- PINNED, tags, settings, and history survive upgrade;
- autostart points to the current executable;
- uninstall removes application files and shortcuts;
- user-data behavior matches the documented installer contract;
- reinstall starts correctly and can load preserved user data;
- no duplicate tray icon or stale process remains.

Do not state uninstall-data behavior in release notes until this is observed with the final MSI.

## 12. Final release gate

Release is blocked by any of:

- failing automated test or gate;
- data loss;
- broken migration;
- broken backup/restore;
- broken Direct Paste;
- broken hotkey or tray;
- off-screen window;
- critical DPI issue;
- unsafe command execution;
- privacy regression;
- retention deleting PINNED;
- incomplete M8 evidence;
- unverified MSI;
- inconsistent version/schema documentation;
- dirty repository.

## 13. Release completion

Only after every previous section passes:

1. replace draft wording in release notes;
2. add final screenshots;
3. generate checksums;
4. confirm clean clone build;
5. commit final release documentation;
6. create the version tag;
7. push tag;
8. publish the GitHub Release;
9. attach MSI and checksums;
10. archive manual and automated evidence.
