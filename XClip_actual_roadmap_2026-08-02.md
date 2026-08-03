# XClip — актуальный полный roadmap разработки

**Статус документа:** единый актуальный рабочий roadmap
**Дата актуализации:** 2026-08-03
**Текущая версия приложения:** v1.3.0
**Технологический стек:** Java 17, JavaFX 21, SQLite, Gradle, JNA, Windows 10/11
**Текущая версия схемы БД:** 6
**Текущая версия config schema:** 5
**Текущая ревизия UI contract:** 18
**Основная ветка:** `main`
**Текущая точка:** M9.1 documentation refresh подготовлен; M8 packaged evidence и release operations отложены до ручной проверки
**Следующий основной milestone:** ручная проверка продукта и M8 evidence; затем M9 packaging/release

---

# 1. Назначение документа

Этот файл является единственным актуальным планом развития XClip.

Он объединяет:

- завершённый clipboard-фундамент;
- завершённый popup UI/UX redesign;
- custom window chrome;
- Lucide SVG infrastructure;
- Tags;
- Advanced Search;
- Duplicate Preferences;
- Privacy Controls;
- Retention and Cleanup;
- завершённый Settings Redesign;
- Data/Performance Hardening;
- Windows Lifecycle Hardening;
- documentation, packaging и final release preparation.

Старые roadmap-файлы до 2026-08-02 считаются историческими и не должны использоваться как текущий источник статуса.

После каждого завершённого milestone обязательны:

1. автоматические тесты;
2. build;
3. ручная проверка, когда этап затрагивает runtime или UI;
4. `git diff --check`;
5. отдельный Git commit;
6. `git push`;
7. обновление roadmap.

Git gate для всех следующих этапов даётся только простыми отдельными командами:

```text
git add .
git commit -m "..."
git push
```

Milestone не считается формально закрытым, пока commit и push не подтверждены.

---

# 2. Продуктовый контракт XClip

XClip — локальный Windows clipboard manager с persistent history, поиском, Direct Paste, закреплёнными записями, типами содержимого, фильтрами, тегами, duplicate policy, privacy controls и управляемым retention.

## 2.1. Основные принципы

- Все пользовательские данные хранятся локально.
- Clipboard-команды никогда не выполняются автоматически.
- Пути к исполняемым файлам и скриптам не запускаются.
- Direct Paste восстанавливает ранее активное окно и отправляет стандартный `Ctrl+V`.
- PINNED не удаляются обычной очисткой history.
- PINNED не участвуют в age/type retention cleanup.
- Автоматическая destructive policy должна быть явно включена пользователем.
- Sensitive-content detection работает локально.
- Existing history не сканируется и не удаляется sensitive-content rules.
- UI не должен блокировать JavaFX Application Thread длительными операциями БД.
- Большие clipboard entries отображаются только в bounded preview.
- Автоматический hover-tooltip полного содержимого запрещён.
- Все изменения SQLite должны быть миграционно совместимыми.
- Интерфейс остаётся programmatic JavaFX; FXML не вводится.
- Основное окно открывается maximized, но не в exclusive fullscreen.
- Панель задач Windows остаётся доступной.
- Popup остаётся keyboard-first и пригодным для Direct Paste workflow.
- Ошибка foreground resolver или sensitive detector не должна приводить к тихой потере данных.
- Состояние UI не должно приводить к потере clipboard data.

---

# 3. Общий статус проекта

## 3.1. Сводка

| Блок | Статус |
|---|---|
| Clipboard ingest и persistent history | ✅ Завершено |
| Direct Paste и Copy-only fallback | ✅ Завершено |
| PINNED workflow и manual order | ✅ Завершено |
| Content types, filters и safe actions | ✅ Завершено |
| Popup UI redesign R0–R9 | ✅ Завершено |
| R10 — Responsive/Performance Validation | ✅ Завершено |
| R11 — Full Regression/UI Freeze | ✅ Завершено |
| Tags 2.1–2.4 | ✅ Завершено |
| Advanced Search 3.1–3.3 | ✅ Завершено |
| Duplicate Preferences 4.1–4.3 | ✅ Завершено |
| Privacy 5.1 — Excluded Applications | ✅ Завершено |
| Privacy 5.2 — Sensitive Content Rules | ✅ Завершено |
| Privacy 5.3 — Retention and Cleanup | ✅ Завершено |
| Repository Cleanup C1–C3 | ✅ Завершено |
| M6 — Settings Redesign | ✅ Завершено |
| M7 — Data/Performance Hardening | ✅ M7.2–M7.3 завершены |
| M8 — Windows Lifecycle Hardening | 🟨 Runtime готов, packaged evidence pending |
| M9 — Documentation/Packaging/Release | ⬜ Не начат |

## 3.2. Оценка готовности

```text
Основной clipboard-функционал:       ~96%
Popup UI и UX:                       ~95%
Tags и Advanced Search:             ~100%
Duplicate и Privacy Controls:       ~100%
Repository hygiene:                 ~100%
Settings architecture:              ~100%
Data/Performance Hardening:          ~75%
Windows Lifecycle Hardening:         ~45%
Packaging/Release Readiness:         ~65%

Полный расширенный roadmap:          ~91–93%
```

