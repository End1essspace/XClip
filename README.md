[ENG]

# XClip

**XClip** is a local-first Windows clipboard manager built with **Java 17**, **JavaFX 21**, SQLite, Gradle, and JNA.

It combines persistent clipboard history with Direct Paste, PINNED items, tags, advanced search, content-aware actions, privacy controls, retention policies, database maintenance, and Windows lifecycle recovery.

> **Repository status:** the application implementation and automated validation assets are present through M8. Final packaged MSI validation, the 18-case Windows lifecycle evidence set, checksums, tagging, and the public release are intentionally pending manual verification.

## Core capabilities

### Clipboard capture and history

- Adaptive clipboard polling with idle backoff.
- Persistent SQLite history in WAL mode.
- SHA-256 duplicate lookup with configurable whitespace, case, exact-content, time-window, RECENT, and PINNED behavior.
- Configurable history size, minimum clip length, and maximum clip size.
- Bounded UI loading and bounded previews for large entries.

### Popup workflow

- Global shortcut: **Ctrl+Shift+V**.
- Direct Paste restores the previously active target and sends the standard `Ctrl+V`.
- Copy fallback remains available when Direct Paste is not appropriate.
- PINNED and RECENT scopes, content-type filters, tag filters, and multi-selection.
- Keyboard-first navigation, visible focus, accessible names, and responsive layouts.

### Search and content types

Advanced search supports:

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

Derived content types:

```text
TEXT
CODE
URL
PATH
JSON
COMMAND
```

Safe type-aware actions include opening URLs, revealing paths in Explorer, formatting JSON, and copying code or commands. XClip does **not** execute clipboard commands.

### PINNED items and tags

- Manual PINNED order.
- Optional titles for PINNED entries.
- Tag creation and assignment for one or multiple clips.
- Tag chips with bounded `+N` overflow.
- Tag filtering, search by tag name, rename, delete, usage count, and unused-tag cleanup.
- Atomic tag updates and case-insensitive tag identity.

### Privacy and retention

- Foreground application exclusions by executable basename.
- Optional local-only suppression of Luhn-valid payment-card-like values.
- Optional local-only suppression of contextual 4–8 digit OTP values.
- Age-based RECENT retention with per-type overrides.
- PINNED entries are excluded from ordinary retention cleanup.
- Optional clear-RECENT-on-exit with bounded shutdown behavior.
- Existing history is not automatically scanned or removed when privacy rules are enabled.

### Settings

Settings uses a nine-page architecture:

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

It includes draft-state tracking, Apply/Cancel semantics, scoped reset, inline validation, compact/standard/wide responsive modes, keyboard navigation, accessible names, and asynchronous long-running data operations.

### Database maintenance and recovery

Settings → Data provides:

- database status and size information;
- `PRAGMA integrity_check`;
- explicit WAL checkpoint;
- database optimization with `VACUUM` and `PRAGMA optimize`;
- manual retention cleanup;
- clear RECENT and clear ALL;
- versioned `.xclip-backup` creation;
- validated restore with rollback protection.

A backup contains:

```text
manifest.properties
xclip.db
config.json
```

Restore validates archive structure, schema versions, configuration, and SQLite integrity before replacing live files. A successful restore exits XClip so the restored state is loaded cleanly on the next start.

### Windows integration and lifecycle

- System tray operation.
- Single-instance activation through an acknowledged loopback protocol.
- Optional per-user autostart through `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`.
- Tray and hotkey recovery after Explorer restart.
- Watcher and Direct Paste recovery after sleep/resume and lock/unlock.
- Window recovery after display topology or DPI changes.
- Ordered, idempotent shutdown with bounded exit cleanup.
- Per-user MSI packaging with a bundled runtime.

## Architecture

```text
config  → configuration, paths, migration
data    → SQLite schema, DAO lifecycle, backup/restore
domain  → clipboard, duplicate, search, privacy, retention rules
system  → clipboard access, tray, hotkey, Windows lifecycle
ui      → JavaFX popup, dialogs, Settings
validation → explicit large-data release harness
```

## Local data

All user-owned data is stored locally under:

```text
%USERPROFILE%\.xclip\
```

| Purpose | File |
|---|---|
| Clipboard database | `xclip.db` |
| Configuration | `config.json` |
| SQLite runtime sidecars | `xclip.db-wal`, `xclip.db-shm`, or `xclip.db-journal` when present |

The MSI installation directory is separate from the data directory. Uninstall, reinstall, and upgrade behavior must still be confirmed by the packaged manual validation matrix before release.

## Build from source

```powershell
git clone https://github.com/End1essspace/XClip.git
```

```powershell
cd XClip
```

```powershell
.\gradlew.bat clean test --no-daemon
```

```powershell
.\gradlew.bat build --no-daemon
```

