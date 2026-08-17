
# XClip 1.4.0 — Draft Release Notes


**Status: DRAFT — NOT APPROVED FOR PUBLIC RELEASE**

These notes describe the current implementation baseline. They do not claim that the final MSI, upgrade path, uninstall/reinstall behavior, or all 18 Windows lifecycle cases have passed. Replace this status only after manual validation and final release gates are complete.

## Highlights


### Final popup readability and Windows edge ergonomics

- Subtle centered `X-SERIES` title-bar branding.
- Slightly larger scoped Actions-menu text and Lucide icons.
- Contextual floating Search Assist so chips/suggestions do not permanently expand the header.
- Wider window-control targets.
- Guarded maximized top-right physical-edge Close behavior for Fitts-law acquisition.
- Wider logical scrollbar hit lane while preserving a slim visual scrollbar.
- Guarded maximized physical-right-edge thumb dragging.

The edge fallbacks are active only for eligible visible/focused/maximized Windows windows and do not extend destructive targets outside a restored window or into a neighboring monitor.


### Redesigned clipboard workflow

- Responsive, keyboard-first popup.
- Direct Paste with Copy fallback.
- PINNED and RECENT scopes.
- Multi-selection and batch actions.
- Bounded previews for large clipboard values.
- Content types: TEXT, CODE, URL, PATH, JSON, and COMMAND.
- Safe content actions without command execution.

### Tags and advanced search

- Local tags with create, assign, batch edit, filter, rename, delete, usage count, and unused cleanup.
- Search operators for type, scope, required tags, excluded tags, and exclusions.
- Quoted tag names, active operator chips, suggestions, and non-blocking diagnostics.
- Search across content, PINNED titles, and assigned tag names.

### Duplicate preferences

- Configurable RECENT and PINNED duplicate positioning.
- Whitespace and case rules.
- Finite or unlimited duplicate window.
- Exact-content mode.
- Four indexed equality hashes allow policy changes without rewriting history.

### Privacy and retention

- Foreground application exclusions.
- Optional local-only payment-card-like and contextual OTP suppression.
- Safe defaults preserve ordinary capture.
- General and per-type age retention for RECENT.
- PINNED entries remain outside retention cleanup.
- Startup, Apply, manual, periodic, and bounded exit cleanup.

### Settings redesign

- Nine-page Settings architecture.
- Draft state, Apply, Cancel, scoped reset, and inline validation.
- Compact, standard, and wide responsive modes.
- Keyboard navigation, accessible names, and visible focus.
- Dedicated Shortcuts, Data, and About pages.

### Database maintenance and recovery

- Database status and storage metrics.
- SQLite integrity check.
- Explicit WAL checkpoint.
- Vacuum/optimize operation.
- Versioned `.xclip-backup`.
- Strict archive/schema/config/integrity validation.
- Restore with staged replacement and rollback protection.
- Transactional database migration with retry after failure.

### Large-data validation

An explicit release harness covers:

- up to 50,000 clips;
- a 500,000-character clip;
- 1,000 PINNED entries;
- 256 tags;
- 2,000 duplicate candidates;
- 25,000 retention deletions;
- rapid search/filter churn;
- heap, SQLite size, latency, and JavaFX responsiveness evidence.

### Windows lifecycle hardening

- Acknowledged single-instance activation.
- Explicit error for unrelated loopback port ownership.
- Tray/hotkey recovery after Explorer restart.
- Watcher recovery after sleep/resume and lock/unlock.
- Direct Paste target invalidation across lifecycle boundaries.
- Window recovery after monitor or DPI changes.
- Stale autostart launcher repair.
- Ordered shutdown and bounded clear-on-exit.
- Fixed per-user MSI upgrade identity.

## Local-first data contract

Default data directory:

```text
%USERPROFILE%\.xclip\
```

Primary files:

```text
xclip.db
config.json
```

Backups contain:

```text
manifest.properties
xclip.db
config.json
```

XClip does not include telemetry in the documented product contract.

## Compatibility

```text
Windows:       10 / 11 x64
Application:   1.4.0
Config schema: 5
SQLite schema: 6
UI contract:   19
Backup format: 1
```

The packaged application includes its Java runtime.

## License

- XClip source: GNU GPL-3.0-only.
- Selected Lucide icons: ISC License.
- See `LICENSE`, `THIRD_PARTY_NOTICES.md`, and packaged license resources.

## Validation still required

Before these notes can become final:

- complete the full manual product checklist;
- run all automated gates on the release host;
- build the final MSI;
- complete all 18 packaged Windows lifecycle cases;
- validate upgrade, uninstall, and reinstall;
- verify preserved data and autostart;
- capture final screenshots;
- generate checksums;
- confirm clean clone and clean repository;
- create and push the release tag;
- publish the GitHub Release.

## Known release-documentation constraint

The product roadmap, Gradle project version, JAR manifest, jpackage app version, and current machine-readable UI contract are aligned to **v1.4.0**. The historical v1.3.0 R11 contract remains frozen separately. Final MSI validation, checksums, tag, and release artifacts must still be verified before publication.
