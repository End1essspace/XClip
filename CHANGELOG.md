# Changelog

All notable changes to XClip are documented in this file.

For detailed release notes, see [`docs/RELEASE_NOTES_v1.4.0.md`](docs/RELEASE_NOTES_v1.4.0.md).

---

## [1.4.0] — 2026-08-17

A major update that turns XClip from a clipboard history popup into a more complete local-first clipboard workspace for Windows.

### Added

- Added custom titles for PINNED clips.
- Added manual PINNED ordering with Move Up, Move Down, Move to Top, and Move to Bottom.
- Added persistent local tags with create, assign, batch edit, filter, rename, delete, usage count, and unused-tag cleanup.
- Added advanced search operators for content type, scope, required tags, excluded tags, and negative type filters.
- Added quoted tag values, active operator chips, contextual suggestions, and non-blocking search diagnostics.
- Added content classification for `TEXT`, `CODE`, `URL`, `PATH`, `JSON`, and `COMMAND`.
- Added type-aware safe actions for opening HTTP(S) URLs, revealing paths, formatting JSON, and copying code or commands without executing clipboard commands.
- Added configurable duplicate behavior for RECENT and PINNED clips.
- Added whitespace, case-sensitivity, duplicate-window, and exact-content matching options.
- Added foreground application exclusions for clipboard capture.
- Added optional local-only suppression for payment-card-like values and contextual one-time codes.
- Added general and per-content-type age retention for RECENT history.
- Added startup, Apply-triggered, manual, periodic, and clear-on-exit cleanup.
- Added a nine-page Settings architecture:
  - General
  - Capture
  - History
  - Duplicate behavior
  - Privacy
  - Appearance
  - Shortcuts
  - Data
  - About
- Added Settings draft state with Apply, Cancel, scoped reset, and inline validation.
- Added database status and storage metrics.
- Added SQLite integrity checking, WAL checkpoint, `VACUUM`, and `PRAGMA optimize`.
- Added versioned `.xclip-backup` creation.
- Added strict backup validation and rollback-protected restore.
- Added large-data validation coverage for histories up to 50,000 clips and very large clipboard entries.
- Added a subtle centered `X-SERIES` title-bar wordmark.

### Changed

- Redesigned the popup into a responsive, keyboard-first workspace with `All`, `Pinned`, and `Recent` scopes.
- Improved Direct Paste target restoration while preserving Copy fallback behavior.
- Search now covers clipboard content, PINNED titles, and assigned tag names.
- Popup rows now use bounded previews and virtualization to keep large histories responsive.
- Search Assist now appears contextually as a floating surface instead of permanently increasing header height.
- Improved Actions-menu readability with larger scoped text, icons, and spacing.
- Improved selection, batch actions, filtering, empty states, and keyboard navigation.
- Improved Settings responsiveness across constrained, standard, and wide layouts.
- Automatic retention remains opt-in; existing history is not deleted unless the user enables a cleanup rule.
- PINNED clips remain protected from ordinary RECENT retention cleanup.
- Duplicate-policy changes no longer require rewriting existing clipboard history.
- The packaged MSI continues to include its own Java runtime.

### Privacy and safety

- Clipboard commands are never executed automatically.
- Executable or script paths copied to the clipboard are not launched as commands.
- Sensitive-content detection remains local-only and opt-in.
- Foreground application resolution fails open instead of silently dropping clipboard data.
- Existing history is not silently rescanned or deleted when privacy settings change.
- Full clipboard contents are not exposed through automatic hover tooltips.

### Data and reliability

- Added four indexed duplicate lookup hashes so matching policy can change without rewriting history.
- Updated the SQLite schema to version `6` and configuration schema to version `5`.
- Added transactional database migration with rollback and retry support.
- Added future-schema rejection before database mutation.
- Added bounded batch deletion for large retention cleanups.
- Added acknowledged single-instance activation and explicit unrelated-port conflict handling.
- Added tray and global-hotkey recovery after Explorer restart.
- Added clipboard watcher recovery after sleep/resume and lock/unlock.
- Added stale Direct Paste target invalidation across lifecycle boundaries.
- Added window recovery after monitor, work-area, topology, and DPI changes.
- Added stale autostart launcher repair.
- Hardened ordered and idempotent shutdown behavior.
- Improved the maximized top-right Close target for physical screen-edge acquisition.
- Widened the scrollbar interaction lane while keeping the visible scrollbar slim.
- Added guarded physical-right-edge scrollbar thumb dragging while maximized.

