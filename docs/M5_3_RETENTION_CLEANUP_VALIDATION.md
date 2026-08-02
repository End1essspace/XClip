# M5.3 — History retention and cleanup validation

## Scope

This milestone implements the roadmap requirements:

- auto-delete `RECENT` older than `N` days;
- preserve `PINNED`;
- per-content-type retention;
- clear `RECENT` on exit;
- cleanup status.

Config advances to version `5`. SQLite schema remains version `6`.

## Safety contract

- All age-based cleanup is disabled by default.
- Clear on exit is disabled by default.
- `PINNED` clips are never deleted by retention or exit cleanup.
- A type override value of `0` disables that override.
- When the general age and a type override both apply, the shorter age wins.
- “Older than N days” is strict: an entry exactly on the boundary is retained.
- Cleanup never rewrites clipboard content or pinned order.
- Existing tag relations disappear only through the existing SQLite cascade when
  their RECENT clip is explicitly deleted by policy.

## Automated gate

Run from the repository root:

```powershell
.\gradlew.bat clean test --no-daemon
```

Expected:

- all tests pass;
- retention policy boundary tests pass;
- config v4 → v5 migration tests pass;
- cleanup service preserves PINNED and applies per-type rules;
- UI contract revision `10` passes;
- CSS resource tests pass.

Then run:

```powershell
.\gradlew.bat build --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

Finally:

```powershell
git diff --check
```

Expected: no output.

## Manual validation preparation

1. Back up `%USERPROFILE%\.xclip\config.json` and `xclip.db`.
2. Start XClip with a test history containing:
   - several old-looking test entries;
   - at least one TEXT, URL, CODE, PATH, JSON, and COMMAND entry;
   - at least two PINNED entries.
3. Open Settings → `History retention & cleanup`.
4. Confirm defaults:
   - `Auto-delete old RECENT clips` is off;
   - every type override is `0`;
   - `Clear all RECENT clips when XClip exits` is off.

Because normal UI-created entries use the current timestamp, exact age-boundary
validation is covered by automated tests. Manual checks focus on persistence,
runtime triggers, clear-on-exit behavior, status, and PINNED safety.

## Manual matrix

### M5.3-01 — Defaults are non-destructive

1. Leave every retention control at its default.
2. Press Apply.
3. Press `Run cleanup now`.

Expected:

- no history entry disappears;
- status reports `skipped` and `0 deleted`;
- PINNED and RECENT remain unchanged.

### M5.3-02 — General policy persistence

1. Enable `Auto-delete old RECENT clips`.
2. Set general age to `30` days.
3. Press Apply.
4. Close and reopen Settings.
5. Restart XClip and reopen Settings.

Expected:

- enabled state and `30` days persist;
- config.json contains config version `5` and canonical retention fields;
- startup cleanup updates the status line without blocking startup.

### M5.3-03 — Type override persistence

1. Set URL override to `7`.
2. Set COMMAND override to `2`.
3. Leave TEXT/CODE/PATH/JSON at `0`.
4. Press Apply and restart XClip.

Expected:

- all values round-trip exactly;
- `0` remains visible as disabled override;
- Settings explains that the shortest applicable age wins.

### M5.3-04 — Manual cleanup status

1. Press `Run cleanup now`.
2. Keep Settings open until the status changes.

Expected:

- the UI remains responsive;
- status includes outcome, deleted count, timestamp, and detail;
- if rows were removed, an open popup refreshes from storage.

### M5.3-05 — PINNED preservation

1. Ensure at least two entries are PINNED.
2. Run manual cleanup with any enabled age rules.
3. Reopen the popup.

Expected:

- every PINNED entry remains;
- titles, tags, and manual pinned order remain unchanged.

### M5.3-06 — Clear RECENT on exit

1. Ensure history contains both RECENT and PINNED clips.
2. Enable `Clear all RECENT clips when XClip exits` and Apply.
3. Exit through the XClip tray menu.
4. Start XClip again.

Expected:

- all RECENT clips are gone;
- all PINNED clips remain;
- no duplicate XClip process or shutdown hang occurs.

### M5.3-07 — Disable clear on exit

1. Disable `Clear all RECENT clips when XClip exits` and Apply.
2. Create a new RECENT clip.
3. Exit and restart XClip.

Expected: the new RECENT clip remains.

### M5.3-08 — Reset retention defaults

1. Configure several custom retention values.
2. Press `Reset retention defaults`.
3. Confirm the controls change but config.json is not yet modified.
4. Close Settings without Apply and reopen it.
5. Repeat reset and press Apply.

Expected:

- closing without Apply restores saved values;
- applying reset disables general cleanup, sets all overrides to `0`, keeps the
  general default at `30`, and disables clear on exit;
- unrelated privacy, duplicate, capture, and startup settings remain unchanged.

### M5.3-09 — Existing privacy behavior regression

1. Keep one M5.1 application exclusion.
2. Keep at least one M5.2 sensitive rule set to `Skip capture`.
3. Apply retention changes.
4. Repeat one excluded-app capture and one sensitive-content capture test.

Expected: M5.1 and M5.2 behavior remains unchanged.

## Completion gate

M5.3 is complete only after:

- `clean test` passes;
- `build` passes;
- `git diff --check` is clean;
- manual scenarios M5.3-01 through M5.3-09 pass;
- commit and push are completed as a separate final step.
