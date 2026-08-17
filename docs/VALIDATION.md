# XClip — Validation and Release Gate

**Development target:** v1.4.0  
**Platform:** Windows 10/11 x64  
**Config schema:** 5  
**SQLite schema:** 6  
**UI contract:** 18  
**Backup format:** 1  
**Updated:** 2026-08-17

This document is the primary validation entry point for the current XClip
development line. It consolidates the active release sequence, the completed
feature-validation history, and the post-freeze v1.4.0 manual checks.

Detailed machine/frozen validation assets remain separate where Gradle or release
scripts reference their existing paths.

---

## 1. Release status

Implemented:

- persistent SQLite WAL clipboard history;
- Direct Paste and Copy fallback;
- PINNED titles and manual ordering;
- tags and batch tag editing;
- advanced search;
- configurable duplicate policy;
- privacy exclusions and optional sensitive-content suppression;
- retention and cleanup;
- nine-page responsive Settings;
- database integrity/checkpoint/optimize/backup/restore;
- large-data validation harness;
- Windows lifecycle hardening assets;
- post-R11 popup/UI polish including the Search Assist overlay, centered
  `X-SERIES` wordmark, Actions-menu readability, maximized top-right Close
  acquisition, and physical-right-edge scrollbar acquisition.

Still required before v1.4.0 release:

1. complete automated regression;
2. coordinated v1.4.0 build/UI-contract metadata migration;
3. full manual UI regression;
4. installed MSI validation;
5. all 18 M8 packaged lifecycle cases;
6. upgrade/uninstall/reinstall/data-preservation proof;
7. final screenshots, checksums, tag, and GitHub Release.

---

## 2. Standard automated gate

Run from the repository root:

```powershell
.\gradlew.bat clean test --no-daemon
.\gradlew.bat build --no-daemon
```

Then:

```powershell
git diff --check
```

Milestone-specific gates remain available through the existing Gradle/scripts
described by their preserved validation assets.

---

## 3. Feature-validation history

The completed feature expansion that leads into v1.4.0 is grouped below.

### Tags

Validated contracts:

- case-insensitive persistent tag identity;
- create/assign/remove;
- atomic batch editing;
- deterministic per-row tag loading without N+1 queries;
- bounded tag chips and `+N` overflow;
- tag filter;
- rename/delete/usage count;
- cleanup of unused tags;
- tag assignment survival across relevant clip operations.

### Advanced Search

Validated contracts:

- parser for `type:`, `is:`, `tag:` and negative operators;
- quoted tag values;
- positive and negative content-type filters;
- required/excluded tags;
- scope integration;
- bounded SQLite query execution;
- stale-result rejection;
- contextual suggestions, active chips, and diagnostics;
- current floating Search Assist overlay does not reserve permanent header height.

Supported examples:

```text
type:url
type:code
type:path
type:json
type:command
type:text
is:pinned
is:recent
tag:work
tag:"Project Work"
-tag:private
-type:text
```

### Duplicate behavior

Validated contracts:

- RECENT duplicate move/preserve;
- PINNED duplicate preserve/move-to-top;
- whitespace normalize/preserve;
- case sensitive/insensitive;
- finite or unlimited duplicate window;
- exact-content mode;
- four persisted equality hashes;
- collision-safe canonical comparison;
- retry-safe persistence behavior.

### Privacy and retention

Validated contracts:

- excluded foreground applications by executable basename;
- optional local payment-card-like suppression;
- optional contextual 4–8 digit OTP suppression;
- fail-open capture behavior;
- no retroactive deletion when privacy rules are enabled;
- general RECENT max age;
- per-type age overrides;
- strictest active retention rule wins;
- PINNED exclusion;
- startup/Apply/manual/periodic cleanup;
- optional clear RECENT on exit;
- bounded transactional deletion.

---

## 4. Preserved technical validation assets

These files intentionally remain separate:

| Area | Canonical assets |
|---|---|
| Settings | `M6_SETTINGS_VALIDATION.md`, `M6_SETTINGS_REGRESSION_MATRIX.csv` |
| Database | `M7_DATABASE_MAINTENANCE.md`, `M7_DATABASE_REGRESSION_MATRIX.csv` |
| Large data | `M7_LARGE_DATA_VALIDATION.md`, `M7_LARGE_DATA_MATRIX.csv` |
| Windows lifecycle | `M8_WINDOWS_LIFECYCLE.md`, `M8_WINDOWS_LIFECYCLE_MATRIX.csv` |
| Responsive/performance | `R10_VALIDATION.md` |
| Frozen regression | `R11_REGRESSION_UI_FREEZE.md`, `R11_REGRESSION_MATRIX.csv`, `R11_SCREENSHOT_SET.csv` |
| Inherited machine UI contract | `UI_CONTRACT_v1.3.0.md` + `app/src/main/resources/ui/ui-contract-v1.3.0.properties` |

The R11/contract paths must not be renamed or removed casually: the current build
uses them as frozen verification inputs. The clean current human-readable UI
contract is [`UI_CONTRACT.md`](UI_CONTRACT.md).

---

## 5. Current v1.4.0 manual UI delta

The following checks are mandatory because they were implemented after the
historical R11 freeze.

### Title bar

- `X-SERIES` wordmark is geometrically centered across the complete title bar;
- wordmark is subtle and does not intercept mouse input;
- minimize/maximize/close remain fully usable;
- dragging and double-click maximize/restore remain correct.

### Maximized Close — Fitts-law edge

On the relevant monitor:

- throw the pointer to the physical top-right corner;
- Close receives hover feedback;
- press inside + release inside closes to background;
- press inside + drag away + release does not close;
- restored mode does not create an outside-window destructive target;
- a neighboring monitor/system UI is never claimed as the Close target.

### Scrollbar — Fitts-law edge

- visible scrollbar remains slim;
- logical interaction lane is easier to acquire than the visible thumb;
- in eligible maximized mode, the physical right edge can acquire the real
  scrollbar thumb by its Y-range;
- dragging from the hard edge moves the real ListView scrollbar;
- restored mode behaves as an ordinary JavaFX scrollbar;
- a right-side system panel or neighboring monitor is not intercepted.

### Actions menu

- menu text and Lucide icons are readable without becoming oversized;
- disabled states remain visually distinct;
- menu sizing does not clip labels at supported scaling.

### Empty state and footer

- `No clips yet` empty state is geometrically centered;
- footer navigation hint is centered against the full footer width rather than
  the asymmetric action-button groups;
- compact footer reflow remains valid.

### Search Assist

- overlay appears only when useful;
- it does not permanently increase header height;
- overlay does not steal unintended list/action input;
- diagnostics and active query state remain readable.

---

## 6. Core functional manual validation

### Clipboard capture

- copying supported text creates history while watcher is enabled;
- Pause prevents new capture;
- Resume restores capture;
- app-originated writes do not create duplicate self-capture;
- large values use bounded previews.

### Popup and keyboard

Validate:

- `Ctrl+Shift+V`;
- tray activation;
- All / Pinned / Recent;
- type and tag filters;
- multi-selection;
- `↑` / `↓`;
- Enter Paste;
- `Ctrl+C`;
- Delete;
- Escape / close-to-background behavior;
- visible focus and accessible names.

### Direct Paste

- previously active external target is restored;
- one standard `Ctrl+V` is sent;
- Copy fallback remains available;
- stale target is invalidated across lifecycle boundaries.

### PINNED and tags

- pin/unpin;
- title edit;
- manual order;
- Move Up/Down/Top/Bottom;
- tag create/assign/remove;
- batch editor;
- rename/delete;
- unused-tag cleanup.

---

## 7. Settings validation

Nine canonical pages:

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

Validate:

- draft/baseline state;
- Apply and Cancel;
- scoped reset;
- inline validation;
- first-error navigation;
- compact/standard/wide responsive modes;
- keyboard navigation and accessibility;
- asynchronous long-running data operations;
- hotkey conflict status/recovery.

Canonical frozen Settings assets remain:

```text
M6_SETTINGS_VALIDATION.md
M6_SETTINGS_REGRESSION_MATRIX.csv
```

---

## 8. Database, backup, and recovery

Use [`M7_DATABASE_MAINTENANCE.md`](M7_DATABASE_MAINTENANCE.md) as the detailed
contract.

Validate at minimum:

- database status/schema/journal information;
- `PRAGMA integrity_check`;
- WAL checkpoint;
- `VACUUM` + `PRAGMA optimize`;
- `.xclip-backup` creation;
- manifest/database/config validation;
- rollback-protected restore;
- application exit after successful restore;
- clear RECENT preserves PINNED;
- clear ALL follows the documented data-ownership contract.

Backup format:

```text
manifest.properties
xclip.db
config.json
```

---

## 9. Large-data validation

Canonical assets:

```text
M7_LARGE_DATA_VALIDATION.md
M7_LARGE_DATA_MATRIX.csv
```

Explicit targets include:

- 1,000 / 10,000 / 50,000 clips;
- 500,000-character clip;
- 1,000 PINNED;
- 256 tags;
- 2,000 duplicate candidates;
- 25,000 retention deletions;
- rapid search churn;
- JavaFX responsiveness, heap, and SQLite-size budgets.

These budgets are release gates, not public benchmark claims.

---

## 10. Windows lifecycle and packaged validation

Canonical assets:

```text
M8_WINDOWS_LIFECYCLE.md
M8_WINDOWS_LIFECYCLE_MATRIX.csv
scripts/start_m8_windows_lifecycle_validation.ps1
scripts/validate_m8_windows_lifecycle_evidence.ps1
```

Formal M8 closure requires the installed MSI and all 18 cases marked PASS with
validated evidence.

The packaged gate must also cover:

- clean install and launch;
- no external Java requirement;
- Explorer restart;
- tray/hotkey recovery;
- sleep/resume;
- lock/unlock;
- display topology and DPI changes;
- single-instance behavior;
- autostart;
- ordered shutdown;
- upgrade;
- uninstall;
- reinstall;
- user-data preservation rules.

---

## 11. Coordinated v1.4.0 metadata gate

The roadmap/release line is v1.4.0, but current technical metadata still includes
the inherited v1.3.0 values.

Before final packaged validation:

- bump `app/build.gradle.kts` to `1.4.0`;
- migrate/version the machine-readable UI contract deliberately;
- update `UiContractFreezeTest` and build expectations atomically;
- verify JAR/MSI metadata and artifact naming;
- rerun automated gates after the bump;
- keep release notes, checksums, and Git tag consistent.

---

## 12. Final release gate

Release only when all applicable items are true:

- automated tests PASS;
- build PASS;
- `git diff --check` PASS;
- current v1.4.0 manual UI delta PASS;
- M6/M7/R10/R11 regressions remain green;
- M7 large-data evidence PASS;
- all 18 M8 packaged cases PASS;
- upgrade/uninstall/reinstall proof PASS;
- README screenshots are final;
- release notes are final;
- checksums are generated;
- version metadata is internally consistent;
- working tree is clean;
- release commit and tag are pushed.

Until then, v1.4.0 remains a development/release target rather than a completed
public release.
