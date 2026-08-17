

# XClip — Roadmap

**Статус:** v1.4.0 release candidate / publication stage  
**Актуализировано:** 2026-08-17  
**Текущая development/release target:** v1.4.0  
**Стек:** Java 17, JavaFX 21, SQLite, Gradle, JNA, Windows 10/11  
**SQLite schema:** 6  
**Config schema:** 5  
**UI contract:** 19  
**Backup format:** 1  
**Основная ветка:** `main`

**Текущая точка:** automated regression, v1.4.0 metadata migration, clean MSI packaging и installed packaged smoke check прошли. Gate C принят по уже выполненным итеративным ручным UI-проверкам. Формальный M8 18-case evidence run waived для v1.4.0 и не заявляется как PASS. Остался Gate E: final release commit → fresh MSI/checksum → tag `v1.4.0` → GitHub Release.

---


## Version-line rule

- **v1.3.0** is the historical release dated 2026-06-05.
- **v1.4.0** is the active development/release target for the feature expansion and hardening cycle documented here.
- `app/build.gradle.kts` declares `1.4.0`.
- the current machine-readable contract is `ui-contract-v1.4.0.properties`, revision `19`.
- the old `ui-contract-v1.3.0.properties` and `UI_CONTRACT_v1.3.0.md` remain frozen only for historical R11 evidence.
- JAR and jpackage MSI versioning are derived from `project.version`, so successful post-migration packaging must report `1.4.0`.

---

# 1. Product contract

XClip — local-first Windows clipboard manager с persistent history, Direct Paste, PINNED, tags, advanced search, content-aware safe actions, privacy controls, retention, database maintenance и Windows lifecycle recovery.

Неподвижные продуктовые гарантии:

- clipboard commands не выполняются автоматически;
- executable/script paths не запускаются как команды;
- пользовательские данные остаются локальными;
- Direct Paste отправляет обычный `Ctrl+V` в ранее захваченную external target;
- PINNED не удаляются обычным RECENT cleanup;
- destructive automation требует явного opt-in;
- большие clips отображаются bounded preview;
- full content не показывается через automatic hover tooltip;
- длительные DB/UI операции не должны блокировать JavaFX Application Thread;
- programmatic JavaFX остаётся UI architecture, FXML не вводится.

---

# 2. Общий статус

| Блок | Статус |
|---|---|
| Clipboard ingest + persistent SQLite history | ✅ Complete |
| Direct Paste + Copy fallback | ✅ Complete |
| PINNED titles/manual ordering | ✅ Complete |
| Content types + safe actions | ✅ Complete |
| Tags | ✅ Complete |
| Advanced Search | ✅ Complete |
| Duplicate Preferences | ✅ Complete |
| Privacy Controls | ✅ Complete |
| Retention/Cleanup | ✅ Complete |
| Popup decomposition + responsive UI | ✅ Complete |
| Settings redesign | ✅ Complete |
| Database maintenance/backup/restore | ✅ Complete |
| Large-data hardening assets | ✅ Implemented |
| Windows lifecycle hardening assets | ✅ Implemented |
| R10/R11 historical regression baseline | ✅ Complete |
| M9.2 popup polish | ✅ Implemented and manually accepted |
| Documentation synchronization for v1.4.0 | ✅ Complete |
| Coordinated 1.4.0 build/UI-contract metadata bump | ✅ Complete |
| Post-migration clean test/build | ✅ PASS |
| Clean `XClip-1.4.0.msi` packaging | ✅ PASS |
| Installed packaged smoke check | ✅ PASS |
| Gate C dedicated rerun | ✅ Accepted from iterative manual validation |
| Formal M8 18-case evidence set | ⚪ WAIVED for v1.4.0; not executed/not PASS |
| Upgrade/uninstall/reinstall formal proof | ⚪ Not claimed; part of waived formal M8 suite |
| Final screenshots | ✅ Present in repository |
| Final checksum/tag/GitHub Release | ⬜ Pending |

---

# 3. Completed feature foundation

## 3.1 Clipboard and persistence

- adaptive clipboard observation;
- SQLite WAL history;
- configurable maximum history / clip limits;
- app-originated write suppression;
- bounded UI clip loading;
- bounded preview for large values;
- schema 6 migration and retry-safe DB initialization.

## 3.2 Direct Paste

- foreground target capture;
- restore previously active external window;
- standard `Ctrl+V` injection;
- Copy fallback;
- stale target invalidation across Windows lifecycle boundaries.

## 3.3 PINNED

- persistent pin state;
- optional title;
- manual dense `pin_order`;
- Move Up/Down/Top/Bottom;
- deterministic ordering after restart;
- duplicate policy integration.

## 3.4 Content types and safe actions

```text
TEXT
CODE
URL
PATH
JSON
COMMAND
```

Safe actions:

| Type | Action |
|---|---|
| URL | Open HTTP(S) in browser |
| PATH | Show in Explorer |
| JSON | Copy formatted JSON |
| CODE | Copy code |
| COMMAND | Copy command |
| TEXT | No special primary action |

Commands are never executed by XClip.

---

# 4. Tags and Advanced Search

## Tags — complete

- persistent many-to-many tags;
- case-insensitive identity;
- create/assign/remove;
- batch editor;
- row chips + bounded `+N` overflow;
- tag filter;
- rename/delete/usage count;
- cleanup unused tags.

## Search — complete

Supported operators:

```text
type:url
type:code
type:path
type:json
type:command
type:text
is:pinned
is:recent
tag:work
tag:"Project Work"
-tag:private
-type:text
```

Current Search UI:

- content/title/tag search;
- contextual suggestions;
- active query chips;
- non-blocking diagnostics;
- stale-result rejection;
- floating Search Assist overlay that does not reserve permanent header height.

---

# 5. Duplicate, Privacy, Retention

## Duplicate policy — complete

- RECENT move/preserve;
- PINNED preserve/move-to-top;
- whitespace normalize/preserve;
- case sensitive/insensitive;
- finite/unlimited time window;
- exact-content mode;
- four indexed equality hashes.

## Privacy — complete

- excluded foreground applications by executable basename;
- optional local payment-card-like suppression;
- optional contextual 4–8 digit OTP suppression;
- safe capture defaults;
- fail-open behavior;
- existing history is not rescanned/deleted when rules change.

## Retention — complete

- general RECENT max age;
- per-type max age;
- strictest active rule wins;
- PINNED excluded;
- startup/Apply/manual/periodic cleanup;
- optional clear RECENT on exit;
- bounded batched deletion.

---

# 6. Popup UI/UX baseline

## Historical R0–R11

Completed:

- custom undecorated window chrome;
- dark navy/graphite theme;
- Lucide SVG infrastructure;
- title/header/filter/list/footer decomposition;
- grouped PINNED/RECENT rows;
- responsive breakpoints;
- keyboard-first workflow;
- accessibility labels/focus;
- bounded previews and virtualization;
- Quick Help and contextual menus;
- R10 responsive/performance validation assets;
- R11 full regression/UI freeze assets.

R11 remains historical evidence; changes after it must be treated as explicit post-freeze deltas.

## M9.2 — post-freeze popup polish

**Status:** ✅ implemented, final regression pending

Implemented on 2026-08-17 baseline:

- contextual floating Search Assist;
- calm palette/readability tuning;
- improved metadata readability;
- wider window-control targets;
- subtle centered `X-SERIES` wordmark in title bar;
- slightly larger scoped Actions-menu text/icons;
- Fitts-law maximized top-right Close hardening;
- 16 px logical scrollbar interaction lane with slim visual thumb;
- Fitts-law maximized physical-right-edge scrollbar thumb drag.

### Close hard-edge behavior

`WindowsCloseCornerSupport` is defensive only and active only when the popup is:

```text
visible + focused + not iconified + maximized + Windows
```

It does not create an outside-window Close target in restored mode and does not extend into another monitor/system UI.

### Scrollbar hard-edge behavior

`WindowsListScrollEdgeSupport` keeps the actual scrollbar adjacent to the screen edge, uses the real thumb Y-range, and enables physical-right-edge thumb acquisition only for an eligible maximized active popup.

Development spot checks:

```text
Physical top-right Close       ✅ works
Physical right-edge thumb      ✅ works
```

These are not final release evidence.

---

# 7. Settings — complete

Nine pages:

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

Implemented:

- draft/current state separation;
- Apply/Cancel;
- scoped reset;
- inline validation;
- compact/standard/wide layouts;
- keyboard navigation/accessibility;
- asynchronous long-running data operations.

---

# 8. Database / large data — complete implementation

## Database maintenance

- status/size/schema/journal metrics;
- `PRAGMA integrity_check`;
- `wal_checkpoint(TRUNCATE)`;
- `VACUUM` + `PRAGMA optimize`;
- versioned `.xclip-backup`;
- strict archive/schema/config/integrity validation;
- staged restore + rollback protection;
- exit after successful restore.

## Large-data harness

Explicit validation targets include:

- 1k / 10k / 50k clips;
- 500k-character clip;
- 1,000 PINNED;
- 256 tags;
- 2,000 duplicate candidates;
- 25,000 retention deletions;
- bounded heap/SQLite/UI response budgets.

---

# 9. Windows lifecycle — implementation complete; formal packaged evidence waived for v1.4.0

Implemented:

- acknowledged single-instance protocol;
- unrelated-port failure handling;
- tray/hotkey recovery after Explorer restart;
- watcher recovery after sleep/resume and lock/unlock;
- stale Direct Paste target invalidation;
- display topology / DPI window recovery;
- autostart repair;
- ordered/idempotent shutdown;
- per-user MSI packaging with bundled runtime.

