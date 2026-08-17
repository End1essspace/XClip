<p align="center">
  <img src="app/src/main/resources/icons/icon.png" width="96" alt="XClip logo">
</p>

<h1 align="center">XClip</h1>

<p align="center">
  <strong>A local-first clipboard workspace for Windows.</strong><br>
  Search, pin, tag, organize, and paste persistent clipboard history — without sending it to the cloud.
</p>

<p align="center">
  <a href="https://github.com/End1essspace/XClip/releases"><img src="https://img.shields.io/github/v/release/End1essspace/XClip?display_name=tag" alt="Latest Release"></a>
  <a href="https://github.com/End1essspace/XClip/actions/workflows/ci.yml"><img src="https://github.com/End1essspace/XClip/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue.svg" alt="GPL-3.0"></a>
  <img src="https://img.shields.io/badge/Windows-10%20%7C%2011-0078D4.svg" alt="Windows 10/11">
</p>

<p align="center">
  <a href="https://github.com/End1essspace/XClip/releases"><strong>Download</strong></a>
  · <a href="docs/USER_GUIDE.md">User Guide</a>
  · <a href="docs/RELEASE_NOTES_v1.4.0.md">v1.4.0 Release Notes</a>
  · <a href="#русский">Русский</a>
</p>

<p align="center">
  <img src="docs/screenshots/xclip-popup.png" alt="XClip clipboard workspace" width="100%">
</p>

---

# English

**Current release:** `v1.4.0` · **2026-08-17**

XClip turns `Ctrl+Shift+V` into a fast clipboard workspace for Windows. Clipboard history stays on your machine, while search, PINNED items, tags, type-aware actions, privacy controls, and data recovery make it practical for everyday use.

The packaged MSI includes its own Java runtime, so a separate Java installation is not required.

## Why XClip

- **Find old clipboard content quickly** with text search, structured operators, scopes, types, and tags.
- **Keep important clips organized** with PINNED items, custom titles, manual ordering, and multi-tag assignment.
- **Paste directly back into your previous app** with foreground-target restoration and Copy fallback.
- **Handle content safely** with type-aware actions for URLs, paths, JSON, code, and commands without auto-executing clipboard commands.
- **Keep control of local data** with privacy exclusions, retention rules, SQLite maintenance, backup, and validated restore.
- **Use it like a Windows utility** with tray behavior, global hotkey, autostart, multi-monitor/DPI recovery, and packaged lifecycle hardening.

## Core capabilities

| Area | What XClip provides |
|---|---|
| **Persistent history** | Local SQLite WAL clipboard history with bounded loading and previews. |
| **Direct Paste** | Restores the previously active target and sends standard `Ctrl+V`, with Copy fallback. |
| **PINNED workspace** | Optional titles, manual ordering, and protection from ordinary RECENT cleanup. |
| **Tags** | Create, assign, filter, rename, delete, clean up, and batch-edit local tags. |
| **Advanced search** | Search content, PINNED titles, and tags with structured type/scope/tag operators. |
| **Safe actions** | Open HTTP(S), reveal paths, format JSON, or copy code/commands without executing clipboard commands. |
| **Privacy & retention** | App exclusions, optional sensitive-content suppression, age/type cleanup, and clear-on-exit policy. |
| **Data recovery** | Integrity check, WAL checkpoint, optimize, versioned backup, and rollback-protected validated restore. |

## How it works

```text
Copy text anywhere in Windows
        ↓
XClip stores it locally
        ↓
Ctrl+Shift+V
        ↓
Search / filter / pin / tag / select
        ↓
Paste, Copy, or use a safe type-aware action
```

The popup supports `All`, `Pinned`, and `Recent` scopes, content-type filters, tag filters, multi-selection, keyboard navigation, bounded previews, and contextual actions.

## Advanced search

Supported operators include:

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

Search assistance is contextual and non-blocking: operator chips, suggestions, and diagnostics appear only when useful.

## v1.4.0 highlights

- Persistent SQLite WAL history with configurable duplicate handling.
- Direct Paste with foreground-target restoration and Copy fallback.
- PINNED ordering, optional titles, tags, and batch actions.
- TEXT, CODE, URL, PATH, JSON, and COMMAND classification.
- Advanced search with positive and negative type/tag operators.
- Nine-page Settings architecture with Apply/Cancel, validation, and responsive layout.
- Local privacy exclusions and optional payment-card-like / contextual OTP suppression.
- General and per-type RECENT retention, scheduled cleanup, and clear-on-exit.
- Database integrity, checkpoint, optimize, backup, and rollback-protected restore.
- Responsive popup with bounded previews, virtualization, keyboard-first navigation, and accessible names.
- Calm dark UI with readable action menus, Lucide icons, and a subtle centered `X-SERIES` title-bar wordmark.
- Maximized-window Fitts-law hardening for the physical top-right Close target.
- Wider scrollbar interaction lane while keeping the visible scrollbar slim, including right-edge thumb dragging when maximized.
- Multi-monitor, DPI/topology recovery, tray/hotkey recovery, and ordered shutdown hardening.