XClip уже является функционально зрелым clipboard manager с очищенным production-кодом,
ресурсами и validation-документацией. Основная оставшаяся работа относится к large-data validation, Windows lifecycle
hardening и final release gate.

---

# 4. Завершённый функциональный фундамент

## 4.1. Milestone 1.1 — Clipboard ingest и duplicate recency

**Статус:** ✅ завершено

Реализовано:

- adaptive clipboard watcher;
- startup barrier;
- pause/resume barrier;
- self-copy suppression;
- SHA-256 content keys;
- duplicate recency;
- `last_copied_at`;
- `use_count`;
- bounded maximum clipboard size;
- minimum clip length;
- automatic history limit pruning;
- SQLite WAL;
- local persistent storage.

---

## 4.2. Milestone 1.2 — Direct Paste

**Статус:** ✅ завершено

Реализовано:

- захват ранее активного внешнего окна;
- запись выбранного текста в clipboard;
- скрытие XClip;
- восстановление target window;
- отправка `Ctrl+V`;
- fallback в Copy-only;
- Enter и double-click используют Direct Paste;
- `Ctrl+C` выполняет Copy-only;
- self-copy suppression не создаёт повторную запись.

---

## 4.3. Milestone 1.3 — PINNED titles и compact presentation

**Статус:** ✅ завершено

Реализовано:

- optional title;
- F2 rename;
- Rename/Clear title;
- title не меняет clipboard content;
- compact row без title;
- title + preview при наличии title;
- title участвует в поиске;
- metadata сохраняется при repin и повторном copy.

---

## 4.4. Milestone 1.4 — Manual PINNED order

**Статус:** ✅ завершено

Реализовано:

- `pin_order`;
- миграция старого порядка;
- `Alt+Up` и `Alt+Down`;
- Move Up/Down/Top/Bottom;
- deterministic dense ordering;
- сохранение после restart;
- новый pin и repin идут наверх;
- duplicate pinned clip obeys configured duplicate policy.

---

## 4.5. Milestone 1.5 — Content types и badges

**Статус:** ✅ завершено

Поддерживаемые derived types:

```text
TEXT
CODE
URL
PATH
JSON
COMMAND
```

Тип вычисляется локально и не хранится в БД.

---

## 4.6. Milestone 1.6 — Scope/type filters

**Статус:** ✅ завершено

Реализовано:

- `All`;
- `Pinned`;
- `Recent`;
- `All types`;
- type filters;
- combination with search;
- reset filters;
- empty states;
- counters;
- stale async result protection;
- сохранение deterministic order.

---

## 4.7. Milestone 1.7 — Safe type actions

**Статус:** ✅ завершено

| Тип | Primary action |
|---|---|
| URL | Open in browser |
| PATH | Show in Explorer |
| JSON | Copy formatted JSON |
| CODE | Copy code |
| COMMAND | Copy command |
| TEXT | Нет primary action |

Гарантии:

- команды не выполняются;
- executable/script path не запускается;
- URL ограничены `http`/`https`;
- JSON formatting не меняет DB record;
- external actions отделены от domain layer.

---

# 5. Фаза R — Popup UI/UX redesign

## 5.1. R0 — UI contract freeze

**Статус:** ✅ завершено

Зафиксированы:

- maximized desktop window;
- dark graphite/navy palette;
- custom title bar;
- compact density;
- Lucide SVG;
- grouped PINNED/RECENT;
- bounded preview;
- context-aware actions;
- unified footer;
- keyboard-first UX.

---

## 5.2. R1 — Popup decomposition

**Статус:** ✅ завершено

Выделены:

- `PopupHeader`;
- `PopupFilterBar`;
- `PopupActionBar`;
- `PopupRow`;
- `PopupRows`;
- `PopupViewState`;
- `ClipRowCell`;
- `PopupActionsMenu`;
- `PopupTitleBar`;
- reusable policy/model classes.

`PopupWindow` остаётся coordinator, а rendering, actions, filtering и view-state вынесены в отдельные компоненты.

---

## 5.3. R2 — Custom window chrome

**Статус:** ✅ завершено

Реализованы:

- `StageStyle.UNDECORATED`;
- minimize;
- maximize;
- restore;
- close-to-background;
- manual resize;
- title drag;
- taskbar-safe maximize;
- restored bounds persistence;
- off-screen recovery;
- multi-monitor-aware positioning;
- negative-coordinate support;
- maximize/restore icon state;
- Fitts-law close target in top-right corner.

---

## 5.4. R3 — Theme и SVG infrastructure

**Статус:** ✅ завершено

Theme разделён на:

```text
theme.css
controls.css
popup.css
dialogs.css
styles.css
```

Lucide infrastructure включает:

- `SvgIcon`;
- `UiIcon`;
- SVG resource cache;
- CSS recoloring;
- XML hardening;
- resource tests;
- attribution;
- icons в header, rows, footer, filters и menus.

