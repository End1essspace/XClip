# XClip — User Guide


Status: documentation baseline for manual validation
Platform: Windows 10/11 x64
Development target: v1.4.0
Config schema: 5
SQLite schema: 6
UI contract: 18

## 1. What XClip does

XClip records text copied to the Windows clipboard and stores it locally. It provides a searchable popup, PINNED entries, tags, Direct Paste, content-aware safe actions, privacy exclusions, retention rules, and database recovery tools.

XClip does not execute clipboard commands. All documented user data remains under the current Windows profile unless the user explicitly exports a backup or copies data elsewhere.

## 2. Opening XClip

The main popup can be opened from:

- the configured global hotkey, currently `Ctrl+Shift+V`;
- a left-click on the XClip tray icon;
- a secondary XClip launch, which activates the primary instance.

A right-click on the tray icon opens the tray menu.

When Windows reports a global-hotkey conflict, XClip keeps running and exposes the conflict in Settings → Shortcuts.

## 3. Clipboard capture

XClip captures supported text while the watcher is enabled.

Capture is affected by:

- minimum clip length;
- maximum clip character count;
- duplicate behavior;
- foreground application exclusions;
- optional sensitive-content rules;
- watcher enabled/paused state.

Application-originated clipboard writes are suppressed from immediate re-capture.

### Duplicate behavior

Duplicate settings control:

- whether matching RECENT clips move to the top;
- whether matching PINNED clips preserve or change manual order;
- whitespace normalization or preservation;
- case-sensitive or case-insensitive matching;
- finite or unlimited duplicate windows;
- exact-content mode.

Changing duplicate policy does not require rewriting existing history because the database stores four policy-independent equality hashes.

## 4. Popup layout

The popup contains:

- title bar and global status;
- search field and search-assist surface;
- scope, type, and tag filters;
- virtualized clipboard rows;
- action controls;
- status/footer information.

The popup loads only a bounded number of clips according to `uiClipLimit`. Large contents use bounded previews rather than rendering the complete value in every row.

## 5. PINNED and RECENT

RECENT contains ordinary history. PINNED contains entries explicitly marked for preservation.

PINNED behavior:

- PINNED entries are not removed by normal RECENT clearing;
- PINNED entries are excluded from age/type retention;
- titles may be assigned to PINNED entries;
- manual PINNED order is persisted;
- tag assignments are preserved with the clip.

## 6. Selecting and acting on clips

Use mouse or keyboard navigation to select clips. Multi-selection supports range and toggle behavior.

Available actions depend on selection and content:

- Copy;
- Direct Paste;
- Pin or unpin;
- delete;
- assign or remove tags;
- edit PINNED title;
- move PINNED entries;
- type-specific safe action.

### Direct Paste

Direct Paste copies the selected content, restores the previously active target window, and sends standard `Ctrl+V`.

Direct Paste target state is deliberately cleared after session/lifecycle boundaries such as lock, unlock, resume, Explorer recovery, or display topology changes. When no safe target is available, use Copy and paste manually.

## 7. Search

Plain text searches clip content, PINNED titles, and assigned tag names.

