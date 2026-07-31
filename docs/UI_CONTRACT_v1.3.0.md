
# XClip UI Contract v1.3.0

**Status:** Frozen R11 baseline, deliberately extended by Milestones 2.2–2.4 and 3.2
**Scope:** Popup, custom window chrome, modal surfaces, Settings styling, keyboard workflow, responsive behavior, and packaged UI resources.

This document is the human-readable counterpart of `/ui/ui-contract-v1.3.0.properties`. Any intentional contract change must update both files and the `UiContractFreezeTest` expectations in the same reviewed milestone.

## 1. Product invariants

- XClip remains a local-first Windows clipboard manager.
- Clipboard commands and copied executable or script paths are never executed automatically.
- Direct Paste restores the captured external window and sends one standard `Ctrl+V`.
- Pinned clips survive normal Clear operations.
- Full clipboard content is never exposed through an automatic row-hover tooltip.
- Large entries use bounded previews.
- Database work and row preparation do not block the JavaFX Application Thread.
- Programmatic JavaFX remains the UI architecture; FXML is not introduced.

## 2. Window contract

- Minimum logical size: `500 × 300`.
- The popup is resizable and supports minimize, maximize/restore, close-to-background, title-bar dragging, and manual edge resizing.
- Restored geometry remains recoverable after monitor removal or DPI/topology changes.
- Negative coordinates are valid for monitors left of or above the primary display.
- Maximized persistence stores the last normal bounds separately.

## 3. Responsive contract

| Mode | Logical width | Required behavior |
|---|---:|---|
| Compact | `≤ 759` | Header, filters, and footer stack; row time may hide; primary actions remain reachable. |
| Balanced | `760–1119` | Single-row shell with reduced row metadata. |
| Wide | `≥ 1120` | Full row metadata and wide shell. |

Row time appears at `700` logical pixels or wider. Layout changes must not cause overlap, unreachable controls, clipped focus rings, or off-screen overlays.

## 4. Popup information architecture

1. Custom title bar.
2. Product/status header.
3. Search and scope/type/tag filters.
4. Virtualized clip list with `PINNED` and `RECENT` sections.
5. Selection/status and action footer.
6. Contextual menus, Quick Help, and modal dialogs.

The canonical scopes are `ALL`, `PINNED`, and `RECENT`. The canonical derived content types are `TEXT`, `CODE`, `URL`, `PATH`, `JSON`, and `COMMAND`.

## 5. Preview and performance budgets

- Expanded preview: at most `18` lines and `8,000` characters.
- Preview cache: at most `4,096` entries.
- Content-type cache: at most `8,192` entries.
- Type-filter candidate scan: at most `5,000` entries.
- Search debounce: `150 ms`.
- Stale asynchronous reload results are discarded.
- Content-type cache keys do not retain full clipboard strings.

## 6. Keyboard contract

The canonical bindings are frozen in `PopupKeyBindings` and serialized into the contract resource. Core workflow:

- `Ctrl+Shift+V` — open XClip and capture target.
- `Ctrl+F` / `Ctrl+K` — focus Search.
- `Ctrl+L` — clear Search.
- `Enter` — Direct Paste.
- `Ctrl+C` — Copy-only outside text input.
- `Ctrl+P` — Pin/Unpin.
- `F2` — rename pinned clip.
- `Alt+Up` / `Alt+Down` — move pinned clip.
- `Delete` — delete selection outside text input.
- `Shift+F10` / Menu key — open Actions.
- `F6` / `Shift+F6` — move between focus zones.
- `Escape` — close the most local active surface before hiding the popup.

Native text editing shortcuts must not be hijacked while Search owns focus.

## 7. Visual and accessibility contract

- Dark theme cascade is deterministic.
- Popup stylesheets: `theme.css`, `controls.css`, `popup.css`.
- Settings/dialog stylesheets: `theme.css`, `controls.css`, `dialogs.css`.
- Lucide SVG resources remain vector-only, packaged, and licensed.
- Focus rings remain visible at 100%, 125%, and 150% Windows scaling.
- Interactive controls expose accessible text/help.
- Section headings are skipped by clip navigation.
- Destructive dialogs keep Cancel as the default action.

## 8. Change-control rule

After R11, visual or interaction changes require all of the following:

1. explicit product reason;
2. updated machine-readable contract when applicable;
3. updated contract test;
4. updated regression matrix if behavior changes;
5. new screenshots;
6. automated and manual R11 gates;
7. separate commit and push.

Tags UI may extend the popup after R11, but it must preserve this frozen baseline unless the contract is deliberately versioned.




## 9. Tags UI extension — contract revision 2

Milestone 2.2 deliberately extends the frozen popup contract without redesigning
the R11 shell.

- Tag names use one shared normalization policy with a maximum of `64` characters.
- Tag editing is available from the footer Actions menu and each row context menu.
- The same editor supports one clip and the current multi-selection.
- Multi-selection exposes `UNASSIGNED`, `ASSIGNED`, and `MIXED` states.
- `MIXED` means existing per-clip differences remain unchanged.
- New tags are not persisted until Save.
- Creation plus all assignment/removal changes commit in one SQLite transaction.
- A failed save rolls back the complete edit.
- Duplicate names are resolved case-insensitively and select the existing tag.
- The R11 responsive shell, keyboard workflow, preview budgets, and safety
  invariants remain unchanged.



## 10. Tags display and filtering extension — contract revision 3

Milestone 2.3 adds tag metadata to the frozen popup without changing the
clipboard-content safety model.

- A clip row renders at most `3` visible tag chips.
- Remaining assigned tags are represented by one deterministic `+N` overflow chip.
- Tags are loaded in one bounded batch after the visible clip list is prepared;
  virtualized cells never query SQLite directly.
- Tag chips follow deterministic case-insensitive DAO ordering.
- The filter toolbar includes `Tag: All tags` plus every persisted tag.
- Selecting a tag restricts results by stable tag id, not by display text.
- Popup search matches clip content, pinned titles, and assigned tag names.
- Scope, content type, text search, and tag filter are combined in one
  deterministic reload snapshot.
- Reset clears scope, content type, and tag filters together.
- Empty states distinguish text-search misses from filter-only misses.
- The R11 preview bounds, Direct Paste workflow, asynchronous stale-result gate,
  and no-content-hover-tooltip invariant remain unchanged.

## 11. Tag management extension — contract revision 4

Milestone 2.4 completes the visible Tags workflow without changing schema v5.

- `Manage tags…` is a global Actions-menu entry and remains reachable when the history list is empty.
- The dialog lists every persisted tag in deterministic case-insensitive order.
- Each row exposes the current clip-assignment count; unused means exactly `0`.
- Rename uses the shared `TagNamePolicy` and rejects case-insensitive collisions.
- Single-tag delete requires explicit confirmation and states how many clip assignments will be removed.
- Deleting a tag relies on the existing foreign-key cascade for `clip_tags`; clipboard entries and clipboard content remain unchanged.
- Cleanup requires confirmation and deletes only tags that still have zero assignments when SQLite executes the statement.
- Management reads and mutations run through the popup's serialized database executor; the JavaFX Application Thread never performs JDBC work.
- Closing the dialog after a mutation refreshes tag chips, tag search, and the active tag-filter option set.
- The R11 shell, Direct Paste workflow, preview budgets, and schema version remain unchanged.

## 12. Advanced search execution extension — contract revision 5

Milestone 3.2 connects the parser foundation to the existing asynchronous popup
reload pipeline without redesigning the search field.

- Supported executable operators are `type:`, `is:`, `tag:`, `-type:`, and `-tag:`.
- Toolbar scope, type, and selected-tag filters are ANDed with search operators.
- Multiple positive `type:` terms use OR semantics because one clip has one derived content type.
- Multiple positive `tag:` terms use AND semantics; every required exact tag identity must be assigned.
- Negative type and tag terms exclude matching clips.
- Contradictory scope, type, or tag constraints resolve deterministically to an empty result.
- The pure-text remainder searches content, pinned titles, and assigned tag names.
- Invalid recognized operators and unterminated quotes retain the M3.1 text fallback contract.
- Text/title/tag and exact tag-operator constraints execute in SQLite.
- Derived content-type constraints use a bounded scan of at most `5,000` candidates unless the configured UI limit is higher.
- The final visible limit is applied without changing deterministic DAO ordering.
- Search highlighting receives only the pure-text remainder, never valid operator syntax.
- Every asynchronous stage is protected by the monotonic reload generation gate.
- Database schema v5, Direct Paste, Tags workflows, preview budgets, and the R11 visual shell remain unchanged.