---

## 5.5. R4–R7 — Header, filters, rows и footer

**Статус:** ✅ завершено

Реализованы:

- custom title bar;
- compact main header;
- search field;
- Clips/Selected counters;
- Pause/Resume;
- Settings;
- Quick Help;
- scope/type/tag filters;
- grouped PINNED/RECENT rows;
- selection controls;
- type badges;
- timestamps;
- bounded preview;
- Paste split button;
- Copy;
- Actions;
- Pin/Unpin;
- Delete;
- responsive footer status zone.

---

## 5.6. R8 — Dialogs, menus, Quick Help и status

**Статус:** ✅ завершено

Реализованы:

- context-menu lifecycle;
- auto-dismiss;
- Quick Help popover;
- bounded preview recovery;
- operation status;
- dark dialogs;
- rename/clear/delete confirmations;
- modal suppression;
- consistent UI styles.

---

## 5.7. R9 — Keyboard UX и accessibility

**Статус:** ✅ завершено в рамках UI freeze

Поддерживаемые shortcuts:

```text
Ctrl+Shift+V  Open XClip and capture target
Ctrl+K        Focus search
Ctrl+F        Focus search
Ctrl+L        Clear search
Enter         Paste
Ctrl+C        Copy
Ctrl+P        Pin/Unpin
F2            Rename pinned
Alt+Up        Move pinned up
Alt+Down      Move pinned down
Delete        Delete
Escape        Close menu / collapse / clear / hide
Ctrl+A        Select all visible clips
Ctrl+D        Clear selection
Ctrl+,        Open Settings
E             Expand/Collapse clip
```

Также реализованы:

- accessible labels;
- stable focus behavior;
- keyboard menu handling;
- visible focus states;
- shortcut documentation in Quick Help.

---

## 5.8. R10 — Responsive и Performance Validation

**Статус:** ✅ завершено

Проверены и зафиксированы:

- responsive breakpoints;
- bounded previews;
- virtualized rows;
- preview cache;
- tag-chip bounds;
- search result gating;
- no huge hover tooltip;
- no recycled-cell mixing;
- stable popup state;
- performance policies;
- validation documentation.

Основная matrix:

```text
1366×768
1920×1080
2560×1440
3840×2160

100%
125%
150%
```

---

## 5.9. R11 — Full Regression и UI Freeze

**Статус:** ✅ завершено

Добавлены:

- automated gate script;
- manual validation launcher;
- evidence validator;
- regression matrix;
- screenshot set;
- UI freeze contract;
- regression tests для основных popup workflows.

После R11 popup UI считается frozen. Изменения после freeze допускаются только при наличии отдельного milestone, regression evidence и обновления contract revision.

---

# 6. Tags

## 6.1. M2.1 — Persistent foundation

**Статус:** ✅ завершено

SQLite schema version 5 ввёл:

```text
tags
clip_tags
```

Текущая schema version 6 сохраняет tag foundation.

Реализованы:

- many-to-many;
- case-insensitive identity;
- normalization;
- maximum 64 characters;
- `ON DELETE CASCADE`;
- deterministic ordering;
- transactional operations;
- indexed lookup.

---

## 6.2. M2.2 — Create and assign tags

**Статус:** ✅ завершено

Реализованы:

- tag editor для одного clip;
- tag editor для multi-selection;
- create tag;
- select existing tags;
- remove assignment;
- atomic save;
- inline validation;
- case-insensitive duplicate handling;
- tri-state batch editing;
- no partial updates on failure.

---

## 6.3. M2.3 — Tag chips и filtering

**Статус:** ✅ завершено

Реализованы:

- chips в clipboard rows;
- bounded visible chip count;
- `+N` overflow;
- tag filter;
- search by tag name;
- clear tag filter;
- combined scope/type/search/tag execution;
- deterministic ordering;
- empty states.

---

## 6.4. M2.4 — Tag management

**Статус:** ✅ завершено

Реализованы:

- list all tags;
- usage count;
- rename;
- delete;
- confirmation;
- cleanup unused;
- collision handling;
- local-only persistence.

---

# 7. Advanced Search

## 7.1. M3.1 — Parser foundation

**Статус:** ✅ завершено

Поддерживаются:

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

Реализованы:

- deterministic tokenizer;
- quoted values;
- escaped quotes;
- invalid-query fallback;
- non-blocking diagnostics;
- pure-text remainder;
- parser unit tests.

---

## 7.2. M3.2 — Query execution

**Статус:** ✅ завершено

Реализованы:

- combination of operators;
- toolbar filters;
- negative type/tag operators;
- tag SQL predicates;
- title/content/tag search;
- deterministic ordering;
- bounded candidate scans;
- stale-result protection;
- unsatisfiable-plan detection.

---

## 7.3. M3.3 — Search UI

**Статус:** ✅ завершено

Реализованы:

- syntax hints;
- contextual suggestions;
- active query chips;
- non-blocking error display;
- text-only highlighting;
- integration with scope/type/tag toolbar filters.

