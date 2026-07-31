# M2.4 — Tag management validation

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

All commands must complete successfully before manual validation.

## Manual validation

1. Open the footer **Actions** menu and activate **Manage tags…**.
2. Confirm that every existing tag is listed in case-insensitive name order.
3. Verify the usage count for an unassigned tag (`0 clips`), a tag assigned to one clip, and a tag assigned to multiple clips.
4. Rename an unused tag and verify the new name in row chips and the tag filter after closing the dialog.
5. Rename an assigned tag and verify that all existing assignments are preserved.
6. Attempt to rename a tag to another existing name using different case and spacing. The dialog must show a collision error and preserve both original tags.
7. Enter a blank name and a name longer than 64 characters. Inline validation must reject both without writing to SQLite.
8. Start a rename and press Cancel. The original name must remain unchanged.
9. Delete an unused tag. Confirmation must state that it is unused.
10. Delete an assigned tag. Confirmation must show the affected clip count; clipboard entries and content must remain intact.
11. Cancel a delete confirmation and verify that the tag and assignments remain unchanged.
12. Create at least two unused tags and one assigned tag, then run **Clean up unused**. Only zero-usage tags may be removed.
13. Cancel cleanup and verify that no tags are removed.
14. Delete the tag currently selected in the popup tag filter. After closing the dialog, the filter must recover to `Tag: All tags`.
15. Open **Manage tags…** with an empty clipboard history. The management surface must remain reachable.
16. Reopen XClip and verify rename/delete/cleanup persistence.
17. Check the dialog at Windows scaling 100%, 125%, and 150%; controls, focus rings, counts, and confirmations must remain visible.
18. Regression-check tag assignment, chips, tag filtering, tag-name search, Direct Paste, Copy, row Actions, and multi-selection.

## Screenshot evidence

Capture:

```text
M2_4-tag-management.png
```

The screenshot must show the management dialog with at least one used tag, one unused tag, visible usage counts, and the **Clean up unused** action.

## Exit gate

- tests green;
- build green;
- `git diff --check` green;
- all manual cases green;
- screenshot captured;
- no tag-assignment loss;
- no clipboard-content mutation;
- no popup regression.
