# XClip M3.3 — Search UI validation

## Scope

Milestone 3.3 adds a visible assistance layer to the already executable advanced-search pipeline.

Implemented surface:

- inline syntax hint while Search is focused;
- contextual operator suggestions;
- suggestions derived from the current token and persisted tag catalog;
- active operator chips;
- bounded `+N` chip overflow;
- inline non-blocking parser diagnostics;
- Search syntax reference in Quick Help;
- pure-text-only result highlighting.

Saved queries remain an optional deferred roadmap item and are not part of this milestone.

No database migration is introduced. SQLite schema remains version 5.

## Automated gate

Run from the repository root:

```powershell
.\gradlew.bat clean test --no-daemon
```

Expected:

- all existing tests pass;
- `SearchUiModelTest` passes;
- `QuickHelpContentTest` passes;
- `UiContractFreezeTest` validates contract revision 6.

Build:

```powershell
.\gradlew.bat build --no-daemon
```

Whitespace gate:

```powershell
git diff --check
```

## Manual validation

### 1. Empty focused Search

1. Open XClip.
2. Focus Search with `Ctrl+K`.
3. Confirm the inline syntax hint appears.
4. Confirm starter suggestions include `type:url`, `type:code`, and `is:pinned`.
5. Confirm the list remains usable and no modal window opens.

### 2. Keyboard suggestion access

1. Focus an empty Search field.
2. Press `Down`.
3. Confirm the first suggestion receives keyboard focus.
4. Use Left/Right or Up/Down to move between suggestions.
5. Press Enter on `type:url`.
6. Confirm only the current token is replaced and Search regains focus.

### 3. Contextual type suggestions

1. Enter `type:`.
2. Confirm only type suggestions are shown.
3. Select `type:json`.
4. Confirm a `type:json` active chip appears.
5. Confirm results match JSON clips.

Repeat for `-type:` and confirm negative suggestions.

### 4. Contextual scope suggestions

1. Enter `is:`.
2. Confirm `is:pinned` and `is:recent` suggestions.
3. Select each value and verify the resulting scope.

### 5. Contextual tag suggestions

1. Create tags `Work`, `Private`, and `Project Work`.
2. Enter `tag:Pro`.
3. Confirm the suggestion is rendered as `tag:"Project Work"`.
4. Select it and confirm the quoted operator is inserted.
5. Enter `-tag:Pri` and confirm `-tag:Private` is suggested.
6. Rename or delete a tag through Manage tags.
7. Return to Search and confirm suggestions use the refreshed tag catalog.

### 6. Token replacement safety

Use:

```text
release tag:Pro -type:text
```

Place the caret after `tag:Pro`, select `tag:"Project Work"`, and confirm the result is:

```text
release tag:"Project Work" -type:text
```

The prefix and suffix must remain unchanged.

### 7. Active operator chips

1. Enter:

```text
release type:url -type:text is:pinned tag:Work -tag:Private
```

2. Confirm five chips are visible.
3. Confirm type, scope, tag, and negative operators have distinct restrained styling.
4. Confirm ordinary word `release` does not become a chip.
5. Confirm chips remain visible when Search loses focus.

### 8. Chip overflow

Enter more than six valid operators.

Confirm:

- only six operator chips are rendered;
- one deterministic `+N` chip represents the remainder;
- no horizontal clipping occurs at 100%, 125%, or 150% scaling.

### 9. Inline error display

Test:

```text
type:video
```

Confirm:

- an inline error explains the invalid value;
- the query fragment is treated as ordinary text;
- no modal dialog appears;
- Search and the rest of the popup remain responsive.

Test an unterminated quote:

```text
tag:"Project Work
```

Confirm the same non-blocking fallback behavior.

### 10. Text-only highlighting

Use:

```text
release type:code tag:Work
```

Confirm:

- only `release` is highlighted in clip content/title/tag text;
- `type:code` and `tag:Work` are not highlighted as content;
- operator chips still show both valid operators.

### 11. Quick Help

1. Open Quick Help.
2. Confirm the `Search syntax` section contains:
   - `type:url`;
   - `-type:text`;
   - `is:pinned / is:recent`;
   - `tag:work`;
   - `-tag:private`;
   - `tag:"Project Work"`.
3. Confirm the popover remains scroll-safe and keyboard accessible.

### 12. Responsive regression

Validate at narrow, balanced, and wide window widths:

- search field never becomes horizontally clipped;
- assistance rows wrap naturally;
- status and header controls remain reachable;
- filters, list, and footer retain their existing layout contracts.

### 13. Functional regression

Confirm unchanged behavior for:

- ordinary text search;
- toolbar scope/type/tag filters;
- combined advanced queries;
- selection and multi-selection;
- Copy and Direct Paste;
- pinned titles and ordering;
- tag assignment and management;
- safe type actions;
- Delete and Clear;
- Escape behavior;
- stale-result protection while typing rapidly.

## Screenshot evidence

Capture:

```text
M3_3-search-ui.png
```

The screenshot should show:

- focused Search;
- at least one active operator chip;
- contextual suggestions or syntax hint;
- unchanged filters and clip list.

## Git gate

After automated and manual validation:

```powershell
git add .; git commit -m "Add advanced search assistance UI"; git push
```