Saved queries не входят в текущий product contract и не блокируют release.

---

# 8. Duplicate Behavior Preferences

## 8.1. M4.1 — Domain policy

**Статус:** ✅ завершено

Поддерживаются:

- move RECENT duplicate to top;
- preserve RECENT position;
- preserve PINNED position;
- move PINNED duplicate to top;
- whitespace normalization;
- whitespace preservation;
- case-sensitive/insensitive matching;
- finite duplicate window;
- unlimited duplicate window;
- exact-content mode.

---

## 8.2. M4.2 — Persistence и runtime

**Статус:** ✅ завершено

Реализованы:

- config migration;
- safe defaults;
- four indexed equality hashes;
- policy changes without history rewrite;
- immediate runtime application;
- duplicate candidate lookup by selected hash;
- schema version 6;
- removal of legacy unique hash constraint.

---

## 8.3. M4.3 — Settings UI

**Статус:** ✅ завершено

Реализованы:

- dedicated duplicate section;
- clear descriptions;
- safe defaults;
- reset;
- runtime apply;
- config persistence;
- accessibility labels.

---

# 9. Privacy Controls

## 9.1. M5.1 — Excluded Applications

**Статус:** ✅ завершено

Реализованы:

- foreground-process resolution;
- executable basename matching;
- case-insensitive policy;
- persisted exclusion list;
- path-to-basename normalization;
- invalid-entry sanitization;
- fail-open behavior;
- observation-before-privacy-decision;
- защита от позднего capture после переключения foreground app.

---

## 9.2. M5.2 — Sensitive Content Rules

**Статус:** ✅ завершено

Реализованы:

- optional payment-card-like suppression;
- 13–19 digits;
- spaces/hyphens;
- Luhn validation;
- leading-range and repeated-digit false-positive guards;
- optional contextual OTP suppression;
- 4–8 digit OTP;
- English, Russian и Uzbek context markers;
- standalone numbers are not blocked;
- explicit `CAPTURE`/`SKIP`;
- safe defaults capture normally;
- fail-open gate;
- no scan or destructive cleanup of existing history.

Config schema после M5.2: 4.
UI contract revision после M5.2: 9.

---

## 9.3. M5.3 — Retention and Cleanup

**Статус:** ✅ завершено

Реализовано:

- delete RECENT older than N days;
- range 1–3650 days;
- type-specific retention for:
  - TEXT;
  - CODE;
  - URL;
  - PATH;
  - JSON;
  - COMMAND;
- `0` disables type override;
- effective retention uses the stricter active rule;
- PINNED never participate;
- strict boundary: exactly N-day-old entry is preserved;
- clear all RECENT on exit;
- startup cleanup;
- apply-triggered cleanup;
- manual cleanup;
- scheduled cleanup every 6 hours;
- last-cleanup status;
- result/outcome/trigger/time/detail;
- popup refresh after deletion;
- atomic batched delete in chunks;
- protection against database recreation after `Clear ALL data`.

Technical versions introduced by M5.3:

```text
Config schema:       5
UI contract:        10
SQLite schema:       6
```

После последующей repository cleanup UI contract был синхронизирован до revision 11
из-за удаления двух неиспользуемых Lucide resources. Runtime semantics M5.3 не менялись.

### M5.3 closure

```text
Code                         ✅
Unit tests                   ✅
Build                        ✅
git diff --check             ✅
Manual validation            ✅
Commit / push                ✅
```

---

# 10. Repository Cleanup and Hardening C1–C8

**Статус:** ✅ завершено

Цель фазы — удалить подтверждённый технический мусор и сократить repository surface
без изменения пользовательского поведения, SQLite semantics или frozen popup UI.

## 10.1. C1.1 — Structure and unused resources

Завершено:

- удалён пустой legacy package `org/example`;
- удалены неиспользуемые `check-check.svg` и `ellipsis.svg`;
- удалены соответствующие `UiIcon` constants;
- icon resource tests синхронизированы с 30 зарегистрированными SVG;
- Gradle packaging gate переведён с независимого hardcoded count на
  `popup.iconCount` из UI contract;
- UI contract revision обновлён с 10 до 11.

## 10.2. C1.2 — Temporary artifacts and stale text

Завершено:

- удалены временные cleanup README/scripts;
- убраны устаревшие milestone/version comments;
- устранён незначимый text/whitespace noise;
- runtime, schema и UI behavior не менялись.

## 10.3. C2.1 — Confirmed dead code

Удалены accessor и convenience methods без production-, test- или build-callers,
включая неиспользуемые getters, status helpers и obsolete restart/reload wrappers.

Гарантии:

- public user-facing behavior не изменён;
- SQL и config persistence не изменены;
- UI contract не изменён;
- полный test/build gate пройден.

## 10.4. C2.2 — Redundant internal API overloads

Удалены доказанно неиспользуемые compatibility overloads в:

- `Config`;
- `ClipService`;
- `ClipboardWatcher`;
- `WatcherController`;
- `TrayController`;
- `PopupActionBar`;
- `PopupWindow`.

