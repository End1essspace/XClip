# Changelog

All notable changes to this project will be documented in this file.


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

