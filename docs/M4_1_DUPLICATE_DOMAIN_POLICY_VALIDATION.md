# Milestone 4.1 — Duplicate Domain Policy Validation

**Target:** define the complete duplicate-behavior contract without changing runtime ingestion, persisted configuration, SQLite schema, or Settings UI.

## 1. Scope

Milestone 4.1 adds a pure Java domain model and decision engine for:

- moving a duplicate RECENT clip to the top;
- preserving the existing RECENT position;
- preserving manual PINNED order;
- moving a duplicate PINNED clip to the top;
- normalized or preserved whitespace;
- case-sensitive or case-insensitive matching;
- an unlimited or finite duplicate time window;
- exact-content matching.

The milestone intentionally does **not** apply these options to live capture yet. Runtime wiring and config persistence belong to Milestone 4.2. User-facing controls belong to Milestone 4.3.

## 2. Default contract

`DuplicateBehaviorPolicy.defaults()` formally preserves existing XClip behavior:

```text
RECENT duplicate position:  MOVE_TO_TOP
PINNED duplicate position:  PRESERVE_PIN_POSITION
Whitespace:                 NORMALIZE
Case:                       SENSITIVE
Duplicate time window:      UNLIMITED
Exact-content mode:         OFF
```

## 3. Matching semantics

### Normalized mode

When exact-content mode is disabled:

- `WhitespaceMode.NORMALIZE` trims edges and collapses every whitespace run to one ASCII space;
- `WhitespaceMode.PRESERVE` keeps whitespace unchanged;
- `CaseSensitivity.INSENSITIVE` applies locale-independent `Locale.ROOT` case folding;
- the resulting canonical keys are compared for equality.

### Exact-content mode

When exact-content mode is enabled:

- content is compared character-for-character;
- whitespace and case options are deliberately ignored;
- leading/trailing whitespace, line breaks, and letter case are significant.

### Time window

- `0 ms` means unlimited age;
- a finite boundary is inclusive;
- a match one millisecond beyond the configured boundary becomes `CREATE_NEW_ENTRY`;
- negative timestamps or negative window values are rejected.

## 4. Decision semantics

The engine returns one of these persistence-neutral decisions:

```text
CREATE_NEW_ENTRY
UPDATE_EXISTING_MOVE_RECENT_TO_TOP
UPDATE_EXISTING_PRESERVE_RECENT_POSITION
UPDATE_EXISTING_PRESERVE_PIN_POSITION
UPDATE_EXISTING_MOVE_PIN_TO_TOP
```

No DAO or Config class is referenced by the domain package.

## 5. Automated validation

Run from repository root:

```powershell
.\gradlew.bat clean test --no-daemon
```

Expected coverage includes:

- defaults;
- whitespace normalization;
- preserved whitespace;
- case-insensitive matching;
- exact-content override;
- unlimited and finite windows;
- inclusive window boundary;
- non-match/new-entry behavior;
- recent move-to-top;
- recent preserve-position;
- pinned preserve-position;
- pinned move-to-top;
- invalid policy values.

Then run:

```powershell
.\gradlew.bat build --no-daemon
```

And:

```powershell
git diff --check
```

## 6. Manual regression

Because Milestone 4.1 has no runtime wiring or UI changes, verify only that current behavior did not change:

1. Copy a RECENT clip again and confirm it still moves to the top.
2. Copy a PINNED clip again and confirm its manual pin order remains unchanged.
3. Confirm no duplicate settings are visible yet.
4. Confirm ordinary capture, search, tags, Copy, Direct Paste, pinning, and restart still work.
5. Confirm the existing schema remains version 5.

## 7. Exit gate

Milestone 4.1 is complete only when:

- all tests pass;
- build passes;
- `git diff --check` passes;
- current duplicate runtime behavior remains unchanged;
- no Config or SQLite migration occurs;
- regression checks pass;
- commit and push complete;
- working tree is clean.