Сохранены canonical production signatures и test-oriented DAO helpers.

## 10.5. C3.1 — Documentation consolidation

Завершено:

- 12 повторяющихся validation checklists M2.2–M5.3 объединены в
  `docs/FEATURE_VALIDATION_HISTORY_v1.3.0.md`;
- README ENG/RUS получил единый Engineering Documentation index;
- сохранены отдельными current/frozen assets:
  - `UI_CONTRACT_v1.3.0.md`;
  - `R10_VALIDATION.md`;
  - `R11_REGRESSION_UI_FREEZE.md`;
  - `R11_REGRESSION_MATRIX.csv`;
  - `R11_SCREENSHOT_SET.csv`;
  - R11 automation scripts.

## 10.6. C3.2 — Roadmap synchronization

Первичная cleanup-синхронизация закрыла C1–C3 и подготовила переход к Settings.

## 10.7. C4 — Architectural consolidation

Завершено:

- удалены подтверждённые redundant domain abstractions;
- объединён popup responsive/layout support;
- централизован DAO connection context;
- унифицированы text validation и normalization helpers.

## 10.8. C5 — Hot-path optimization

Завершено:

- popup reload/search metadata cache;
- clipboard ingest и duplicate hash preparation;
- candidate evaluation без повторной incoming normalization;
- content classifier regex reuse;
- keyset pagination для retention cleanup;
- bounded batch deletion;
- deterministic watcher worker cleanup.

## 10.9. C6 — SQLite lifecycle and data cleanup

Завершено:

- единый transaction boundary с rollback и `autoCommit` recovery;
- общие SQLite connection PRAGMA;
- отслеживание и закрытие connections всех DAO threads;
- terminal DAO shutdown;
- удаление `.db`, `-wal`, `-shm` и `-journal`;
- безопасное освобождение connections перед `Clear ALL data`.

## 10.10. C7 — Test/build stability

Завершено:

- single-fork test runtime;
- глобальный JUnit timeout;
- обязательная очистка `@TempDir`;
- bounded executor waits;
- устранение timing-dependent polling;
- lifecycle assertions для watcher, paste и cleanup workers.

## 10.11. C8 — Final automated baseline audit

Завершено:

- полный source/resource/lifecycle audit очищенного baseline;
- второй полный test pass в фиксированном randomized order через `c8BaselineGate`;
- исправлено восстановление `HistoryCleanupService` после неудачного
  `Clear ALL data`;
- roadmap синхронизирован с фактической точкой продолжения.

Application version остаётся v1.3.0. Config / SQLite / UI contract:
`5 / 6 / 12`.

---

# 11. M6 — Settings Redesign

**Статус:** ✅ M6.1–M6.5 завершены

## 11.1. Цель

Преобразовать Settings в полноценную многостраничную architecture без изменения
проверенной runtime semantics.

**M6.1–M6.5 завершены:**

- создан shell с left sidebar и custom undecorated window chrome;
- выделены девять независимых page composition classes;
- введены `SettingsDraft`, baseline/current session и field-level validation;
- Apply активен только для dirty и valid draft; Cancel восстанавливает baseline;
- scoped reset не затрагивает соседние секции;
- Data page показывает data/database/config paths, maintenance и destructive actions;
- Clear RECENT сохраняет PINNED, tags и config; Clear ALL выполняется вне FX thread;
- Shortcuts page использует общий `QuickHelpContent` и live hotkey conflict status;
- About page содержит version, author, GPL, notices, links и local-data statement;
- UI contract повышен до revision 15;
- Settings адаптирован для compact/standard/wide layout;
- добавлены accessible navigation/page names, keyboard validation action и visible focus;
- закрыт 24-case Settings regression gate через `m6SettingsGate`.

**Следующая подзадача:** M7.3 — large-data validation.

## 11.2. Целевая навигация

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

## 11.3. Основные требования

- left navigation;
- отдельные pages;
- grouped setting cards;
- persistent selected page;
- единый custom window chrome;
- responsive layout;
- keyboard navigation;
- accessible names;
- visible focus states;
- dirty-state tracking;
- `Apply`, `Cancel`, `Reset`;
- inline validation;
- restart-required indicators;
- async long-running data operations;
- no duplicated config state;
- сохранение всех текущих M4–M5 controls.

## 11.4. Page contract

### General

- start on boot;
- start minimized;
- watcher enabled;
- basic lifecycle behavior.

### Capture

- minimum clip length;
- maximum clip characters;
- foreground application exclusions;
- capture status.

### History

- maximum history size;
- age retention;
- per-type retention;
- clear RECENT on exit;
- run cleanup now;
- last cleanup status.

### Duplicate behavior

- RECENT duplicate position;
- PINNED duplicate position;
- whitespace mode;
- case sensitivity;
- time window;
- exact-content mode;
- reset duplicate policy.

### Privacy

- excluded applications overview;
- sensitive card rule;
- sensitive OTP rule;
- explanation of local-only processing;
- no automatic scan of existing history.