## Local-first safety and privacy

XClip is designed around explicit, local behavior:

- Clipboard commands are **never executed automatically**.
- Copied executable or script paths are not launched as commands.
- Sensitive-content detection is local-only and suppression is opt-in.
- Existing history is not silently rescanned or deleted when privacy rules change.
- PINNED clips are excluded from ordinary retention cleanup.
- Full clipboard contents are not exposed through automatic hover tooltips.
- No telemetry or cloud synchronization is part of the documented product contract.

Local user data is stored under:

```text
%USERPROFILE%\.xclip\
```

| Purpose | File |
|---|---|
| Clipboard database | `xclip.db` |
| Configuration | `config.json` |
| SQLite sidecars | `xclip.db-wal`, `xclip.db-shm`, or `xclip.db-journal` when present |

## Windows integration

- Windows 10/11 x64.
- Java 17 + JavaFX 21 desktop application.
- System tray and close-to-background behavior.
- Global `Ctrl+Shift+V` popup shortcut.
- Single-instance activation.
- Optional per-user Start with Windows.
- Explorer tray/hotkey recovery.
- Sleep/resume, lock/unlock, display topology, and DPI recovery.
- Per-user MSI packaging with a bundled runtime.

## Settings and recovery

<p align="center">
  <img src="docs/screenshots/xclip-settings.png" alt="XClip Settings" width="100%">
</p>

Settings pages:

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

**Settings → Data** contains database status, integrity checking, WAL checkpoint, optimize, retention cleanup, destructive clear operations, backup, and validated restore.

## Build from source

Requirements for development: Java 17 and Windows.

```powershell
git clone https://github.com/End1essspace/XClip.git
cd XClip
.\gradlew.bat clean test --no-daemon
.\gradlew.bat build --no-daemon
```

Build the Windows MSI:

```powershell
.\gradlew.bat clean packageMsi --no-daemon
```

The packaged build uses `jpackage` plus a bundled runtime image.

## Documentation

| Document | Purpose |
|---|---|
| [User guide](docs/USER_GUIDE.md) | Complete user-facing behavior and troubleshooting |
| [Russian user guide](docs/USER_GUIDE_RU.md) | Russian user-facing guide |
| [Roadmap](docs/roadmap.md) | v1.4.0 implementation history and current project status |
| [UI contract](docs/UI_CONTRACT.md) | Current human-readable UI/product contract |
| [Validation](docs/VALIDATION.md) | Regression, manual, packaged, and release gates |
| [Database & backup](docs/M7_DATABASE_MAINTENANCE.md) | SQLite maintenance, backup, restore, and rollback contract |
| [Windows lifecycle](docs/M8_WINDOWS_LIFECYCLE.md) | Packaged lifecycle hardening and validation scope |
| [Release notes](docs/RELEASE_NOTES_v1.4.0.md) | XClip v1.4.0 release summary |

Milestone-named M6/M7/M8/R10/R11 documents, CSV matrices, and `UI_CONTRACT_v1.3.0.md` remain in `docs/` as frozen build/release-gate assets.

## Contributing and security

