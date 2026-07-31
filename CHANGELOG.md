# Changelog

All notable changes to this project will be documented in this file.


## [Unreleased] — Tags display, filtering, and frozen UI extension

### Added

* Added compact tag chips under clip previews with a strict three-chip budget and `+N` overflow.
* Added a `Tag: All tags` popup filter with deterministic tag ordering.
* Added tag-name matching to the existing popup search without changing content/title search behavior.
* Added batch assignment loading so virtualized rows never issue one database query per cell.
* Added a single-clip and multi-selection tag editor available from Actions and row context menus.
* Added inline tag creation, validation, case-insensitive duplicate resolution, and assignment removal.
* Added tri-state multi-selection semantics: assign to all, remove from all, or preserve mixed assignments.
* Added one-transaction tag creation and batch assignment through `TagDao.applyEdit`.
* Extended the machine-readable UI contract for the Milestone 2.2 Tags surface.
* Extended the frozen UI contract to revision 3 for Milestone 2.3 tag chips and filtering.
* Added deterministic tests for tag-name normalization, editor planning, atomic saves, and rollback.

### Changed

* Popup reload now combines scope, content type, text search, tag-name search, and selected-tag filtering in one deterministic pipeline.
* Popup rows now carry immutable tag metadata prepared off the JavaFX Application Thread.
* The popup now receives the existing schema-v5 `TagDao` and exposes visible Tags UI without changing clipboard content.
* The R11 shell remains frozen; Milestone 2.2 is an explicit contract revision rather than a popup redesign.

### R11 baseline

* Added a versioned machine-readable UI contract for the v1.3.0 popup baseline.
* Added a 38-case R11 regression matrix and a canonical screenshot evidence set.
* Added PowerShell workflows for automated validation, manual evidence collection, and final evidence verification.
* Added a Gradle `r11AutomatedGate` and packaged UI contract verification.

### Internal

* Frozen responsive breakpoints, preview/performance budgets, keyboard bindings, content types, status tones, dialog tones, icon count, and stylesheet cascade through automated tests.
* Excluded local `artifacts/` regression evidence from version control.


## [1.3.0] — Dark UI & Tray Polish Update — 2026-06-05

### Added

* Added full dark production UI theme for Popup and Settings windows.
* Added native dark Windows title bar support through DWM integration.
* Added dark styling for confirmation, information, and error dialogs.
* Added custom dark tray context menu instead of the default native white AWT menu.
* Added tray menu outside-click handling for more natural context menu behavior.

### Improvements

* Improved popup visual hierarchy with clearer `PINNED` and `RECENT` section headers.
* Improved pinned clip row styling with subtle amber accent indication.
* Improved selected row styling to avoid heavy yellow/brown selection blocks.
* Improved popup footer spacing and action button sizing.
* Improved Settings window visual consistency with the rest of the application.
* Improved tray menu hover, separator, and accent styling.

### Fixed

* Fixed white Windows title bars breaking the dark UI appearance.
* Fixed Settings confirmation dialogs remaining light-themed.
* Fixed tray menu staying open after clicking outside.
* Fixed tray menu item interaction issues caused by unstable `JPopupMenu` anchoring.
* Fixed muted section headers caused by disabled section cells.

### Internal

* Added `WindowsTitleBar` helper for Windows DWM title bar styling.
* Reworked tray menu implementation to use a custom Swing `JWindow` menu.
* Added native mouse-state watcher for reliable outside-click tray menu closing.
* Kept database schema unchanged.
* Kept config schema version unchanged.


## [1.2.0] — Clipboard Safety & UI Limit Update — 2026-03-15

### Behavior Changes

* Redesigned **Clear** button behavior to prevent accidental history loss.
* Clear now removes **only clips currently visible in the popup** instead of wiping the entire database.
* Pinned clips remain protected during Clear operations.

### Added

* New configurable **UI clip limit** setting (default: `200`).
* Allows adjusting how many recent clips are loaded into the popup interface.
* Setting available directly in **Settings → UI clip limit**.

### Improvements

* Popup clip loading limit is now driven by configuration instead of hardcoded value.
* Runtime update of popup limit without restarting the application.

### Internal

* Extended `Config` system with `uiClipLimit` parameter.
* Updated Settings window with new spinner control.
* Refactored Clear operation to delete only visible clip IDs.




## [1.1.0] — Window System & Licensing Update — 2026-02-24

### Window & UI Improvements

* Switched from `StageStyle.UTILITY` to `StageStyle.DECORATED`
* Restored native Windows title bar buttons:

  * Minimize
  * Maximize / Restore
  * Close
* Enabled proper window resizing for popup window
* Added application icon to window title bar
* Fixed tray reopening issue after native minimize (de-iconify fix)
* Improved window state restoration behavior

### Stability Improvements

* Fixed bug where popup could not reopen after native minimize
* Improved stage focus behavior when restored from tray
* Ensured consistent window state handling (iconified/maximized)

### Licensing

* License changed from **MIT** to **GNU General Public License v3.0**
* Added proper GPL v3 license file
* Updated README license section

### Internal

* Refined window initialization logic
* Improved stage lifecycle handling
* Cleaned up title bar behavior for production-grade experience




## [1.0.1] — 2026-02-18

### Fixed

* Fixed UI freeze when displaying very large clipboard entries (e.g. 5000+ lines).
* Replaced hard drop (`50_000` chars) with configurable truncation logic.
* Fixed `withStartOnBoot()` constructor bug in `Config` (incorrect argument mapping).
* Fixed `Apply` behavior for numeric settings (digits-only input enforced).
* Fixed inconsistent spinner validation state after reopening Settings.


### Added

* **Configurable `maxClipChars` setting** (default: 500,000).
* Bounded UI preview for large clips (prevents JavaFX layout explosion).
* Expand / Collapse hotkey:

  * `E` → Toggle expanded preview (UI-only, bounded).
* Extended preview limits for expanded mode (safe rendering).
* Updated Quick Help tooltip with new hotkey.




## [1.0.0] - 2026-02-18

### Added
- Clipboard monitoring service
- System tray integration
- Global hotkey support
- Search functionality
- Pin / unpin clips
- SQLite persistence (WAL mode)
- Single-instance protection
- Windows autostart support
- MSI packaging with upgrade UUID

### Fixed
- Clipboard polling backoff improvements
- Connection reuse for SQLite
- Preview rendering optimization