### Appearance

- current dark theme information;
- future density/theme settings only if added explicitly;
- no speculative controls without runtime support.

### Shortcuts

- global hotkey;
- popup shortcuts reference;
- conflict status;
- future rebind contract if implemented.

### Data

- open data folder;
- database path;
- config path;
- clear RECENT;
- clear ALL data;
- cleanup controls;
- database status, integrity, checkpoint and optimize controls;
- versioned backup and validated restore.

### About

- application version;
- author;
- license;
- third-party notices;
- links;
- data/privacy statement.

## 11.5. M6.5 — Responsive, accessibility and regression gate

**Статус:** ✅ завершено

Реализовано:

- visual-bounds-aware initial Settings size;
- responsive modes `COMPACT`, `STANDARD`, `WIDE`;
- stacked settings grids в compact mode;
- wrapping action rows;
- predictable initial focus on selected navigation item;
- accessible navigation position/purpose and page-scroll names;
- keyboard activation validation feedback через Enter/Space;
- visible focus для validation action;
- 24-case regression matrix;
- `m6SettingsGate`, включающий полный `c8BaselineGate`;
- UI contract revision 15.

## 11.6. M6 acceptance gate

- all existing settings survive migration;
- no change to current safe defaults;
- Apply updates runtime immediately where supported;
- Cancel discards unsaved changes;
- invalid values cannot be persisted;
- keyboard-only navigation works;
- no clipped page at 1366×768 / 125%;
- destructive actions remain confirmed;
- tests/build/diff/manual/Git gate pass.

---

# 12. M7 — Data and Performance Hardening

**Статус:** ✅ M7.2–M7.3 завершены

Уже завершено:

- DAO connection ownership и terminal shutdown;
- transaction rollback/cleanup hardening;
- единые SQLite PRAGMA;
- popup/search/clipboard/classifier/retention hot-path optimization;
- large retention batch tests;
- deterministic repeatable test gate;
- database status и size diagnostics;
- `PRAGMA integrity_check`;
- explicit WAL checkpoint strategy;
- explicit off-UI-thread vacuum;
- versioned backup/restore archive;
- transactional migration rollback/retry;
- future-schema rejection before mutation;
- interrupted partial migration recovery.

Текущая foundation:

- SQLite WAL;
- busy timeout;
- atomic config writes;
- batched retention delete;
- bounded UI queries;
- virtualized rows;
- cache policies;
- stale-result protection.

## 12.1. M7.1 — DAO lifecycle

- audit `ThreadLocal<Connection>`;
- explicit connection ownership;
- close all worker-thread connections;
- transaction boundary review;
- busy/locked retry policy;
- shutdown ordering;
- avoid DB reopening after data deletion;
- batch operations for large selections;
- dense pin-order maintenance review.

## 12.2. M7.2 — Database maintenance

**Статус:** ✅ завершено

Реализовано:

- Settings Data page показывает schema, journal mode, DB/WAL/SHM size и
  reclaimable-page estimate;
- full `PRAGMA integrity_check`;
- explicit `wal_checkpoint(TRUNCATE)` после release DAO connections;
- explicit `VACUUM` + `PRAGMA optimize` вне JavaFX Application Thread;
- `.xclip-backup` format version 1;
- consistent SQLite snapshot через `VACUUM INTO`;
- manifest + database + normalized config archive;
- archive entry, size, schema, config и integrity validation;
- staged restore с rollback-файлами;
- successful restore завершает приложение для clean runtime reload;
- base schema и migrations объединены одной SQLite transaction;
- migration failure откатывает изменения и допускает retry того же `Database`;
- future `user_version` отклоняется до schema mutation;
- partial interrupted migration state восстанавливается idempotently;
- UI contract revision 16;
- `m7DatabaseGate`;
- 20-case regression matrix и отдельная maintenance documentation.

Canonical evidence:

- `docs/M7_DATABASE_MAINTENANCE.md`;
- `docs/M7_DATABASE_REGRESSION_MATRIX.csv`;
- `DatabaseMaintenanceServiceTest`;
- расширенный `DatabaseMigrationTest`.

## 12.3. M7.3 — Large-data validation

**Статус:** ✅ реализовано; canonical gate и evidence pipeline добавлены

Реализовано:

- deterministic isolated fixtures на `1 000`, `10 000` и `50 000` clips;
- отдельный 500 000-character clip;
- 1 000 PINNED с dense zero-based `pin_order`;
- 256 tags с deterministic assignments;
- 2 000 indexed duplicate candidates;
- 25 000 retention-eligible RECENT clips;
- production-equivalent popup data pipeline с bounded 200-row result;
- startup median/p95 measurements;
- cold popup pipeline p95;
- text, tag и derived-type search p95;
- 120-request rapid search/filter churn;
- repeated immutable row-build ordering check;
- deterministic JavaFX `ListView` scroll sequence;
- actual `HistoryCleanupService` cleanup on a copied 50k fixture;
- complete-run peak heap sampling under `-Xmx768m`;
- main SQLite file-size evidence;
- continuous JavaFX queue p95/max-stall probe;
- machine-readable `summary.json`, `metrics.csv` и
  `environment.properties`;