Build the MSI on Windows:

```powershell
.\gradlew.bat clean packageMsi --no-daemon
```

## Engineering documentation

- [English user guide](docs/USER_GUIDE_v1.3.0.md)
- [Russian user guide](docs/USER_GUIDE_v1.3.0_RU.md)
- [Documentation refresh scope](docs/M9_DOCUMENTATION_REFRESH.md)
- [Manual validation plan](docs/M9_MANUAL_VALIDATION_PLAN.md)
- [Draft release notes](docs/RELEASE_NOTES_v1.3.0_DRAFT.md)
- [UI contract](docs/UI_CONTRACT_v1.3.0.md)
- [Feature validation history](docs/FEATURE_VALIDATION_HISTORY_v1.3.0.md)
- [Settings validation](docs/M6_SETTINGS_VALIDATION.md)
- [Database maintenance and backup/restore](docs/M7_DATABASE_MAINTENANCE.md)
- [Large-data validation](docs/M7_LARGE_DATA_VALIDATION.md)
- [Windows lifecycle hardening](docs/M8_WINDOWS_LIFECYCLE.md)
- [Responsive and performance validation](docs/R10_VALIDATION.md)
- [Full regression and UI freeze](docs/R11_REGRESSION_UI_FREEZE.md)

## System requirements

- Windows 10 or Windows 11, 64-bit.
- No external Java installation is required for the packaged application.
- WiX and a JDK containing `jpackage` are required to build the MSI.

## Version and schemas

```text
Application version: 1.3.0
Config schema:       5
SQLite schema:       6
UI contract:         18
Backup format:       1
```

## Privacy

XClip is local-first and does not include telemetry in the documented product contract. Clipboard contents, tags, settings, and backups remain on the user’s machine unless the user explicitly copies or moves them elsewhere.

## License

XClip is licensed under **GNU GPL-3.0-only**.

Selected Lucide SVG icons are distributed under the ISC License. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the packaged license assets.

## Author

**Rafael Xudoynazarov — End1essspace | RX**

