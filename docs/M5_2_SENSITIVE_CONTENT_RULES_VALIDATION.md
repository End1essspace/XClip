# XClip M5.2 — Sensitive Content Rules Validation

## Scope

Milestone 5.2 adds explicit opt-in rules that can skip newly changed clipboard
text before persistence.

Implemented rules:

- payment-card-like values;
- contextual one-time codes.

The default action for every rule is `CAPTURE`, preserving previous behavior.
Existing history is never scanned, rewritten, or deleted.

Not part of this milestone:

- automatic cleanup or retention;
- password-manager discovery;
- standalone-number OTP detection;
- content redaction or masked persistence;
- automatic deletion of existing clips.

Password-manager apps remain configurable through the M5.1 executable exclusion
list. Retention and cleanup belong to M5.3.

## Detection contract

### Payment cards

A candidate is classified only when all conditions pass:

1. 13–19 digits;
2. spaces and hyphens are the only accepted internal separators;
3. safe alphanumeric boundaries;
4. leading digit is in the common payment-card range `2`–`6`;
5. digits are not all identical;
6. Luhn checksum is valid.

### One-time codes

A candidate is classified only when:

1. it contains 4–8 digits;
2. it is not part of a longer digit sequence;
3. explicit OTP, 2FA, verification, authentication, login, or equivalent
   Russian/Uzbek wording occurs within the bounded context window.

A standalone value such as `482913` is not classified as OTP.

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

All commands must pass before commit.

Expected automated coverage includes:

- config v3 to v4 migration;
- canonical action persistence;
- wither preservation;
- valid and invalid Luhn candidates;
- separator and boundary behavior;
- contextual English, Russian, and Uzbek OTP messages;
- standalone-number false-positive protection;
- per-rule capture/skip behavior;
- immediate runtime gate updates;
- foreground exclusion regression;
- UI contract revision 9;
- packaged Settings CSS selectors.

## Manual validation

Use synthetic test data only.

### Default behavior

1. Open Settings.
2. Confirm both sensitive-content actions show `Capture normally`.
3. Copy `4111 1111 1111 1111` from Notepad.
4. Confirm it appears in XClip.
5. Copy `Your verification code is 482913`.
6. Confirm it appears in XClip.

### Payment-card rule

1. Set Payment card numbers to `Skip capture` and Apply.
2. Copy a new unique ordinary text value; confirm it is captured.
3. Copy `Card: 4111 1111 1111 1111`; confirm it is not captured.
4. Switch applications without changing the clipboard; confirm the skipped
   value does not appear later.
5. Copy `4111 1111 1111 1112`; confirm the invalid-Luhn value is captured.
6. Copy `Invoice 123456789012`; confirm it is captured.

### One-time-code rule

1. Set One-time codes to `Skip capture` and Apply.
2. Copy `Your verification code is 734122`; confirm it is not captured.
3. Copy `Код подтверждения: 734123`; confirm it is not captured.
4. Copy `Tasdiqlash kodi 734124`; confirm it is not captured.
5. Copy standalone `734125`; confirm it is captured.
6. Copy `Invoice 734126 was paid`; confirm it is captured.

### Combined rules and runtime Apply

1. Enable `Skip capture` for both rules.
2. Confirm both synthetic sensitive examples are skipped.
3. Change both actions back to `Capture normally` and Apply without restarting.
4. Copy new synthetic examples; confirm both are captured.

### Reset and discard

1. Set one or both rules to `Skip capture`.
2. Press Reset sensitive rules.
3. Close Settings without Apply.
4. Reopen Settings; confirm the last saved actions return.
5. Repeat Reset and Apply.
6. Restart XClip; confirm both actions remain `Capture normally`.

### Regression

Verify:

- M5.1 excluded applications still block by foreground executable;
- ordinary clipboard capture works;
- watcher pause/resume works;
- duplicate policies remain unchanged;
- Search, filters, Tags, Copy, and Direct Paste work;
- no skipped value appears after switching windows without another clipboard change;
- no existing history row is deleted or modified;
- database schema remains version 6.

## Evidence

Capture:

```text
M5_2-sensitive-content-settings.png
```

Record:

- successful `clean test`;
- successful `build`;
- successful `git diff --check`;
- screenshot filename;
- Windows scaling;
- manual validation result.

## Exit gate

M5.2 is complete only when automated checks, manual validation, commit, push,
and clean working-tree confirmation all pass.
