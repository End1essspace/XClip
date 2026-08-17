## Summary

Describe what changed and keep the PR focused on one concern.

## Why

Explain the problem or requirement this change addresses.

## Validation

List the checks you ran. For normal code changes, the baseline is:

- `\.\gradlew.bat clean test --no-daemon`
- `\.\gradlew.bat build --no-daemon`
- `git diff --check`

Add relevant manual validation for UI, Windows lifecycle, persistence, or packaging changes.

## Risk

Describe compatibility, migration, privacy, data-loss, Windows integration, or regression risks. Write `None identified` only when that is genuinely the case.

## Checklist

- [ ] The change is scoped and does not mix unrelated refactoring.
- [ ] Tests were added or updated where behavior changed.
- [ ] Existing local-first, privacy, and safe-action guarantees are preserved.
- [ ] SQLite/config changes are migration-compatible when applicable.
- [ ] User-facing behavior is documented when applicable.
- [ ] No secrets, private clipboard contents, build output, or local user data are included.
