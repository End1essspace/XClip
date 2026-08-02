# Changelog

All notable changes to this project will be documented in this file.


## [Unreleased] — Privacy controls and duplicate behavior preferences

### Added

* Added process-based foreground application exclusions with a dedicated Privacy section in Settings.
* Added config v3 persistence for a normalized, case-insensitive executable-basename exclusion list with backward-compatible migration from config v2.
* Added a best-effort Windows foreground resolver that records process id, executable name, and window title without blocking capture on resolver failure.
* Added a fail-open clipboard privacy gate: only a positive foreground executable match suppresses ingest.
* Added strict UI validation, safe persisted-value sanitization, deterministic normalization, and one-click clearing for excluded applications.
* Extended the frozen UI contract to revision 8 and added privacy policy, resolver parsing, runtime gate, config migration, and CSS resource tests.
* Added a dedicated Duplicate behavior section to Settings with product-facing controls for RECENT/PINNED positioning, whitespace, letter case, duplicate windows, and exact-content mode.
* Added stable duplicate-window presets plus lossless custom millisecond input for non-preset persisted values.
* Added an exact-mode override state that visibly disables whitespace and case controls while their values remain preserved.
* Added Reset duplicate defaults, scoped only to duplicate preferences and backed by `DuplicateBehaviorPolicy.defaults()`.
* Added a scrollable Settings content surface with a fixed Apply/Close action bar so all controls remain reachable on smaller displays.
* Extended the frozen UI contract to revision 7 and added pure Settings mapping/validation tests.
* Added config v2 persistence for duplicate position, whitespace, case, time-window, and exact-content preferences with backward-compatible migration from v1.
* Added four policy-independent SHA-256 lookup keys per clip so duplicate settings can change without rewriting history.
* Added schema v6 migration that removes the legacy unique-hash restriction while preserving legacy rows, tags, titles, and pinned order.
* Connected duplicate policy decisions to runtime ingestion, including finite-window row creation and optional PINNED move-to-top behavior.
* Added config migration, alternate-key, database migration, and runtime policy integration tests.
* Added a pure-Java duplicate behavior policy covering recent positioning, pinned positioning, whitespace normalization, case sensitivity, duplicate time windows, and exact-content matching.
* Added a deterministic duplicate decision engine that returns persistence-neutral mutation intents without touching Config, SQLite, or JavaFX.
* Added defaults that formally preserve current XClip behavior: RECENT duplicates move to the top, PINNED duplicates keep manual order, whitespace is normalized, matching is case-sensitive, and the duplicate window is unlimited.
* Added domain contract tests for every duplicate-policy axis and boundary condition.
* Added a responsive inline advanced-search assistance surface beneath the popup search field.
* Added contextual operator completions for `type:`, `is:`, `tag:`, `-type:`, and `-tag:` with tag-name quoting.
* Added bounded active-operator chips with deterministic `+N` overflow.
* Added non-blocking inline parser diagnostics and a complete Search syntax section in Quick Help.
* Connected parsed `type:`, `is:`, `tag:`, `-type:`, and `-tag:` operators to the popup query pipeline.
* Added immutable search execution plans that combine advanced operators with toolbar scope, type, and tag filters.
* Added exact tag-identity `EXISTS`/`NOT EXISTS` constraints with deterministic AND/exclusion semantics.
* Added bounded derived-type execution that preserves DAO ordering and the existing 5,000-candidate safety budget.
* Added stale-generation checkpoints between count, query, derived filtering, tag loading, and JavaFX publication.
* Added a deterministic pure-Java advanced-search parser foundation for `type:`, `is:`, and `tag:` operators.
* Added quoted values, negative type/tag clauses, pure-text remainder extraction, and non-fatal invalid-query fallback diagnostics.
* Added parser, execution-plan, ordering, conflict, and advanced tag-query contract tests.
* Added a global `Manage tags…` dialog that remains available even when clipboard history is empty.
* Added deterministic tag usage counts based on current clip assignments.
* Added inline tag rename with shared validation and case-insensitive collision reporting.
* Added confirmed single-tag deletion with the affected assignment count shown before removal.
* Added confirmed cleanup for tags with zero assignments only.
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

* Duplicate behavior values remain persisted in config v3 and continue to apply immediately through `ClipService` and the dedicated Settings controls.
* Clipboard watcher now evaluates the foreground privacy gate after marking a changed value as observed, preventing excluded content from being captured later after a window switch without another clipboard change.
* Clipboard watching now forwards exact capped text to the domain layer so case- and whitespace-only changes can be evaluated by the selected duplicate policy.
* `content_hash` is no longer unique in schema v6 because finite duplicate windows can intentionally retain multiple equal clips.
* Extended the frozen UI contract to revision 6 for Milestone 3.3 Search UI.
* Search suggestions replace only the token at the caret and preserve the remainder of the query.
* Valid operator syntax remains excluded from clip-content highlighting; only the pure-text remainder is highlighted.
* Extended the frozen UI contract to revision 5 for Milestone 3.2 advanced-search execution.
* Search highlighting now uses only the parsed pure-text remainder instead of valid operator syntax.
* Removed the redundant UI-side resort so query results retain the exact deterministic DAO order.
* Extended the frozen UI contract to revision 4 for Milestone 2.4 tag management.
* Tag-management database work now runs through the popup's existing serialized database executor.
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