For v1.4.0, the release owner waived the separate 18-case packaged evidence run after successful clean MSI packaging and installed-app smoke validation. This is a waiver, not a PASS result; the formal M8 suite remains available for future validation.

---

# 10. Documentation baseline

Current documentation is synchronized as of 2026-08-17.

README uses the packaged application icon and repository screenshots:

```text
app/src/main/resources/icons/icon.png
docs/screenshots/xclip-popup.png
docs/screenshots/xclip-settings.png
```

The screenshot assets are present in the repository.

Canonical user/developer docs:

```text
README.md
CHANGELOG.md
docs/roadmap.md
docs/USER_GUIDE.md
docs/USER_GUIDE_RU.md
docs/UI_CONTRACT.md
docs/VALIDATION.md
docs/M7_DATABASE_MAINTENANCE.md
docs/RELEASE_NOTES_v1.4.0.md
```

Preserved build/release-gate assets:

```text
docs/UI_CONTRACT_v1.3.0.md
docs/M6_SETTINGS_VALIDATION.md
docs/M6_SETTINGS_REGRESSION_MATRIX.csv
docs/M7_DATABASE_REGRESSION_MATRIX.csv
docs/M7_LARGE_DATA_VALIDATION.md
docs/M7_LARGE_DATA_MATRIX.csv
docs/M8_WINDOWS_LIFECYCLE.md
docs/M8_WINDOWS_LIFECYCLE_MATRIX.csv
docs/R10_VALIDATION.md
docs/R11_REGRESSION_UI_FREEZE.md
docs/R11_REGRESSION_MATRIX.csv
docs/R11_SCREENSHOT_SET.csv
```

The milestone-named files above remain separate because current Gradle/release
gates use them as frozen executable evidence. They are not the primary
documentation entry points.

---

# 11. Release path status

## Gate A — documentation review

Status: ✅ Complete for release preparation.

- stable docs structure is in place;
- README uses repository-backed image paths;
- EN/RU guides, roadmap, UI contract, validation, and release notes are synchronized to v1.4.0;
- frozen M6/M7/M8/R10/R11 assets remain intact.

## Gate B — automated regression

Status: ✅ PASS.

Observed on 2026-08-17:

```text
clean test --no-daemon -> BUILD SUCCESSFUL
build --no-daemon      -> BUILD SUCCESSFUL
M6                      -> contract=19
M7 Database             -> contract=19
M7 Large Data assets    -> contract=19
M8 assets               -> contract=19
R11                     -> 38 cases / 9 screenshots
```

## Gate B.5 — coordinated v1.4.0 metadata

Status: ✅ PASS.

- Gradle project version: `1.4.0`;
- current machine contract: `ui-contract-v1.4.0.properties`, revision `19`;
- historical R11 v1.3.0 contract preserved separately;
- generated JAR: `app-1.4.0.jar`;
- jpackage app version: `1.4.0`.

## Gate C — manual UI regression

Status: ✅ Accepted from iterative manual validation.

A separate redundant rerun was intentionally skipped because the final UI changes
were checked manually as they were implemented, including the centered branding,
Actions-menu readability, Search Assist overlay, physical top-right Close,
restored-window safety, scrollbar hit lane/right-edge drag, empty-state centering,
footer centering, scaling, maximize/restore, and relevant multi-monitor/system-edge
safety.

## Gate D — packaged validation

Status: ✅ Accepted for v1.4.0 with an explicit formal-evidence waiver.

Verified:

- clean `XClip-1.4.0.msi` build;
- bundled runtime image creation/verification;
- installed packaged application launch;
- About/version contract check;
- `Ctrl+Shift+V` popup invocation;
- clipboard history smoke behavior.

Not claimed:

- the formal M8 `18/18` evidence matrix was not executed;
- no M8 `PASS.txt` is claimed;
- individual formal upgrade/uninstall/reinstall lifecycle cases are not claimed as PASS.

Release decision:

- the release owner waived the separate formal 18-case M8 evidence requirement for v1.4.0;
- M8 scripts/matrix remain available for future formal lifecycle validation.

## Gate E — release

Status: ⬜ In progress.

Remaining:

1. apply this final release-documentation update;
2. `git diff --check`;
3. final release commit and push;
4. rebuild `XClip-1.4.0.msi` from the release commit;
5. generate SHA-256;
6. create/push tag `v1.4.0`;
7. publish the GitHub Release with final notes and MSI.

No additional M8-001 → M8-018 run is required by the selected v1.4.0 release policy.

---

# 12. Git workflow

After every completed and verified milestone:

```text
git add .
git commit -m "..."
git push
```

Do not treat a milestone as formally closed until commit/push is confirmed.
