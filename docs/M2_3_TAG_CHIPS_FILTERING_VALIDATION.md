# XClip Milestone 2.3 — Tag chips and filtering validation

## Scope

Milestone 2.3 adds read-only tag metadata to clip rows and integrates tags into
the existing popup filtering pipeline.

## Automated gate

Run from the repository root:

```powershell
.\gradlew.bat clean test --no-daemon
.\gradlew.bat build --no-daemon
git diff --check
```

Expected coverage includes:

- unified clip query by selected tag id;
- search matches assigned tag names;
- scope and tag filters compose correctly;
- deterministic batch assignment loading;
- immutable tag metadata in popup rows;
- three-chip rendering budget and `+N` overflow;
- UI contract revision 3.

## Manual validation

1. Create at least five tags: `Alpha`, `Beta`, `Gamma`, `Delta`, `Epsilon`.
2. Assign all five tags to one clip.
3. Confirm the row shows `Alpha`, `Beta`, `Delta` or the deterministic DAO order
   for the first three visible chips plus one `+2` overflow chip.
4. Hover `+2` and confirm the remaining tag names are listed without exposing
   the full clipboard content.
5. Assign one tag to multiple pinned and recent clips.
6. Select that tag in `Tag: All tags`; only assigned clips must remain.
7. Combine the tag filter with `Pinned`, then `Recent`.
8. Combine the tag filter with each content-type filter.
9. Search by a tag name while `All tags` is selected; assigned clips must match.
10. Search by content while a tag filter is active; both restrictions must apply.
11. Search by a different tag name while a tag filter is active; results must
    remain restricted to the selected tag.
12. Press Reset; scope, type, and tag must all return to defaults.
13. Verify an unknown search displays the search-specific empty state.
14. Verify a valid tag with no matching scope/type displays the filter empty state.
15. Create a new tag through Tags…, save, and confirm it appears in the filter
    after the popup reload.
16. Restart XClip and confirm chip order and tag filtering remain stable.
17. Validate compact, balanced, and wide layouts at 100%, 125%, and 150% scaling.
18. Confirm Direct Paste, Copy, row Actions, and multi-selection still work.

## Screenshot evidence

Capture:

```text
M2_3-tag-chips-filter.png
```

The screenshot must show:

- one row with three tag chips and `+N`;
- `Tag: <selected tag>` in the filter toolbar;
- a result set restricted by that tag.

## Git gate

After all automated and manual checks pass:

```powershell
git add .; git commit -m "Add tag chips and filtering"; git push
```
