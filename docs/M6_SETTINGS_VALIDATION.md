
# XClip M6 Settings Validation

> **Version-line status — 2026-08-17:** v1.3.0 is the historical 2026-06-05 release. The active development/release target and Gradle metadata are **v1.4.0**. The current machine-readable UI contract is `ui-contract-v1.4.0.properties` revision 19; the v1.3.0 resource remains frozen only for historical R11 evidence.



> **Current baseline note — 2026-08-17:** this remains the historical M6 Settings validation record. The application still declares v1.4.0 / config schema 5 / SQLite schema 6 / UI contract 18. Later M9.2 popup-only polish does not change Settings semantics or invalidate this milestone.

**Product version:** v1.4.0
**UI contract revision:** 15
**Milestone:** M6.5 — Responsive, accessibility and regression gate

## Purpose

This gate freezes the completed M6 Settings architecture after page extraction,
draft lifecycle, product pages, responsive layout, and accessibility hardening.

The canonical case list is
[`M6_SETTINGS_REGRESSION_MATRIX.csv`](M6_SETTINGS_REGRESSION_MATRIX.csv).

## Automated gate

Run from the repository root:

```powershell
.\gradlew.bat clean m6SettingsGate --no-daemon
```

The task includes:

- the complete standard test suite;
- the deterministic randomized-order repeat from `c8BaselineGate`;
- packaged UI resource verification;
- frozen UI contract verification;
- M6 regression asset verification.

Then run:

```powershell
.\gradlew.bat build --no-daemon
```

```powershell
git diff --check
```

## Required responsive matrix

Validate Settings on Windows with these logical conditions:

| Case | Required result |
|---|---|
| Default 960×640 | Standard two-column Settings layout |
| Minimum 840×520 | Compact layout; stacked setting rows; no horizontal clipping |
| 1366×768 at 125% | Initial height fits Windows visual bounds; title bar and footer remain visible |
| Width ≥1180 | Wide spacing without semantic or focus-order changes |
| Runtime resize | Compact, standard, and wide modes switch without losing values |

Every page must keep an independent vertical scroll surface. Horizontal scrolling
is not part of the Settings contract.

## Keyboard and accessibility matrix

- Opening Settings focuses the selected sidebar item.
- `Up`, `Down`, `Home`, and `End` navigate the sidebar.
- `Tab` and `Shift+Tab` reach page controls and footer actions.
- Navigation items expose page title, canonical position, and page purpose.
- Page scroll surfaces expose page-specific accessible names and keyboard help.
- Validation feedback is focusable and behaves as an action.
- Mouse click, `Enter`, and `Space` on validation feedback focus the first invalid field.
- Navigation, fields, validation feedback, and footer buttons have visible focus states.
- Apply remains available only for a dirty and valid draft.
- Cancel restores the saved baseline.

## Manual completion record

Record PASS/FAIL and evidence outside the repository for every matrix row. A failed
row blocks the M6.5 Git gate.