- [Contributing guide](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
- [Issue tracker](https://github.com/End1essspace/XClip/issues)

## Author

**End1essspace | RX**  
Telegram: [@End1essspace](https://t.me/End1essspace)  
GitHub: [End1essspace](https://github.com/End1essspace)

## License

XClip is licensed under the [GNU General Public License v3.0](LICENSE).

Selected Lucide SVG icons are distributed under the ISC License. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)

---

# Русский

**Текущий релиз:** `v1.4.0` · **17.08.2026**

<p align="center">
  <a href="https://github.com/End1essspace/XClip/releases"><strong>Скачать XClip</strong></a>
  · <a href="docs/USER_GUIDE_RU.md">Руководство</a>
  · <a href="docs/RELEASE_NOTES_v1.4.0.md">Описание релиза</a>
</p>

**XClip** превращает `Ctrl+Shift+V` в полноценное локальное рабочее пространство для буфера обмена Windows. История остаётся на компьютере, а поиск, PINNED, теги, безопасные действия, privacy controls и инструменты восстановления помогают быстро находить и повторно использовать нужный контент.

Готовая MSI-сборка включает собственный Java runtime — отдельно устанавливать Java не нужно.

## Зачем XClip

- **Быстро находить старые записи** через обычный поиск, structured operators, scopes, content types и теги.
- **Хранить важное отдельно** через PINNED, titles, ручной порядок и несколько тегов на запись.
- **Вставлять напрямую в предыдущее приложение** через восстановление foreground target и Copy fallback.
- **Безопасно работать с разными типами контента**: URL, PATH, JSON, CODE и COMMAND не выполняются автоматически.
- **Контролировать локальные данные** через exclusions, retention, SQLite maintenance, backup и validated restore.
- **Использовать как нормальную Windows utility**: tray, global hotkey, autostart, multi-monitor/DPI recovery и packaged lifecycle hardening.

## Основные возможности

| Область | Что предоставляет XClip |
|---|---|
| **Persistent history** | Локальная SQLite WAL history с bounded loading и previews. |
| **Direct Paste** | Восстановление предыдущего target window и стандартный `Ctrl+V`, с Copy fallback. |
| **PINNED workspace** | Titles, ручной порядок и защита от обычной RECENT cleanup. |
| **Tags** | Создание, назначение, фильтрация, rename/delete, cleanup и batch editing. |
| **Advanced search** | Поиск по content, PINNED titles и tags с type/scope/tag operators. |
| **Safe actions** | HTTP(S), Explorer, JSON formatting, code/command copy без выполнения clipboard-команд. |
| **Privacy & retention** | App exclusions, optional sensitive suppression, age/type cleanup и clear-on-exit. |
| **Data recovery** | Integrity check, WAL checkpoint, optimize, versioned backup и validated restore. |

## Как это работает

```text
Копируешь текст в Windows
        ↓
XClip сохраняет его локально
        ↓
Ctrl+Shift+V
        ↓
Ищешь / фильтруешь / закрепляешь / тегируешь
        ↓
Paste, Copy или безопасное type-aware действие
```

Popup поддерживает `All`, `Pinned`, `Recent`, фильтры типов и тегов, multi-selection, keyboard navigation, bounded previews и contextual actions.

## Advanced Search

Примеры операторов:

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

Подсказки поиска появляются только когда нужны и не занимают постоянное место в header.

## Что вошло в v1.4.0

- configurable duplicate behavior;
- PINNED titles и manual ordering;
- tags и batch organization;
- TEXT / CODE / URL / PATH / JSON / COMMAND classification;
- structured Advanced Search;
- девятистраничные Settings с Apply/Cancel и validation;
- privacy exclusions и optional sensitive-content suppression;
- общий и per-type retention;
- scheduled cleanup и clear-on-exit;
- integrity/checkpoint/optimize/backup/restore;
- responsive и keyboard-first popup;
- virtualization и bounded previews;
- multi-monitor/DPI/topology recovery;
- tray/hotkey recovery;
- Windows lifecycle hardening;
- bundled-runtime MSI.

## Безопасность и приватность

- XClip **никогда автоматически не выполняет clipboard commands**.
- Пути к executable/script не запускаются как команды.
- Sensitive-content detection выполняется локально и включается пользователем.
- Изменение privacy rules не приводит к скрытому пересканированию или удалению существующей history.
- PINNED не участвуют в обычном retention cleanup.
- Полное содержимое clipboard не показывается через автоматические hover tooltips.
- Telemetry и cloud sync не входят в documented product contract.

Локальные данные:

```text
%USERPROFILE%\.xclip\
```

## Windows integration

- Windows 10/11 x64;
- tray и close-to-background;
- global `Ctrl+Shift+V`;
- single-instance activation;
- optional Start with Windows;
- Explorer tray/hotkey recovery;
- sleep/resume, lock/unlock, topology и DPI recovery;
- per-user MSI с bundled runtime.

## Settings

<p align="center">
  <img src="docs/screenshots/xclip-settings.png" alt="XClip Settings" width="100%">
</p>

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

**Settings → Data** содержит database status, integrity check, WAL checkpoint, optimize, retention cleanup, destructive clear operations, backup и validated restore.

## Сборка из исходников

```powershell
git clone https://github.com/End1essspace/XClip.git
cd XClip
.\gradlew.bat clean test --no-daemon
.\gradlew.bat build --no-daemon
```

MSI:

```powershell
.\gradlew.bat clean packageMsi --no-daemon
```

## Документация

- [Руководство пользователя](docs/USER_GUIDE_RU.md)
- [User Guide](docs/USER_GUIDE.md)
- [Roadmap](docs/roadmap.md)
- [UI contract](docs/UI_CONTRACT.md)
- [Validation / release gate](docs/VALIDATION.md)
- [Database & backup](docs/M7_DATABASE_MAINTENANCE.md)
- [Windows lifecycle](docs/M8_WINDOWS_LIFECYCLE.md)
- [Release notes v1.4.0](docs/RELEASE_NOTES_v1.4.0.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)

Технические M6/M7/M8/R10/R11 документы, CSV-матрицы и `UI_CONTRACT_v1.3.0.md` остаются отдельными frozen build/release-gate assets.

## Автор и лицензия

**End1essspace | RX** · [@End1essspace](https://t.me/End1essspace) · [GitHub](https://github.com/End1essspace)

XClip распространяется по лицензии [GNU GPL v3.0](LICENSE).
