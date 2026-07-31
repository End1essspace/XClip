# M3.1 — Advanced Search Parser Foundation Validation

## Scope

Milestone 3.1 introduces the pure Java parser contract for advanced-search
expressions. It does **not** connect parsed operators to SQLite, popup reloads,
highlighting, suggestions, or active query chips. Those belong to M3.2 and
M3.3.

No database migration is required. The SQLite schema remains version 5.

## Supported syntax

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
-tag:"Private Client"
-type:text
```

Operator names and enum values are case-insensitive.

## Parser contract

- Whitespace separates tokens outside quoted values.
- Double quotes group values containing spaces.
- `\"` and `\\` are supported inside quoted values.
- Type, scope, and tag clauses preserve source order.
- Duplicate clauses are preserved for deterministic later execution.
- Remaining non-operator text is exposed through `SearchQuery.text()`.
- Unknown operator syntax remains ordinary text without an error.
- Recognized but invalid operator fragments remain ordinary text and produce a
  non-fatal `SearchQueryIssue`.
- An unterminated quote falls back to the complete raw query and produces one
  `UNTERMINATED_QUOTE` issue.
- Negative operators are supported for `type:` and `tag:`.
- Negative `is:` is rejected through non-fatal fallback.
- Tag values use the existing `TagNamePolicy`, including whitespace
  normalization, the 64-character limit, and control-character rejection.
- Returned lists are immutable.
- Parser code has no JavaFX, DAO, SQLite, or filesystem dependency.

## Automated validation

Run from the repository root:

```powershell
.\gradlew.bat clean test --no-daemon
```

The M3.1 tests cover:

1. all supported operator families;
2. positive and negative clauses;
3. quoted multi-word tag values;
4. quoted escape handling;
5. pure text remainder;
6. source-order preservation;
7. duplicate clause preservation;
8. case-insensitive parsing;
9. missing values;
10. unsupported values;
11. unsupported `-is:` negation;
12. unknown operator fallback;
13. unterminated quote full fallback;
14. null and empty input;
15. immutable result collections;
16. canonical operator text.

## Build and diff gates

```powershell
.\gradlew.bat build --no-daemon
```

```powershell
git diff --check
```

## Manual smoke validation

M3.1 has no user-visible UI surface. The manual gate is therefore limited to
confirming that existing search remains unchanged:

1. Start XClip.
2. Search for ordinary clipboard content.
3. Search for text containing a colon, such as `https://` or `key:value`.
4. Confirm that search, filters, tag filter, selection, Copy, and Direct Paste
   behave exactly as before.
5. Confirm that typing `type:url` still behaves as ordinary text in the popup
   until M3.2 connects parser execution.
6. Restart XClip and confirm no persistence or startup regression.

## Exit criteria

- all tests pass;
- build passes;
- `git diff --check` passes;
- ordinary popup search is unchanged;
- Direct Paste and tag workflows have no regression;
- no schema or config migration was introduced;
- commit and push complete;
- working tree is clean.