### Release

- Released as `v1.4.0` on GitHub on 2026-08-17.
- Gradle, JAR, jpackage, MSI, and the current machine-readable UI contract are aligned to version `1.4.0`.
- Clean automated tests and the full Gradle build passed before release.
- `XClip-1.4.0.msi` was built successfully and the installed packaged application passed smoke validation.
- The separate formal 18-case M8 packaged lifecycle evidence run was waived for this release; unexecuted cases are not represented as PASS.

---

## [1.3.0] — 2026-06-05

### Added

- Added a full dark production theme for the Popup and Settings windows.
- Added native dark Windows title-bar support through DWM integration.
- Added dark styling for confirmation, information, and error dialogs.
- Added a custom dark tray context menu.
- Added reliable outside-click dismissal for the tray menu.

### Changed

- Improved visual hierarchy between `PINNED` and `RECENT`.
- Added a subtle amber accent for PINNED rows.
- Refined selected-row styling.
- Improved popup footer spacing and action-button sizing.
- Improved Settings visual consistency.
- Improved tray-menu hover, separator, and accent styling.

### Fixed

- Fixed white Windows title bars breaking the dark appearance.
- Fixed light-themed Settings dialogs.
- Fixed the tray menu remaining open after clicking outside.
- Fixed tray-menu interaction instability.
- Fixed muted section headers caused by disabled section cells.
- Fixed popup reopening behavior after native minimize.

---

## [1.2.0] — 2026-03-15

### Added

- Added a configurable UI clip limit with a default of `200`.
- Added runtime application of the popup history limit without requiring restart.

### Changed

- Changed **Clear** to remove only clips currently visible in the popup instead of wiping the entire database.
- PINNED clips remain protected during Clear operations.
- Popup clip loading is now configuration-driven instead of hardcoded.

---

## [1.1.0] — 2026-02-24

### Changed

- Switched the popup from `StageStyle.UTILITY` to `StageStyle.DECORATED`.
- Restored native Windows Minimize, Maximize / Restore, and Close buttons.
- Enabled normal window resizing.
- Added the application icon to the native title bar.
- Improved window-state restoration and focus behavior.

### Fixed

- Fixed popup reopening after native minimize.
- Improved restoration from the system tray.
- Improved handling of iconified and maximized window states.

### Licensing

- Changed the project license from MIT to GNU General Public License v3.0.
- Added the GPL v3 license file and updated repository documentation.

---

## [1.0.1] — 2026-02-18

### Added

- Added configurable `maxClipChars` with a default of `500,000`.
- Added bounded previews for very large clipboard entries.
- Added `E` to expand or collapse the bounded clip preview.
- Updated Quick Help with the new preview shortcut.

### Fixed

- Fixed UI freezes when displaying very large clipboard entries.
- Replaced the previous hard `50,000`-character drop with configurable truncation.
- Fixed incorrect argument mapping in `Config.withStartOnBoot()`.
- Fixed numeric Settings Apply behavior.
- Fixed inconsistent spinner validation state after reopening Settings.

---

## [1.0.0] — 2026-02-18

Initial public release.

### Added

- Clipboard monitoring.
- Persistent SQLite history in WAL mode.
- Search.
- Pin and unpin.
- System tray integration.
- Global hotkey support.
- Single-instance protection.
- Windows autostart support.
- MSI packaging with a stable upgrade UUID.

### Improved

- Clipboard polling backoff.
- SQLite connection reuse.
- Preview rendering performance.

---

[1.4.0]: https://github.com/End1essspace/XClip/releases/tag/v1.4.0
[1.3.0]: https://github.com/End1essspace/XClip/releases/tag/v1.3.0
[1.2.0]: https://github.com/End1essspace/XClip/releases/tag/v1.2.0
[1.1.0]: https://github.com/End1essspace/XClip/releases/tag/v1.1.0
[1.0.1]: https://github.com/End1essspace/XClip/releases/tag/v1.0.1
[1.0.0]: https://github.com/End1essspace/XClip/releases/tag/v1.0.0
