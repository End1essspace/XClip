# XClip UI Contract v1.3.0

**Status:** Frozen R11 baseline, deliberately extended by Milestones 2.2–2.4, 3.2–3.3, 4.3, 5.1–5.3, M6.1, and M6.3
**Scope:** Popup, custom window chrome, modal surfaces, Settings styling, privacy controls, keyboard workflow, responsive behavior, and packaged UI resources.

This document is the human-readable counterpart of `/ui/ui-contract-v1.3.0.properties`. Any intentional contract change must update both files and the `UiContractFreezeTest` expectations in the same reviewed milestone.

**Contract revision:** 13
**Registered Lucide UI icons:** 30

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
- The registry contains only icons referenced by current runtime UI code; unused `check-check` and horizontal `ellipsis` assets were removed during C1.1 cleanup.
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

## 13. Advanced Search UI extension — contract revision 6

Milestone 3.3 adds a visible assistance layer to the executable M3.2 search
pipeline without changing query semantics or schema v5.

- The assistance surface is rendered inline beneath Search and never opens a blocking modal.
- A focused empty Search field exposes a compact syntax hint for `type:`, `is:`, `tag:`, `-type:`, and `-tag:`.
- Contextual suggestions replace only the token containing the caret and preserve the rest of the raw query.
- `type:` and `-type:` suggestions enumerate the canonical derived content types.
- `is:` suggestions expose `is:pinned` and `is:recent`.
- `tag:` and `-tag:` suggestions are derived from the persisted tag catalog and quote names containing spaces.
- At most `6` active operator chips are visible; remaining operators use one deterministic `+N` overflow chip.
- Parser diagnostics are displayed inline and remain non-fatal; the existing ordinary-text fallback still executes.
- Search highlighting receives only the parsed pure-text remainder.
- Suggestions are keyboard reachable with Down from Search and support cyclic arrow-key navigation.
- The Search syntax section in Quick Help documents the executable operator contract.
- Saved queries remain an optional deferred roadmap item and are not part of revision 6.
- DAO ordering, toolbar combination rules, bounded scans, stale-result protection, Direct Paste, and all Tags behavior remain unchanged.


## 14. Duplicate Settings extension — contract revision 7

Milestone 4.3 exposes the persisted M4.2 duplicate policy through a dedicated
Settings section without changing config version 2 or database schema version 6.

- RECENT duplicates expose `MOVE_TO_TOP` and `PRESERVE_EXISTING_POSITION`.
- PINNED duplicates expose `PRESERVE_PIN_POSITION` and `MOVE_PIN_TO_TOP`.
- Whitespace matching exposes `NORMALIZE` and `PRESERVE`.
- Letter-case matching exposes `SENSITIVE` and `INSENSITIVE`.
- Duplicate age uses stable presets plus an exact custom millisecond value so
  an externally configured non-preset duration round-trips without data loss.
- `UNLIMITED` remains the safe default and maps to `0` milliseconds.
- Exact-content mode visibly disables Whitespace and Letter case because those
  settings are overridden by the domain policy while exact matching is active.
- Reset affects only duplicate controls and restores
  `DuplicateBehaviorPolicy.defaults()`; unrelated capture, startup, and window
  settings remain unchanged.
- Apply persists the complete config snapshot and immediately updates
  `ClipService`, watcher state, and popup configuration through the existing
  runtime callback.
- Closing Settings discards unapplied duplicate edits together with other
  unsaved Settings changes.
- Settings content scrolls independently while Apply and Close remain reachable
  in a fixed bottom action bar.
- Every duplicate control exposes accessible text/help and product-facing labels
  rather than internal enum names.

---

## 15. Excluded applications privacy extension — contract revision 8

Milestone 5.1 adds process-based capture exclusions without changing database
schema version 6. Config advances from version 2 to version 3.

- Settings exposes a dedicated `Privacy — excluded applications` section.
- Each entry is normalized to a lower-case executable basename; full paths and
  names without `.exe` are accepted as input.
- Matching is case-insensitive and intentionally does not use window-title text.
- At most `128` executable names are persisted; one normalized basename is at
  most `260` characters.
