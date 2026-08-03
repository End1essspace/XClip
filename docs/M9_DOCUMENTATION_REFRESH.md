# XClip M9.1 — Documentation Refresh

Status: prepared for review
Change type: documentation only
Runtime code changes: none
Build/packaging changes: none

## Purpose

M9.1 synchronizes repository documentation with the implementation baseline
through M8 while deliberately postponing manual product validation, packaged
MSI evidence, checksums, tagging, and public release.

This stage must not be interpreted as final release approval.

## Updated documents

```text
README.md
CHANGELOG.md
XClip_actual_roadmap_2026-08-02.md
docs/FEATURE_VALIDATION_HISTORY_v1.3.0.md
docs/M8_WINDOWS_LIFECYCLE.md
```

## New documents

```text
docs/USER_GUIDE_v1.3.0.md
docs/USER_GUIDE_v1.3.0_RU.md
docs/M9_MANUAL_VALIDATION_PLAN.md
docs/RELEASE_NOTES_v1.3.0_DRAFT.md
docs/M9_DOCUMENTATION_REFRESH.md
```

## Documented baseline

```text
Application version: 1.3.0
Config schema:       5
SQLite schema:       6
UI contract:         18
Backup format:       1
```

The documentation covers:

- clipboard capture and duplicate behavior;
- popup, keyboard, Direct Paste, and safe actions;
- PINNED workflow;
- tags and advanced search;
- privacy and retention;
- nine-page Settings architecture;
- database status, integrity, checkpoint, optimize, backup, and restore;
- large-data validation;
- Windows lifecycle hardening;
- local data, licensing, and privacy;
- future manual validation and release gates.

## Explicitly deferred

The following are not completed by M9.1:

- manual functional validation;
- final M8 automated evidence review;
- final MSI build and installed-app validation;
- 18-case packaged Windows lifecycle evidence;
- upgrade/uninstall/reinstall proof;
- final screenshots;
- checksums;
- version tag;
- GitHub Release.

## Review requirements

Before committing this documentation stage:

- verify all Markdown links;
- confirm English/Russian user guides describe the same product contract;
- confirm no file claims manual or packaged PASS;
- confirm version/schema values match the source tree;
- confirm data location and privacy wording are consistent;
- confirm release notes remain marked DRAFT;
- run `git diff --check`.

No application runtime test is required merely to review Markdown, but the final
release remains blocked until the complete manual and packaged validation plan
is executed.

## Versioning note

The repository currently declares `1.3.0`, while the historical changelog
already contains an earlier `[1.3.0]` entry. This documentation-only stage does
not change the version. The final release phase must choose and apply one
consistent version across Gradle metadata, changelog, release notes, tag, MSI,
and checksums.
