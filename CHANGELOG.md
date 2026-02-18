# Changelog

All notable changes to this project will be documented in this file.

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

