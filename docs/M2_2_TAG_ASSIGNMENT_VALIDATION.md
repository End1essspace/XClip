# Milestone 2.2 — Create and assign tags

**Scope:** Visible tag creation and assignment for one clip or the current
multi-selection.

## Implemented contract

- `Actions → Tags…` opens the editor.
- The same entry is available from a row context menu.
- Single selection shows assigned and unassigned tags.
- Multi-selection uses three states:
  - checked — assign to every selected clip;
  - unchecked — remove from every selected clip;
  - mixed — preserve existing per-clip differences.
- New tag names are staged in memory and are not persisted on Cancel.
- Whitespace is normalized and identity is case-insensitive.
- Maximum tag-name length is 64 characters.
- Typing an existing tag name selects that tag instead of creating a duplicate.
- Save creates new tags and applies all additions/removals in one SQLite transaction.
- Any validation or database failure rolls back the complete edit.
- Clipboard content, pinned state, pinned order, search state, and selection are unchanged.

## Automated gate

Run from the repository root:

```powershell
.\gradlew.bat clean test
```

Then:

```powershell
.\gradlew.bat build
```

Finally:

```powershell
git diff --check
```

Required test coverage:

- shared tag-name normalization;
- blank/control/overlong validation;
- assigned/unassigned/mixed derivation;
- mixed-state preservation;
- existing-tag duplicate selection;
- pending duplicate rejection;
- atomic multi-clip assignment;
- new-tag creation within the save transaction;
- rollback on an invalid clip or tag id;
- frozen UI contract revision 2.

## Manual matrix

Use synthetic clipboard content.

1. Select one clip and open `Actions → Tags…`.
2. Create `Work`, save, reopen, and confirm it is assigned.
3. Enter ` work ` again and confirm the existing tag is selected instead of duplicated.
4. Uncheck `Work`, save, reopen, and confirm the assignment is removed.
5. Cancel after staging a new tag and confirm the tag was not created.
6. Select multiple clips where only one has `Work`; confirm `Work` appears mixed.
7. Save without touching the mixed checkbox; confirm existing differences remain.
8. Change mixed `Work` to checked; confirm all selected clips receive it.
9. Change checked `Work` to unchecked; confirm all selected clips lose it.
10. Create two new tags in one edit and confirm both are assigned to every selected clip.
11. Open the same editor from a row context menu.
12. Verify Search, scope/type filters, Direct Paste, Copy, Pin/Unpin, Delete, and popup auto-hide still behave as before.
13. Verify the editor at 100%, 125%, and 150% Windows scaling.
14. Verify keyboard focus reaches the name field, Add, tag checkboxes, Cancel, and Save.
15. Verify invalid or overlong names show inline feedback and cannot be saved.
16. Capture `M2_2-tags-mixed.png` showing the multi-selection editor with one mixed tag and one pending new tag.

## Completion gate

Milestone 2.2 is complete only when:

- tests pass;
- build passes;
- `git diff --check` passes;
- all 16 manual checks pass;
- no duplicate tag rows are created;
- Cancel leaves the database unchanged;
- multi-selection save is all-or-nothing;
- no R11 popup regression is observed.
