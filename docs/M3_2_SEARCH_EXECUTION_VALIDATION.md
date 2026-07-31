# M3.2 Advanced Search Execution Validation

## Scope

Milestone 3.2 connects the M3.1 parser to the existing popup reload pipeline.
It changes search behavior only; it does not redesign the search field or add
suggestions, active query chips, or inline syntax errors.

Supported executable syntax:

```text
type:text
type:code
type:url
type:path
type:json
type:command
is:pinned
is:recent
tag:work
tag:"Project Work"
-type:text
-tag:private
```

## Execution contract

- Toolbar scope/type/tag filters are ANDed with search operators.
- Multiple positive `type:` values use OR semantics.
- Multiple positive `tag:` values use AND semantics.
- Negative type/tag values exclude matching clips.
- Exact tag identity is case-insensitive.
- Pure text searches content, pinned title, and assigned tag name.
- Valid operator syntax is not used for row highlighting.
- Invalid recognized syntax falls back to ordinary text.
- Contradictory constraints produce an empty result without an exception.
- DAO ordering is preserved.
- Derived type scans are bounded to 5,000 candidates unless the UI limit is higher.
- Stale asynchronous results cannot replace a newer search result.

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

Expected coverage includes:

- parser regression tests;
- execution-plan combination and conflict tests;
- type OR/exclusion behavior;
- stable ordering and final limit;
- SQL required-tag AND semantics;
- SQL negative-tag exclusion;
- toolbar selected-tag combination;
- machine-readable UI contract revision 5;
- existing stale generation gate tests.

## Manual matrix

Prepare clips representing TEXT, CODE, URL, PATH, JSON, and COMMAND. Create
`Work`, `Urgent`, and `Private` tags and assign them to overlapping clips.
Pin at least one clip and give it a custom title.

1. Search `type:url` and confirm only URL clips are shown.
2. Search `type:url type:json` and confirm URL or JSON clips are shown.
3. Search `-type:text` and confirm TEXT clips are excluded.
4. Search `is:pinned` and confirm only pinned clips are shown.
5. Select toolbar `Recent`, then search `is:pinned`; confirm an empty result.
6. Search `tag:Work`; confirm exact case-insensitive tag matching.
7. Search `tag:Work tag:Urgent`; confirm clips must have both tags.
8. Search `tag:Work -tag:Private`; confirm Private clips are excluded.
9. Select toolbar tag `Work`, then search `tag:Urgent`; confirm both constraints apply.
10. Select toolbar type `JSON`, then search `type:url type:json`; confirm only JSON remains.
11. Search `release tag:Work`; confirm `release` matches content, pinned title, or tag name while `Work` remains required.
12. Search `tag:"Project Work"` and confirm quoted tag values work.
13. Search `type:video`; confirm it is treated as ordinary text and the popup remains responsive.
14. Search `tag:"Project Work` with an unterminated quote; confirm full-query text fallback and no crash.
15. Search `type:url -type:url`; confirm an empty result without an error dialog.
16. Rapidly alternate `type:url`, `type:json`, and `tag:Work`; confirm an older result never replaces the newest query.
17. Confirm rows retain pinned manual order and RECENT recency order.
18. Confirm only the pure-text remainder is highlighted; `type:`, `is:`, and `tag:` syntax is not highlighted.
19. Clear the search and confirm ordinary toolbar filters still work.
20. Verify Copy, Direct Paste, selection, Tags editor, Manage tags, pinning, delete, and Clear remain unchanged.

## Evidence

Capture one screenshot showing a combined query and toolbar filter:

```text
M3_2-advanced-search-execution.png
```

Recommended example:

```text
release type:code tag:Work -tag:Private
```

## Non-goals

Deferred to M3.3:

- syntax hint;
- autocomplete/suggestions;
- active operator chips;
- visible parser diagnostics;
- saved queries.

Database schema remains v5. Config schema remains v1.