- The default list is empty, preserving pre-M5.1 capture behavior.
- The foreground resolver records PID, executable basename, and window title at
  clipboard polling detection time.
- Only a positive executable-name match blocks ingest. Missing process metadata,
  unsupported operating systems, permission failures, and resolver exceptions are
  fail-open.
- A blocked clipboard value is still stored as the watcher `lastSeen` snapshot,
  so switching to a non-excluded window without changing the clipboard cannot
  retroactively ingest sensitive content.
- Apply updates the runtime privacy gate immediately through the existing Settings
  callback; restarting XClip is not required.
- Invalid manually edited persisted entries are discarded individually while valid
  entries and all unrelated config values are preserved.
- No existing history is deleted automatically. The exclusion applies only to new
  clipboard changes detected while the listed process is foreground.


---

## 16. Sensitive content rules extension — contract revision 9

Milestone 5.2 adds explicit opt-in suppression for selected sensitive text.
Config advances from version 3 to version 4; database schema remains version 6.

- Settings exposes a dedicated `Privacy — sensitive content` section.
- Every rule uses the stable actions `CAPTURE` and `SKIP`.
- `CAPTURE` is the default for every rule, preserving pre-M5.2 behavior and
  preventing heuristic false positives from silently losing clipboard data.
- Payment-card detection accepts only bounded 13–19 digit candidates, permits
  spaces or hyphens, restricts the leading digit to common payment-card ranges,
  rejects repeated-identical digits, and requires a valid Luhn checksum.
- One-time-code detection accepts only 4–8 digit values near explicit OTP,
  verification, authentication, login, or equivalent Russian/Uzbek wording.
- A standalone numeric value is never classified as OTP by this revision.
- Sensitive inspection happens after the watcher marks the exact capped value as
  observed. A skipped value therefore cannot be ingested later merely because a
  rule is disabled or the foreground application changes while the clipboard is
  unchanged.
- Detection is local-only and does not log, transmit, rewrite, or persist the
  rejected content.
- Detector failures are fail-open independently from foreground-process
  resolution failures.
- Apply updates the runtime gate immediately without restarting XClip.
- Reset affects only sensitive-content controls and restores normal capture.
- Existing history is never scanned or automatically deleted.
- Password-manager applications remain governed by the explicit M5.1 executable
  exclusion list. Retention and cleanup belong to Milestone 5.3.

---

## 17. History retention and cleanup extension — contract revision 10

Milestone 5.3 adds explicit, age-based cleanup for unpinned history. Config
advances from version 4 to version 5; database schema remains version 6.

- Settings exposes a dedicated `History retention & cleanup` section.
- The general rule deletes only `RECENT` clips older than `N` whole days.
- The supported age range is `1` to `3,650` days.
- Independent age overrides exist for `TEXT`, `CODE`, `URL`, `PATH`, `JSON`,
  and `COMMAND`; `0` disables one type-specific override.
- When the general rule and a type override both apply, the shorter age wins.
- `PINNED` clips are never retention candidates and remain preserved regardless
  of age or content type.
- Age cleanup runs at startup, after Settings Apply, on explicit manual request,
  and every six hours while XClip remains running.
- Clear on exit is a separate explicit option and deletes all `RECENT` clips
  synchronously during normal shutdown while preserving `PINNED` clips.
- The Settings section displays the runtime last cleanup result, trigger,
  timestamp, and deleted-row count.
- Content type remains deterministic derived metadata and is not added to the
  database schema. SQLite first returns bounded unpinned age candidates; the
  domain policy performs the exact type decision locally.
- Multi-row deletion uses batches of at most `500` ids so cleanup remains below
  SQLite parameter limits.
- Automatic age cleanup and clear on exit are both disabled by default.
- Cleanup does not rewrite clipboard content, mutate PINNED order, or scan
  sensitive rules. Tag relations for deleted RECENT clips follow the existing
  foreign-key cascade.

---

## 18. Multi-page Settings shell extension — contract revision 12

Milestone M6.1 replaces the single continuous Settings document with a stable
multi-page shell while preserving all existing configuration and runtime
semantics.

Canonical page order:

```text
GENERAL
CAPTURE
HISTORY
DUPLICATE_BEHAVIOR
PRIVACY
APPEARANCE
SHORTCUTS
DATA
ABOUT
```

- Settings uses a persistent left sidebar with exactly one selected page.
- `GENERAL` is the default page for a newly created Settings window.
- The selected page is preserved when Settings is hidden and shown again during
  the same application session.
- Every page owns an independent vertical scroll surface; the navigation and
  bottom action bar remain fixed.
- Existing controls are moved, not duplicated. One JavaFX control instance maps
  to one configuration field and one page.
- `Apply` keeps the existing immediate runtime update behavior.
- `Cancel`, the custom close button, and the native close request all discard
  unapplied edits through the same close path.
- Settings uses `StageStyle.UNDECORATED` and the shared
  `WindowChromeController` for minimize, maximize/restore, title dragging, and
  manual edge resizing.
- The top-right close button occupies the complete corner target.
- Arrow keys, Home, and End navigate the sidebar; selected page buttons expose
  accessible names and visible focus.
- `APPEARANCE`, `SHORTCUTS`, and `ABOUT` are informational in M6.1. No
  speculative preference is persisted before runtime support exists.
- Config schema remains `5`, SQLite schema remains `6`, and product version
  remains `1.3.0`.
- Popup layout, Direct Paste, tags, advanced search, duplicate behavior,
  privacy, and retention semantics remain unchanged.

---

## 19. Settings draft lifecycle and validation — contract revision 13

Milestone M6.3 adds a single testable editing lifecycle to the multi-page
Settings shell without changing Config schema 5, SQLite schema 6, or runtime
feature semantics.

- One `SettingsDraftSession` owns the saved baseline, current raw draft, and
  current validation result.
- Dirty state is derived from `current != baseline`; returning every field to
  its saved value immediately restores a clean state.
- `Apply` is enabled only when the draft differs from baseline and every field
  validates successfully.
- Text-backed numeric fields remain representable while empty or out of range;
  invalid raw values never cross into `ConfigService` or runtime callbacks.
- Validation issues use stable field identities mapped to one canonical
  Settings page and one JavaFX control.
- Invalid controls receive the existing `input-error` treatment, affected
  sidebar pages receive a validation state, and the first issue is exposed in
  the fixed bottom bar.
- A defensive Apply attempt selects the first invalid page and focuses the
  exact failing control.
- Cancel, custom close, native close request, and hide-discard all restore the
  complete baseline draft through one lifecycle path.
- Duplicate, sensitive-content, and retention reset actions remain scoped to
  their own sections. Retention reset does not change maximum history; sensitive
  reset does not clear executable exclusions.
- A successful Apply materializes one validated Config snapshot, persists it,
  updates runtime services, then commits a new canonical draft baseline.
- Config schema remains `5`, SQLite schema remains `6`, and product version
  remains `1.3.0`.


## 20. Data, Shortcuts, and About completion — contract revision 14

Milestone M6.4 completes the three informational and ownership-oriented Settings
pages without changing Config schema 5 or SQLite schema 6.

### Shortcuts

- The global shortcut is fixed as `Ctrl+Shift+V` for v1.3.0.
- Settings displays the live Windows registration state: not started,
  registering, active, conflict, unavailable, failed, or stopped.
- Popup shortcut rows are rendered from the same `QuickHelpContent` contract used
  by popup Quick Help; Settings does not maintain a divergent shortcut list.
- Shortcut rebinding remains explicitly unavailable rather than exposing a
  speculative control.

### Data

- Settings exposes the data-directory, SQLite database, and config paths.
- Every path is selectable and can be copied; the owned data directory can be
  opened explicitly in Explorer.
- Saved retention cleanup and Clear RECENT are separate operations.
- Clear RECENT preserves PINNED clips, tags, configuration, and retention rules.
- Clear ALL remains confirmed and removes all XClip-owned local data before exit.
- Destructive data work runs outside the JavaFX Application Thread.
- Backup and restore are identified as deferred M7.2 capabilities and are not
  represented by unsafe placeholder actions.

### About

- About exposes version, author, GPL license, UI contract revision, bundled
  third-party notices, project links, and the local-data/privacy statement.
- External links open only after an explicit user action.
