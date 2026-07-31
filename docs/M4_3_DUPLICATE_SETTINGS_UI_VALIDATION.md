# M4.3 Duplicate Settings UI Validation

## Scope

Milestone 4.3 exposes the duplicate policy implemented by M4.1 and M4.2 through
the product Settings window.

This milestone changes no database schema and no config version.

Expected versions:

```text
Config version: 2
Database schema: 6
UI contract revision: 7
```

## Automated gate

Run from the repository root:

```powershell
.\gradlew.bat clean test --no-daemon
```

Then:

```powershell
.\gradlew.bat build --no-daemon
```

Then:

```powershell
git diff --check
```

All commands must complete successfully before manual validation.

## Baseline safety

Before manual testing:

1. Exit XClip completely from the tray.
2. Back up `%USERPROFILE%\.xclip\config.json`.
3. Back up `%USERPROFILE%\.xclip\xclip.db`.
4. Start the development build.
5. Open Settings with `Ctrl+,`.

Existing history, tags, pinned titles, and manual pinned order must still be
present.

## Settings layout

Verify:

- Settings opens at a usable size.
- The content area scrolls vertically.
- Apply, Close, and Open data folder remain visible while content scrolls.
- The Duplicate behavior section is visually distinct.
- Every duplicate row has a title and explanation.
- Keyboard Tab navigation reaches every duplicate control.
- Focus rings remain visible.
- No control overlaps or clips at 100%, 125%, and 150% Windows scaling.
- Resize the Settings window down to its minimum size and confirm all content
  remains reachable through scrolling.

## Safe defaults

Press `Reset duplicate defaults`, then Apply.

Expected visible values:

```text
Recent duplicates: Move duplicate to top
Pinned duplicates: Keep pinned position
Whitespace: Normalize whitespace
Letter case: Case-sensitive
Duplicate window: Unlimited
Exact content mode: Off
```

Verify unrelated values such as Max history, UI clip limit, capture enabled,
startup mode, and autostart are unchanged.

Restart XClip and confirm the same duplicate values are restored.

## RECENT positioning

Set:

```text
Recent duplicates: Move duplicate to top
Duplicate window: Unlimited
```

Copy in this order:

```text
M43-A
M43-B
M43-A
```

Expected: one `M43-A` row appears above `M43-B`.

Change Recent duplicates to `Keep existing position`, Apply, then copy:

```text
M43-C
M43-D
M43-C
```

Expected: one `M43-C` row remains below `M43-D`.

## PINNED positioning

Create and pin two distinct clips. Arrange them manually so clip A is below
clip B.

With `Keep pinned position`, copy clip A again.

Expected: clip A stays in its manual position.

With `Move pinned clip to top`, copy clip A again.

Expected: clip A moves to the top of PINNED.

## Whitespace matching

Set:

```text
Whitespace: Normalize whitespace
Letter case: Case-sensitive
Exact content mode: Off
Duplicate window: Unlimited
```

Copy:

```text
alpha beta
```

Then copy a version containing multiple spaces or a TAB between the words.

Expected: one row.

Change Whitespace to `Preserve whitespace`, Apply, and repeat with new text.

Expected: separate rows.

## Letter-case matching

Set:

```text
Whitespace: Normalize whitespace
Letter case: Ignore case
Exact content mode: Off
```

Copy:

```text
M43-Case
m43-case
```

Expected: one row.

Change Letter case to `Case-sensitive`, Apply, and repeat with new text.

Expected: separate rows.

## Exact-content override

Enable `Require exact content`.

Verify:

- Whitespace is disabled.
- Letter case is disabled.
- The yellow override explanation is visible.
- Previously selected whitespace/case values remain visible but unavailable.

Copy two values that differ only by case or whitespace.

Expected: separate rows.

Disable exact content.

Expected:

- Whitespace and Letter case become enabled.
- Their prior selections are restored.

## Duplicate window presets

Select `2 seconds`, Apply.

Copy one unique value twice within two seconds.

Expected: one row.

Wait more than two seconds and copy it again.

Expected: a second row.

Select `Unlimited`, Apply, and repeat with another value after a delay.

Expected: one row.

## Custom duplicate window

Select `Custom…`.

Verify the custom millisecond field appears.

Enter:

```text
2500
```

Apply, close Settings, reopen Settings.

Expected:

- `Custom…` remains selected.
- `2500` is preserved exactly.

Verify blank custom input blocks Apply and shows an inline error/status.
Verify a value larger than `Long.MAX_VALUE` is rejected without changing the
saved config.

## Reset and discard behavior

1. Change several duplicate controls.
2. Press Reset duplicate defaults.
3. Close Settings without Apply.
4. Reopen Settings.

Expected: the last saved values return; reset was discarded.

Repeat Reset and press Apply.

Expected: defaults persist after restart.

## Runtime regression

Verify after applying duplicate settings:

- clipboard capture still works;
- pause/resume still works;
- Search still works;
- scope/type/tag filters still work;
- tag assignment and management still work;
- Copy-only still works;
- Direct Paste still works;
- Clear keeps PINNED clips;
- restart preserves history and configuration;
- no unexpected duplicate rows appear under default policy.

## Evidence

Capture one screenshot showing the complete Duplicate behavior section:

```text
M4_3-duplicate-settings-ui.png
```

Record the following with the milestone:

- successful `clean test`;
- successful `build`;
- successful `git diff --check`;
- screenshot filename;
- Windows scaling used;
- manual validation result.
