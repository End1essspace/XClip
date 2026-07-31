# Milestone 4.2 — Duplicate Persistence and Runtime Validation

## Scope

This milestone persists the Milestone 4.1 duplicate policy and applies it to
clipboard ingestion. It does not add the dedicated Settings controls planned
for Milestone 4.3.

Changed runtime contracts:

- config schema upgrades from version 1 to version 2;
- database schema upgrades from version 5 to version 6;
- duplicate matching uses four indexed policy-independent hashes;
- finite duplicate windows may retain multiple equal clipboard rows;
- RECENT and PINNED duplicate position decisions are applied by `ClipService`;
- the clipboard watcher forwards exact capped text so case/whitespace policy can decide.

## Persisted config values

After the first start, `%USERPROFILE%\.xclip\config.json` contains canonical values:

```json
{
  "duplicateRecentPosition": "MOVE_TO_TOP",
  "duplicatePinnedPosition": "PRESERVE_PIN_POSITION",
  "duplicateWhitespaceMode": "NORMALIZE",
  "duplicateCaseSensitivity": "SENSITIVE",
  "duplicateWindowMillis": 0,
  "duplicateExactContentMode": false
}
```

Supported values:

```text

duplicateRecentPosition:
  MOVE_TO_TOP
  PRESERVE_EXISTING_POSITION

duplicatePinnedPosition:
  PRESERVE_PIN_POSITION
  MOVE_PIN_TO_TOP

duplicateWhitespaceMode:
  NORMALIZE
  PRESERVE

duplicateCaseSensitivity:
  SENSITIVE
  INSENSITIVE

duplicateWindowMillis:
  0 = unlimited
  positive integer = inclusive window in milliseconds

duplicateExactContentMode:
  false = use whitespace/case options
  true  = exact character-for-character matching
```

Unknown enum strings and negative window values normalize to safe defaults
without discarding unrelated config values.

## Automated gate

Run from the repository root:

```powershell
.\gradlew.bat clean test --no-daemon
```

Expected result:

```text
BUILD SUCCESSFUL
```

Then run:

```powershell
.\gradlew.bat build --no-daemon
```

And:

```powershell
git diff --check
```

## Migration validation

1. Close XClip.
2. Back up `%USERPROFILE%\.xclip\config.json` and `xclip.db`.
3. Start XClip once.
4. Confirm `config.json` now contains `"version": 2` and all six duplicate fields.
5. Confirm existing history, titles, tags, PINNED state, and manual PINNED order remain intact.
6. Restart XClip and confirm no second migration or duplicate-history loss occurs.

Database expectations:

```text
PRAGMA user_version = 6
idx_clip_hash is non-unique
idx_clip_exact_hash exists
idx_clip_exact_ci_hash exists
idx_clip_norm_ci_hash exists
idx_clip_pinned_order exists
```

## Runtime validation matrix

Edit `config.json` only while XClip is fully closed, then restart for each case.
Milestone 4.3 will expose these values in Settings.

### A. Safe defaults

Use the default values shown above.

1. Copy `alpha`.
2. Copy `beta`.
3. Copy `alpha` again.

Expected:

- only one `alpha` row exists;
- `alpha` is at the top of RECENT;
- its use count is incremented internally;
- PINNED metadata remains untouched.

### B. Preserve RECENT position

Set:

```json
"duplicateRecentPosition": "PRESERVE_EXISTING_POSITION"
```

Repeat `alpha → beta → alpha`.

Expected:

- only one `alpha` row exists;
- `beta` remains above `alpha`;
- `alpha` content/usage metadata updates without changing RECENT position.

### C. PINNED duplicate preserves manual order

Set:

```json
"duplicatePinnedPosition": "PRESERVE_PIN_POSITION"
```

1. Pin `first` and `second`.
2. Arrange `second` above `first`.
3. Copy `first` again.

Expected:

- `second` remains above `first`;
- title and tags on `first` remain intact.

### D. PINNED duplicate moves to top

Set:

```json
"duplicatePinnedPosition": "MOVE_PIN_TO_TOP"
```

Repeat the previous scenario.

Expected:

- `first` moves to the top of PINNED;
- title, tags, and pinned state remain intact.

### E. Whitespace normalization

Set:

```json
"duplicateWhitespaceMode": "NORMALIZE",
"duplicateCaseSensitivity": "SENSITIVE",
"duplicateExactContentMode": false
```

Copy:

```text
Alpha  Value
```

then another clip, then:

```text
Alpha<TAB>Value
```

Expected: one matching Alpha row.

### F. Preserve whitespace

Set:

```json
"duplicateWhitespaceMode": "PRESERVE"
```

Repeat the previous scenario.

Expected: two distinct Alpha rows.

### G. Case-insensitive matching

Set:

```json
"duplicateCaseSensitivity": "INSENSITIVE",
"duplicateExactContentMode": false
```

Copy `Alpha`, another clip, then `alpha`.

Expected: one matching row.

### H. Exact-content mode

Set:

```json
"duplicateWhitespaceMode": "NORMALIZE",
"duplicateCaseSensitivity": "INSENSITIVE",
"duplicateExactContentMode": true
```

Copy `Alpha`, another clip, then `alpha`.

Expected: two distinct rows because exact mode overrides normalization and case settings.

### I. Finite duplicate window

Set:

```json
"duplicateWindowMillis": 2000
```

1. Copy `window-test`.
2. Copy another value.
3. Wait more than two seconds.
4. Copy `window-test` again.

Expected: two `window-test` rows are retained.

Repeat within two seconds.

Expected: the newest matching row is updated instead of creating a third row.

## Regression checks

Confirm after the matrix:

- Search text, operators, suggestions, chips, and diagnostics still work;
- toolbar scope/type/tag filters still work;
- Copy and Direct Paste still work;
- self-copy does not create a new history row;
- tag assignment and tag management still work;
- PINNED title and manual ordering still work;
- Clear preserves PINNED entries;
- pause/resume and restart barriers do not ingest stale clipboard text;
- no duplicate tray icon or second process appears.

## Evidence

Capture one screenshot showing:

- the popup with a duplicate-policy result;
- the relevant `config.json` values visible in an editor;
- no error dialog.

Suggested filename:

```text
M4_2-duplicate-persistence-runtime.png
```