Operators:

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
-tag:private
-type:text
```

Rules:

- toolbar filters and operators are combined;
- repeated positive type operators use OR semantics;
- repeated required tags use AND semantics;
- negative operators exclude matches;
- contradictory constraints produce an empty result rather than unsafe fallback execution;
- invalid query fragments show non-blocking diagnostics.

## 8. Content types and safe actions

XClip derives one of these types without storing it as mutable metadata:

- TEXT;
- CODE;
- URL;
- PATH;
- JSON;
- COMMAND.

Safe primary actions:

- URL → open through the system browser;
- PATH → reveal through Explorer;
- JSON → copy formatted JSON;
- CODE → copy code;
- COMMAND → copy command.

COMMAND values are never executed by XClip.

## 9. Tags

Tags are local database records with case-insensitive identity.

You can:

- create a tag;
- assign tags to one clip;
- assign or remove tags across a multi-selection;
- filter by tag;
- search tag names;
- rename tags;
- delete tags;
- view usage counts;
- remove unused tags.

Tag changes are transactional. A failed save does not intentionally leave a partial selection update.

## 10. Settings

Settings pages:

```text
General
Capture
History
Duplicate behavior
Privacy
Appearance
Shortcuts
Data
About
```

### Draft and Apply

Settings edits are held in a draft.

- Apply persists and applies a valid changed draft.
- Cancel discards unapplied changes.
- Closing Settings follows the same discard path as Cancel.
- Invalid fields block Apply.
- The validation message can navigate to the first invalid field.
- Scoped reset affects only the documented settings group.

### Responsive and accessibility behavior

Settings supports compact, standard, and wide layouts. At small sizes, two-column setting grids stack vertically and action rows wrap.

Keyboard navigation, accessible names, visible focus, and validation activation by mouse, Enter, or Space are part of the documented contract.

## 11. Privacy

### Excluded applications

XClip can skip capture while a listed executable owns the foreground window.

Matching uses the executable basename without case sensitivity. Paths entered by the user are reduced to their executable filename.

If Windows cannot resolve foreground process metadata, the resolver fails open so XClip does not silently lose ordinary clipboard history.

### Sensitive-content rules

Both rules are disabled by default.

Optional rules can skip:

- payment-card-like values that satisfy bounded candidate rules and Luhn validation;
- 4–8 digit values near explicit OTP/verification context.

Rules inspect only newly copied text. Existing history is not scanned or deleted when a rule is enabled.

## 12. Retention and cleanup

Retention applies only to RECENT entries.

Available rules:

- general maximum age;
- independent TEXT, CODE, URL, PATH, JSON, and COMMAND age overrides;
- optional clear RECENT on exit;
- manual cleanup;
- startup, Apply, periodic, and exit triggers.

When multiple rules apply, the shorter enabled age wins. The boundary is strict: a clip exactly N days old is preserved until it becomes older than N days.

PINNED entries are never part of retention cleanup.

## 13. Data and database maintenance

Settings → Data shows:

- data directory;
- database path;
- configuration path;
- SQLite schema;
- journal mode;
- database/WAL/shared-memory sizes;
- page and free-page information.

Actions:

- Refresh status;
- Check integrity;
- Run retention cleanup;
- Checkpoint WAL;
- Optimize database;
- Clear RECENT history;
- Create backup;
- Restore backup;
- Clear ALL data.

Long-running or exclusive actions run outside the JavaFX Application Thread and temporarily block conflicting actions.

## 14. Backup and restore

### Create backup

Create backups outside `%USERPROFILE%\.xclip`.

The `.xclip-backup` archive contains:

```text
manifest.properties
xclip.db
config.json
```

The database snapshot is created through SQLite `VACUUM INTO`, then checked with `PRAGMA integrity_check`.

Only persisted configuration is included. Unapplied Settings draft values are not backed up.

### Restore backup

Before replacement, XClip validates:

- exact archive entries;
- duplicate or unsafe paths;
- archive size bounds;
- backup format version;
- database schema;
- configuration schema;
- manifest consistency;
- configuration parseability;
- SQLite integrity.

Restore uses staged files and rollback copies. A successful restore exits XClip. Restart the application to load the restored state.

## 15. Clear actions

### Clear RECENT

Removes non-PINNED clips while preserving:

- PINNED clips;
- PINNED titles and order;
- tag library and relevant assignments;
- configuration and policy settings.

### Clear ALL

Clear ALL is destructive and removes XClip-owned user data. Use backup first.

The operation releases database connections and deletes SQLite sidecars to avoid leaving partial local state.

## 16. Windows lifecycle behavior

XClip includes runtime recovery for:

- Explorer restart;
- sleep and resume;
- lock and unlock;
- monitor connect/disconnect;
- display bounds and DPI changes;
- secondary launch;
- stale autostart launcher path;
- normal shutdown and JVM shutdown.

Expected guarantees include one tray surface, one primary instance, a restarted watcher after resume, visible window recovery, and no stale Direct Paste target across lifecycle boundaries.

The release is not considered complete until the packaged 18-case matrix is manually validated with a real MSI.

## 17. Local files

Default directory:

```text
%USERPROFILE%\.xclip\
```

Files may include:

```text
xclip.db
xclip.db-wal
xclip.db-shm
xclip.db-journal
config.json
config.bad-<timestamp>.json
```

Do not manually edit or delete database files while XClip is running.

## 18. Troubleshooting

### Global hotkey does not work

1. Open Settings → Shortcuts.
2. Check the registration status.
3. Close the application currently using `Ctrl+Shift+V`.
4. Restart XClip or use the tray command to open the popup.

### Popup appears on the wrong display

Trigger the popup again after the display change. XClip should recover fully off-screen windows to visible bounds while preserving valid negative-coordinate monitor layouts.

### Integrity check fails

1. Stop destructive data actions.
2. Create a copy of `%USERPROFILE%\.xclip`.
3. Do not run repeated writes against the affected database.
4. Restore a previously validated XClip backup or preserve the files for diagnosis.

### Restore is rejected

The selected file may have unexpected entries, an unsupported schema, an invalid config, or failed SQLite integrity. XClip refuses replacement before changing live data.


## 19. Current popup presentation and edge ergonomics

The current v1.4.0 development tree includes a final popup readability/ergonomics pass:

- the title bar carries a subtle centered `X-SERIES` wordmark;
- the Actions menu uses slightly larger text and Lucide icons for easier scanning;
- Search Assist appears contextually as a floating overlay instead of permanently making the header taller;
- the vertical scrollbar keeps a slim visual appearance but has a wider interaction lane.

### Maximized top-right Close

On Windows, when XClip is maximized on a hard screen edge, you can throw the pointer into the exact physical top-right corner and use Close without aiming at the visible `×` glyph. The extra physical-edge behavior is disabled for restored, hidden, inactive, or minimized windows.

### Maximized right-edge scrollbar

When XClip is maximized and the vertical scrollbar is visible, the scrollbar thumb can be acquired from the physical right edge at the thumb's current vertical position. The feature does not extend into a neighboring monitor or a right-side system panel.

## 20. Current release status

The codebase documents application version 1.4.0, config schema 5, SQLite schema 6, UI contract 18, and backup format 1.

Before public release, the project still requires:

- full manual functional validation;
- M8 packaged 18-case evidence;
- clean MSI build and installed-app validation;
- upgrade/uninstall/reinstall proof;
- final screenshots;
- final checksums;
- tagged clean commit;
- final release notes and GitHub Release.