- explicit `m7LargeDataValidation` and aggregate `m7LargeDataGate`;
- normal `test`, `check` и `build` не запускают heavy 50k workload;
- UI contract revision 17;
- 18-case frozen matrix and dedicated documentation.

Canonical evidence:

- `LargeDataValidationPolicy`;
- `LargeDataValidationMain`;
- `LargeDataValidationPolicyTest`;
- `docs/M7_LARGE_DATA_VALIDATION.md`;
- `docs/M7_LARGE_DATA_MATRIX.csv`;
- `scripts/run_m7_large_data_validation.ps1`;
- runtime evidence under `app/build/reports/m7-large-data/`.

Acceptance budgets cover:

- startup;
- popup preparation;
- text/tag/type search;
- duplicate lookup;
- row build and scroll stability;
- retention cleanup;
- rapid churn;
- peak heap;
- DB size;
- JavaFX p95 queue delay and maximum stall.

---

# 13. M8 — Windows Lifecycle Hardening

**Статус:** 🟨 runtime hardening и automated gate реализованы; packaged evidence pending

R2/R11 уже покрывают часть window behavior, но M8 должен проверить packaged production lifecycle.

## 13.1. Validation matrix

- clean Windows start;
- autostart;
- start minimized;
- tray lifecycle;
- secondary launch;
- Explorer restart;
- sleep/resume;
- lock/unlock;
- display topology change;
- monitor disconnect;
- DPI change;
- user logoff;
- shutdown;
- global hotkey conflict;
- stale autostart entry;
- packaged MSI upgrade;
- uninstall;
- reinstall.

## 13.2. Required guarantees

- no duplicate tray icons;
- secondary launch activates primary instance;
- watcher resumes after sleep;
- window never restores off-screen;
- Direct Paste target is not stale after lock/resume;
- database closes cleanly;
- cleanup-on-exit does not block shutdown indefinitely;
- autostart points to the current executable;
- upgrade preserves user data;
- uninstall behavior is documented and verified.

## 13.3. Реализованный hardening

- acknowledged loopback single-instance protocol;
- explicit primary socket shutdown;
- Explorer shell PID recovery;
- idempotent tray reinstall and hotkey restart;
- sleep/resume heartbeat recovery;
- Windows lock/unlock input-desktop probe;
- watcher restart with fresh clipboard snapshot;
- Direct Paste target invalidation across lifecycle boundaries;
- display bounds/scale/DPI topology fingerprint;
- visible-screen recovery on topology changes;
- bounded three-second clear-on-exit operation;
- JVM shutdown hook and ordered backend shutdown;
- stale HKCU Run launcher repair;
- fixed MSI upgrade UUID/per-user/data-location contract;
- 18-case packaged evidence workflow.

## 13.4. Remaining M8 close gate

M8 закрывается только после:

- `m8WindowsLifecycleGate` PASS;
- packaged MSI built successfully;
- all 18 manual cases marked PASS;
- evidence validator creates `PASS.txt`;
- build and diff checks pass;
- Git gate completed.

---

# 14. M9 — Documentation, Packaging and Release

**Статус:** 🟨 документационная часть подготовлена; packaging/release отложены

## 14.1. Documentation refresh

**Статус:** 🟨 подготовлено; ожидает review и Git gate

В документационном этапе обновляются только Markdown-файлы. Код, Gradle tasks,
MSI, screenshots, checksums, tag и GitHub Release не изменяются.

Подготовлено:

- полностью синхронизированный README ENG/RUS;
- user guide ENG;
- user guide RUS;
- consolidated feature validation history through M8;
- M8 status wording без ложного packaged PASS;
- manual validation plan для последующего выполнения;
- draft release notes с явной пометкой DRAFT;
- roadmap с разделением documentation/manual/package/release phases;
- CHANGELOG для M6–M8 и documentation phase.

Отложено до ручной проверки:

- финальные screenshots установленной сборки;
- замена DRAFT release notes на final;
- clean-clone proof;
- final MSI build;
- install/upgrade/uninstall/reinstall evidence;
- checksums;
- tag;
- GitHub Release.

Важное ограничение:

- build metadata остаётся `1.3.0`;
- config schema остаётся `5`;
- SQLite schema остаётся `6`;
- UI contract остаётся `18`;
- backup format остаётся `1`;
- documentation-only patch не меняет runtime contract.
## 14.2. Automated release checks

```powershell
.\gradlew.bat clean test --no-daemon
.\gradlew.bat build --no-daemon
.\gradlew.bat clean packageMsi
git diff --check
```

Additional checks:

- clean clone;
- packaged launch;
- runtime image resources;
- install;
- upgrade;
- uninstall;
- clean machine;
- checksums;
- release notes;
- tagged commit.

## 14.3. Final release gate

Release запрещён при наличии:

