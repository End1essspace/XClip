
# XClip — Roadmap

**Статус:** active development roadmap  
**Актуализировано:** 2026-08-17  
**Текущая development/release target:** v1.4.0  
**Стек:** Java 17, JavaFX 21, SQLite, Gradle, JNA, Windows 10/11  
**SQLite schema:** 6  
**Config schema:** 5  
**UI contract:** 19  
**Backup format:** 1  
**Основная ветка:** `main`

**Текущая точка:** Gate B pre-migration automated regression прошёл; Gate B.5 metadata migration подготовлен: Gradle/current UI contract/runtime version text переводятся на v1.4.0. Следующий обязательный шаг — повторный `clean test` + `build` после миграции; затем Git gate и полный manual/packaged validation.

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
| M9.2 popup polish | ✅ Implemented; fresh release regression pending |
| Documentation synchronization for v1.4.0 | ✅ Prepared 2026-08-17 |
| Coordinated 1.4.0 build/UI-contract metadata bump | ⬜ Pending |
| Installed MSI + M8 18-case evidence | ⬜ Pending |
| Upgrade/uninstall/reinstall proof | ⬜ Pending |
| Final screenshots/checksums/tag/release | ⬜ Pending |

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

# 9. Windows lifecycle — implementation complete, packaged evidence pending

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

Still required: validated packaged 18-case M8 evidence set.

---

# 10. Documentation baseline

Current documentation is synchronized as of 2026-08-17.

README uses XCC-style product presentation and reserves:

```text
assets/xclip_app.png
docs/screenshots/xclip-popup.png
docs/screenshots/xclip-settings.png
```

The actual PNGs are intentionally supplied separately.

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
docs/RELEASE_NOTES_v1.4.0_DRAFT.md
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

# 11. Remaining release path

## Gate A — documentation review

- review README visual structure and stable docs links;
- add final logo/screenshots to reserved paths;
- verify `USER_GUIDE.md` / `USER_GUIDE_RU.md` consistency;
- verify `roadmap.md`, `UI_CONTRACT.md`, and `VALIDATION.md` agree on the v1.4.0 release line;
- keep frozen M6/M7/M8/R10/R11 gate assets intact;
- `git diff --check`.

## Gate B — automated regression

```powershell
.\gradlew.bat clean test --no-daemon
.\gradlew.bat build --no-daemon
```

Also run the explicit M7/M8 gates required by the release process.

## Gate B.5 — coordinated v1.4.0 metadata gate

Implementation prepared:

- ✅ `app/build.gradle.kts` → `1.4.0`;
- ✅ current machine-readable contract → `ui-contract-v1.4.0.properties`, revision `19`;
- ✅ historical R11 v1.3.0 contract retained independently;
- ✅ `UiContractFreezeTest` and packaged/current milestone gates redirected to the v1.4.0 contract;
- ✅ current Settings/About version text aligned to v1.4.0;
- ⬜ rerun `clean test` and `build`;
- ⬜ confirm generated JAR path reports `app-1.4.0.jar`;
- ⬜ commit/push this verified metadata migration;
- ⬜ verify MSI reports `1.4.0` during Gate D.

## Gate C — full manual UI regression

Mandatory new cases:

- QHD 2560×1440 at 125%;
- centered wordmark;
- Actions-menu readability;
- Search Assist overlay geometry;
- physical top-right Close;
- restored-window Close safety/cancellation;
- wide scrollbar hit lane;
- physical right-edge thumb drag;
- multi-monitor and system-panel safety.

## Gate D — packaged validation

- clean MSI build;
- installed launch;
- no-external-Java host;
- 18-case M8 lifecycle evidence;
- upgrade/uninstall/reinstall;
- data preservation.

## Gate E — release

- final screenshots;
- checksums;
- final release notes;
- clean commit;
- version/tag consistency;
- GitHub Release.

Release is blocked until all applicable gates are complete.

---

# 12. Git workflow

After every completed and verified milestone:

```text
git add .
git commit -m "..."
git push
```

Do not treat a milestone as formally closed until commit/push is confirmed.
