# XClip M5.1 — Excluded Applications Validation

## Scope

Milestone 5.1 adds process-based foreground application exclusions.

The privacy gate applies only to new clipboard changes. It does not delete or
rewrite existing clipboard history.

## Automated gate

Run from the repository root:

```powershell
.\gradlew.bat clean test --no-daemon
```

```powershell
.\gradlew.bat build --no-daemon
```

```powershell
git diff --check
```

All three commands must pass before commit.

## Configuration migration

1. Close XClip completely.
2. Back up `%USERPROFILE%\.xclip\config.json`.
3. Start XClip.
4. Confirm `config.json` now contains:

```json
"version": 3,
"excludedApplications": []
```

5. Confirm all previous capture, duplicate, startup, and window settings remain unchanged.

## Settings UI

1. Open Settings.
2. Confirm a `Privacy — excluded applications` section is visible.
3. Confirm the list accepts one executable per line.
4. Enter:

```text
notepad
CHROME.EXE
C:\Program Files\KeePass Password Safe 2\KeePass.exe
```

5. Click Apply.
6. Reopen Settings and confirm the canonical values are:

```text
notepad.exe
chrome.exe
keepass.exe
```

7. Confirm duplicate entries collapse case-insensitively.
8. Confirm `*.exe` is rejected and the invalid field receives an error state.
9. Confirm Clear exclusions removes only the exclusion list after Apply.
10. Confirm closing Settings without Apply restores the last saved list.

## Runtime exclusion

Use Notepad because its executable identity is stable (`notepad.exe`).

1. Add `notepad.exe` to exclusions and Apply.
2. Focus Notepad.
3. Copy a unique value such as:

```text
XCLIP_M5_1_BLOCKED_NOTEPAD
```

4. Open XClip and confirm the value is absent from history.
5. Without copying anything else, switch to another application and wait at least five seconds.
6. Open XClip again and confirm the blocked value is still absent.
7. Remove `notepad.exe` from exclusions and Apply.
8. Focus Notepad and copy a new unique value:

```text
XCLIP_M5_1_ALLOWED_NOTEPAD
```

9. Confirm the new value appears in history.

## Fail-open behavior

The runtime intentionally allows capture when the foreground process cannot be
identified. This prevents silent clipboard data loss from transient Windows API,
permission, or process-lifecycle failures.

Manual simulation of resolver failure is not required for the release gate; it
is covered by automated tests.

## Regression checks

Confirm all remain functional:

- pause/resume;
- watcher enable/disable;
- Direct Paste;
- Copy-only;
- duplicate settings and runtime behavior;
- tags;
- advanced search;
- Settings Apply/Close;
- restart persistence;
- tray open and global hotkey.

## Acceptance

Milestone 5.1 passes when:

- automated tests and build are green;
- config migrates to version 3 without losing unrelated settings;
- excluded Notepad content never enters history;
- the same blocked clipboard value is not captured after switching windows;
- removing the exclusion restores capture immediately;
- resolver failures remain fail-open;
- `git diff --check` is clean.