- failing tests;
- data-loss issue;
- broken migration;
- broken Direct Paste;
- broken hotkey;
- broken tray lifecycle;
- off-screen window;
- critical DPI issue;
- unsafe execution;
- privacy regression;
- retention deleting PINNED;
- unverified package;
- dirty repository.

---

# 15. Рабочий процесс milestone

## 15.1. Перед реализацией

Перед каждым этапом указывается точный список файлов и папок, необходимых для работы.

Не запрашивается весь проект, если достаточно локального набора.

## 15.2. Поставка

Каждый milestone поставляется отдельным ZIP:

```text
XClip_<milestone>_<description>.zip
```

ZIP:

- сохраняет repo-relative paths;
- содержит только изменённые и новые файлы;
- распаковывается в root repository;
- не содержит build output;
- не содержит полную копию проекта без необходимости.

## 15.3. Проверка

Обязательные команды:

```powershell
.\gradlew.bat clean test --no-daemon
.\gradlew.bat build --no-daemon
git diff --check
```

PowerShell-команды предоставляются в одну строку без backtick continuation.

## 15.4. Manual validation

Проверяются:

- основной сценарий;
- edge cases;
- regression;
- persistence;
- restart;
- selection;
- Direct Paste;
- filters/search;
- Tags;
- duplicate behavior;
- privacy behavior;
- retention;
- visual state;
- keyboard behavior;
- window lifecycle.

## 15.5. Git gate

Для каждого проверенного этапа используются только простые отдельные команды:

```text
git add .
git commit -m "Краткое описание этапа"
git push
```

Составные PowerShell-команды, обязательный `pull --rebase`, перечисление отдельных
файлов и проверки в одной строке в Git gate не используются.

---

# 16. Definition of Done

Milestone завершён только когда:

- код реализован;
- ZIP применён;
- tests passed;
- build passed;
- `git diff --check` passed;
- manual checklist passed;
- критические regressions отсутствуют;
- commit создан;
- push выполнен;
- roadmap обновлён.

---

# 17. Актуальная последовательность следующих шагов

```text
Cleanup and hardening
     C1–C8                                   ✅

M6   Settings Redesign
     M6.1 Settings shell and navigation      ✅
     M6.2 Page extraction and config draft   ✅
     M6.3 Validation / Apply / Cancel / Reset ✅
     M6.4 Data/About/Shortcuts pages         ✅
     M6.5 Responsive/accessibility gate      ✅

M7   Data and Performance Hardening
     M7.2 Database maintenance               ✅
     M7.3 Large-data validation              ✅

M8   Windows Lifecycle Hardening             🟨 MANUAL PACKAGED EVIDENCE PENDING

M9   Documentation refresh                     🟨 PREPARED / REVIEW PENDING
     Manual product validation                 ⬜ NEXT USER PHASE
     Packaging and final release               ⬜ BLOCKED UNTIL VALIDATION
```

---

# 18. Текущая точка продолжения

```text
Current implementation baseline:
M8 runtime hardening and automated assets are present.

Current work:
M9.1 documentation-only synchronization.

Prepared documentation:
README ENG/RUS
USER_GUIDE_v1.3.0.md
USER_GUIDE_v1.3.0_RU.md
M9_DOCUMENTATION_REFRESH.md
M9_MANUAL_VALIDATION_PLAN.md
RELEASE_NOTES_v1.3.0_DRAFT.md
CHANGELOG and validation-history synchronization

Not executed yet:
full manual product validation
final M8 packaged evidence
final MSI verification
checksums
tag
GitHub Release

Next user-controlled phase:
manual validation of the application, data operations, lifecycle, and MSI.
```

Главное правило:

> Documentation completion не разрешает release. Packaging, tag и GitHub Release
> выполняются только после ручной проверки и полного M8 evidence PASS.

---
# 19. Финальный статус roadmap

```text
Clipboard and popup foundation             ✅
Tags / Advanced Search                     ✅
Duplicate / Privacy / Retention            ✅
Repository Cleanup C1–C3                   ✅
Architectural consolidation C4             ✅
Hot-path optimization C5                   ✅
SQLite lifecycle hardening C6              ✅
Test/build stabilization C7                ✅
Final automated baseline audit C8          ✅
Settings Redesign M6.1–M6.5                ✅
Database maintenance M7.2                  ✅
Large-data validation M7.3                 ✅
UI contract revision 18                    ✅

Windows Lifecycle runtime hardening        ✅
Windows Lifecycle packaged evidence        🟨 DEFERRED UNTIL MANUAL VALIDATION
M9.1 documentation refresh                 🟨 PREPARED / REVIEW PENDING
Manual product validation                  ⬜ NEXT USER PHASE
Packaging / tag / GitHub Release           ⬜ BLOCKED UNTIL VALIDATION
```

Data and Performance Hardening M7 полностью закрыт. M8 runtime и automated
assets реализованы, но packaged evidence ещё не выполнен. Документационная
часть M9.1 подготовлена отдельно. Следующий этап выбирает пользователь:
сначала полная ручная проверка, затем M8 evidence и только после этого
packaging, tag и GitHub Release.