- Telegram: [@End1essspace](https://t.me/End1essspace)
- GitHub: [End1essspace](https://github.com/End1essspace)

---

[RUS]

# XClip

**XClip** — локальный менеджер буфера обмена для Windows на **Java 17**, **JavaFX 21**, SQLite, Gradle и JNA.

Он объединяет постоянную историю, Direct Paste, PINNED-записи, теги, расширенный поиск, действия по типу содержимого, privacy controls, retention, обслуживание базы данных и восстановление после Windows lifecycle events.

> **Статус репозитория:** реализация приложения и automated validation assets присутствуют до M8 включительно. Финальная проверка установленного MSI, 18-case Windows lifecycle evidence, checksums, tag и публичный релиз намеренно отложены до ручной проверки.

## Основные возможности

### Захват и история

- Адаптивный clipboard polling со снижением нагрузки в простое.
- Постоянная SQLite history в режиме WAL.
- SHA-256 duplicate lookup с настройкой пробелов, регистра, exact-content, time window и поведения RECENT/PINNED.
- Настраиваемый размер истории, минимальная длина и максимальный размер clip.
- Ограниченная загрузка UI и bounded preview для больших записей.

### Popup workflow

- Глобальная комбинация: **Ctrl+Shift+V**.
- Direct Paste восстанавливает ранее активное окно и отправляет стандартный `Ctrl+V`.
- Copy fallback остаётся доступным, когда Direct Paste использовать не следует.
- Scope PINNED/RECENT, фильтры типов, фильтр тегов и multi-selection.
- Keyboard-first navigation, видимый focus, accessible names и responsive layout.

### Поиск и типы содержимого

Поддерживаемые операторы:

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

Типы:

```text
TEXT
CODE
URL
PATH
JSON
COMMAND
```

Безопасные действия позволяют открыть URL, показать путь в Explorer, форматировать JSON и копировать code/command. XClip **не выполняет** команды из буфера обмена.

### PINNED и теги

- Ручной порядок PINNED.
- Пользовательские titles для PINNED.
- Создание и назначение тегов одному или нескольким clips.
- Tag chips с ограниченным `+N` overflow.
- Фильтрация, поиск по тегам, rename, delete, usage count и cleanup unused tags.
- Атомарные изменения и case-insensitive identity.

### Privacy и retention

- Исключение foreground-приложений по имени executable.
- Опциональный локальный пропуск Luhn-valid значений, похожих на номер карты.
- Опциональный локальный пропуск contextual OTP длиной 4–8 цифр.
- Age-based cleanup RECENT с отдельными overrides для типов.
- PINNED не участвуют в обычном retention cleanup.
- Опциональный clear RECENT on exit с ограниченным временем shutdown.
- Включение privacy rules не сканирует и не удаляет существующую history автоматически.

### Settings

Settings разделён на девять страниц:

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

Поддерживаются draft state, Apply/Cancel, scoped reset, inline validation, compact/standard/wide responsive modes, keyboard navigation, accessibility и асинхронные data operations.

### Обслуживание и восстановление базы

Settings → Data предоставляет:

- статус и размеры SQLite;
- `PRAGMA integrity_check`;
- явный WAL checkpoint;
- оптимизацию через `VACUUM` и `PRAGMA optimize`;
- ручной retention cleanup;
- clear RECENT и clear ALL;
- создание versioned `.xclip-backup`;
- проверяемое restore с rollback protection.

Backup содержит:

```text
manifest.properties
xclip.db
config.json
```

Перед заменой live-файлов restore проверяет структуру архива, версии схем, конфигурацию и SQLite integrity. После успешного restore XClip завершается, чтобы восстановленное состояние чисто загрузилось при следующем запуске.

### Windows integration и lifecycle

- Работа через system tray.
- Single-instance activation с подтверждаемым loopback protocol.
- Опциональный per-user autostart через `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`.
- Восстановление tray и hotkey после Explorer restart.
- Восстановление watcher и сброс stale Direct Paste target после sleep/resume и lock/unlock.
- Возврат окна на видимый экран после display topology или DPI changes.
- Упорядоченный idempotent shutdown и bounded exit cleanup.
- Per-user MSI со встроенным runtime.

## Архитектура

```text
config  → configuration, paths, migration
data    → SQLite schema, DAO lifecycle, backup/restore
domain  → clipboard, duplicate, search, privacy, retention rules
system  → clipboard access, tray, hotkey, Windows lifecycle
ui      → JavaFX popup, dialogs, Settings
validation → explicit large-data release harness
```

## Локальные данные

Все пользовательские данные хранятся локально:

```text
%USERPROFILE%\.xclip\
```

| Назначение | Файл |
|---|---|
| Clipboard database | `xclip.db` |
| Configuration | `config.json` |
| SQLite sidecars | `xclip.db-wal`, `xclip.db-shm` или `xclip.db-journal`, когда они существуют |

Директория MSI отделена от пользовательских данных. Поведение upgrade/uninstall/reinstall должно быть подтверждено packaged manual validation до релиза.

## Сборка из исходников

```powershell
git clone https://github.com/End1essspace/XClip.git
```

```powershell
cd XClip
```

```powershell
.\gradlew.bat clean test --no-daemon
```

```powershell
.\gradlew.bat build --no-daemon
```

Сборка MSI на Windows:

```powershell
.\gradlew.bat clean packageMsi --no-daemon
```

## Документация

- [Руководство на русском](docs/USER_GUIDE_v1.3.0_RU.md)
- [User guide на английском](docs/USER_GUIDE_v1.3.0.md)
- [Состав documentation refresh](docs/M9_DOCUMENTATION_REFRESH.md)
- [План ручной проверки](docs/M9_MANUAL_VALIDATION_PLAN.md)
- [Черновик release notes](docs/RELEASE_NOTES_v1.3.0_DRAFT.md)
- [UI contract](docs/UI_CONTRACT_v1.3.0.md)
- [История validation](docs/FEATURE_VALIDATION_HISTORY_v1.3.0.md)
- [Settings validation](docs/M6_SETTINGS_VALIDATION.md)
- [Database maintenance и backup/restore](docs/M7_DATABASE_MAINTENANCE.md)
- [Large-data validation](docs/M7_LARGE_DATA_VALIDATION.md)
- [Windows lifecycle hardening](docs/M8_WINDOWS_LIFECYCLE.md)
- [Responsive/performance validation](docs/R10_VALIDATION.md)
- [Full regression/UI freeze](docs/R11_REGRESSION_UI_FREEZE.md)

## Системные требования

- Windows 10 или Windows 11, 64-bit.
- Для packaged application внешняя Java не требуется.
- Для сборки MSI требуются WiX и JDK с `jpackage`.

## Версии

```text
Application version: 1.3.0
Config schema:       5
SQLite schema:       6
UI contract:         18
Backup format:       1
```

## Конфиденциальность

XClip является local-first приложением и не включает telemetry в документированном product contract. Clipboard contents, tags, settings и backups остаются на компьютере пользователя, пока пользователь сам не скопирует или не переместит их.

## Лицензия

XClip распространяется по **GNU GPL-3.0-only**.

Выбранные Lucide SVG icons распространяются по ISC License. См. [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) и packaged license assets.

## Автор

**Rafael Xudoynazarov — End1essspace | RX**

- Telegram: [@End1essspace](https://t.me/End1essspace)
- GitHub: [End1essspace](https://github.com/End1essspace)
