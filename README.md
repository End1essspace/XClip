

[ENG]

📋 **XClip**

**XClip** is a production-grade Windows clipboard manager built with **Java 17 + JavaFX 21**, designed for performance, reliability, and clean architecture.

Unlike simple clipboard utilities, XClip focuses on engineering quality: layered architecture, SQLite WAL mode, single-instance control, MSI packaging, and Windows integration.


🚀 **Core Features**

🔄 **Real-Time Clipboard Monitoring**
- Adaptive polling strategy with idle backoff
- Smart deduplication using SHA-256 hashing
- Configurable duplicate runtime covers RECENT/PINNED positioning, whitespace, case, finite time windows, and exact-content matching
- Dedicated Settings controls expose RECENT/PINNED positioning, whitespace, case, duplicate windows, exact-content mode, and one-click reset to safe defaults
- Four indexed equality hashes allow policy changes without rewriting clipboard history
- Protection against clipboard lock issues
- Safe background execution

🔐 **Foreground Application Exclusions**
- Skip clipboard capture while a listed executable owns the foreground window
- Case-insensitive matching by executable basename
- Local-only persisted exclusion list in Settings
- Fail-open resolver behavior prevents silent data loss when Windows process metadata is unavailable

🛡 **Sensitive Content Rules**
- Optional local-only suppression for Luhn-valid payment-card-like values
- Optional suppression for contextual 4–8 digit OTP and verification codes
- Safe defaults capture normally; every blocking rule requires explicit opt-in
- Existing clipboard history is never scanned or deleted automatically

🧹 **History Retention & Cleanup**
- Optional age-based cleanup for RECENT history with a general day limit
- Independent day overrides for TEXT, CODE, URL, PATH, JSON, and COMMAND clips
- PINNED clips are always preserved; the shorter applicable age rule wins
- Optional clear-RECENT-on-exit plus startup, manual, Apply, and periodic cleanup status

🔍 **Instant Search with Highlighting**
- Live search across full clipboard history
- Advanced operators: `type:`, `is:`, `tag:`, `-type:`, and `-tag:`
- Quoted tag values such as `tag:"Project Work"`
- Inline syntax hints, contextual suggestions, active operator chips, and non-blocking query diagnostics
- Operators combine with toolbar scope, type, and tag filters
- Highlighted matching substrings from the pure-text part of the query
- Optimized rendering with preview caching
- Smooth scrolling without UI stutter

📌 **Pin Important Clips**
- Mark entries as favorites
- Favorites are preserved during history pruning
- Clear visual separation of pinned items

🏷 **Create, Display, and Filter Tags**
- Create tags directly from the popup Actions menu
- Assign or remove tags for one clip or a multi-selection
- Compact tag chips with bounded `+N` overflow in clipboard rows
- Filter clips by tag and search assigned tag names
- Manage the full tag library with usage counts, rename, confirmed delete, and unused cleanup
- Tri-state batch editing preserves mixed assignments
- Case-insensitive duplicate prevention and atomic database saves

🧠 **Multi-Selection Support**
- **Shift** — range selection
- **Ctrl** — toggle selection
- Synchronized selection state
- Batch operations ready

🗂 **Persistent Local Storage**
- SQLite database with WAL mode enabled
- Indexed queries
- Connection reuse for performance
- Automatic history limit pruning

🖥 **System Tray Integration**
- Background operation
- Left-click — open popup
- Right-click — context menu
- Proper lifecycle management

🚫 **Single-Instance Protection**
- Prevents multiple instances
- Secondary launch signals the primary process
- No duplicate tray icons

⚡ **Windows Autostart**
- Optional autostart via Registry (HKCU Run)
- Proper EXE path detection in packaged mode
- Clean enable/disable logic

📦 **Professional MSI Installer**
- Built with `jlink` and a bundled runtime
- Packaged via `jpackage` + WiX
- Fixed Upgrade UUID
- Proper uninstall support
- Start Menu integration

🎨 **Dark Production UI**
- Full dark JavaFX theme
- Native dark Windows title bars
- Dark confirmation dialogs
- Polished popup rows, section headers, and action footer
- Custom dark tray context menu


🏗 **Architecture Overview**

Layered design:

```text
system  → Windows integration (tray, hotkeys, autostart)
domain  → business logic (ingest, filtering, limits)
data    → SQLite persistence (DAO layer)
ui      → JavaFX presentation
config  → runtime configuration management
```

Designed for maintainability and scalability.


🗃 **Data Storage**

All data is stored locally.

Default location:

```text
%USERPROFILE%\.xclip\
```

Files:

| Purpose       | File        |
|---------------|-------------|
| Database      | xclip.db    |
| Configuration | config.json |


🧩 **Build from Source**

```bash
git clone https://github.com/End1essspace/XClip.git
cd XClip
gradlew build
```

To build MSI installer:

```bash
gradlew clean packageMsi
```


📚 **Engineering Documentation**

- [UI contract](docs/UI_CONTRACT_v1.3.0.md)
- [Feature validation history](docs/FEATURE_VALIDATION_HISTORY_v1.3.0.md)
- [Responsive and performance validation](docs/R10_VALIDATION.md)
- [Full regression and UI freeze](docs/R11_REGRESSION_UI_FREEZE.md)
- [Database maintenance and recovery](docs/M7_DATABASE_MAINTENANCE.md)
- [Large-data validation](docs/M7_LARGE_DATA_VALIDATION.md)


🖥 **System Requirements**

* Windows 10 / 11 (64-bit)
* No external Java installation required


🔄 **Versioning**

Current version: **v1.3.0**


👨‍💻 **Author**

**End1essspace | RX**

Telegram: [@End1essspace](https://t.me/End1essspace)

GitHub: [End1essspace](https://github.com/End1essspace)


🧾 **License**

XClip is licensed under the GNU General Public License v3.0 (GPL-3.0).

You are free to use, modify, and distribute this software under the terms of the GPL v3. Any distributed modifications must also be licensed under GPL v3 and include source code.

Selected UI icons are provided by the Lucide project under the ISC License. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).


🧾 **Copyright**

Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)

---

[RUS]

📋 **XClip**

**XClip** — это production-grade менеджер буфера обмена для Windows, написанный на **Java 17 + JavaFX 21**, с акцентом на производительность, надёжность и чистую архитектуру.

В отличие от простых clipboard-утилит, XClip построен как инженерный продукт: слоистая архитектура, SQLite в режиме WAL, защита от двойного запуска, MSI-упаковка и интеграция с Windows.


🚀 **Основные возможности**

🔄 **Мониторинг буфера обмена в реальном времени**
- Адаптивный polling с умным снижением нагрузки в простое
- Дедупликация через SHA-256
- Настраиваемый duplicate runtime управляет позициями RECENT/PINNED, пробелами, регистром, time window и exact-content matching
- Отдельный раздел Settings управляет позициями RECENT/PINNED, пробелами, регистром, duplicate window, exact-content mode и сбросом к безопасным defaults
- Четыре индексированных equality hash позволяют менять policy без перезаписи истории
- Защита от проблем с блокировкой буфера обмена
- Безопасная работа в фоне

🔐 **Исключения приложений по foreground-процессу**
- Пропуск clipboard capture, пока foreground-окно принадлежит указанному executable
- Сопоставление имени процесса без учёта регистра
- Локальный persistent-список исключений в Settings
- Fail-open resolver не допускает тихой потери данных, если Windows не отдал сведения о процессе

🛡 **Правила чувствительного содержимого**
- Опциональный локальный пропуск значений, похожих на номер платёжной карты и прошедших Luhn-проверку
- Опциональный пропуск 4–8-значных OTP-кодов только при явном контексте подтверждения
- Безопасные defaults сохраняют обычный capture; блокировка включается только явно
- Существующая clipboard history автоматически не сканируется и не удаляется

🧹 **Retention и очистка истории**
- Опциональная возрастная очистка RECENT по общему лимиту дней
- Отдельные day overrides для TEXT, CODE, URL, PATH, JSON и COMMAND
- PINNED всегда сохраняются; при совпадении правил применяется меньший срок
- Опциональная очистка RECENT при выходе, ручной запуск и статус startup/Apply/periodic cleanup

🔍 **Мгновенный поиск с подсветкой**
- Живой поиск по всей истории буфера обмена
- Расширенные операторы: `type:`, `is:`, `tag:`, `-type:` и `-tag:`
- Значения тегов в кавычках, например `tag:"Project Work"`
- Inline-подсказки синтаксиса, контекстные suggestions, chips активных операторов и неблокирующие diagnostics
- Операторы объединяются с toolbar-фильтрами scope, type и tag
- Подсветка совпадений из обычной текстовой части запроса
- Оптимизированный рендеринг с кэшированием preview
- Плавный скролл без подвисаний интерфейса

📌 **Закрепление важных клипов**
- Возможность отмечать записи как избранные
- Избранные записи сохраняются при очистке и ограничении истории
- Чёткое визуальное разделение закреплённых элементов

🏷 **Создание, отображение и фильтрация тегов**
- Создание тегов прямо через меню Actions в popup
- Назначение и снятие тегов для одного клипа или множественного выбора
- Компактные tag chips с ограниченным отображением и `+N` overflow
- Фильтрация клипов по тегу и поиск по именам назначенных тегов
- Управление всей библиотекой тегов: usage count, переименование, подтверждаемое удаление и очистка неиспользуемых тегов
- Tri-state пакетное редактирование сохраняет смешанные назначения
- Защита от дубликатов без учёта регистра и атомарное сохранение в БД

🧠 **Поддержка множественного выбора**
- **Shift** — выбор диапазона
- **Ctrl** — переключение выбора отдельной записи
- Синхронизированное состояние выделения
- Основа для пакетных операций

🗂 **Постоянное локальное хранение**
- SQLite database с включённым WAL mode
- Индексированные запросы
- Переиспользование соединений для производительности
- Автоматическое ограничение размера истории

🖥 **Интеграция с системным треем**
- Работа в фоне
- ЛКМ — открыть popup
- ПКМ — контекстное меню
- Корректное управление жизненным циклом приложения

🚫 **Защита от двойного запуска**
- Запрещает запуск нескольких экземпляров
- Повторный запуск активирует уже работающий процесс
- Нет дублирующихся иконок в трее

⚡ **Автозапуск Windows**
- Опциональный автозапуск через Registry (HKCU Run)
- Корректное определение EXE в packaged mode
- Чистая логика включения и отключения

📦 **Профессиональный MSI-установщик**
- Сборка через `jlink` со встроенным runtime
- Упаковка через `jpackage` + WiX
- Фиксированный Upgrade UUID
- Корректное удаление приложения
- Интеграция в меню Пуск

🎨 **Тёмный production UI**
- Полная тёмная JavaFX-тема
- Нативные тёмные Windows-заголовки окон
- Тёмные окна подтверждения
- Полировка строк popup, заголовков секций и нижней панели действий
- Кастомное тёмное меню системного трея


🏗 **Обзор архитектуры**

Слоистая структура:

```text
system  → интеграция с Windows (tray, hotkeys, autostart)
domain  → бизнес-логика (ingest, filtering, limits)
data    → SQLite-хранение (DAO layer)
ui      → JavaFX presentation
config  → управление runtime-конфигурацией
```

Архитектура рассчитана на поддерживаемость и дальнейшее развитие.


🗃 **Хранение данных**

Все данные хранятся локально.

Путь по умолчанию:

```text
%USERPROFILE%\.xclip\
```

Файлы:

| Назначение   | Файл        |
|--------------|-------------|
| База данных  | xclip.db    |
| Конфигурация | config.json |


🧩 **Сборка из исходников**

```bash
git clone https://github.com/End1essspace/XClip.git
cd XClip
gradlew build
```

Сборка MSI-установщика:

```bash
gradlew clean packageMsi
```


📚 **Инженерная документация**

- [UI contract](docs/UI_CONTRACT_v1.3.0.md)
- [История feature validation](docs/FEATURE_VALIDATION_HISTORY_v1.3.0.md)
- [Responsive и performance validation](docs/R10_VALIDATION.md)
- [Full regression и UI freeze](docs/R11_REGRESSION_UI_FREEZE.md)
- [Обслуживание и восстановление базы данных](docs/M7_DATABASE_MAINTENANCE.md)
- [Проверка на больших объёмах данных](docs/M7_LARGE_DATA_VALIDATION.md)


🖥 **Системные требования**

* Windows 10 / 11 (64-bit)
* Внешняя установка Java не требуется


🔄 **Версионирование**

Текущая версия: **v1.3.0**


👨‍💻 **Автор**

**End1essspace | RX**

Telegram: [@End1essspace](https://t.me/End1essspace)

GitHub: [End1essspace](https://github.com/End1essspace)


🧾 **Лицензия**

XClip распространяется под лицензией GNU General Public License v3.0 (GPL-3.0).

Вы можете использовать, изменять и распространять это программное обеспечение в соответствии с условиями GPL v3. Любые распространяемые модифицированные версии также должны быть лицензированы под GPL v3 и сопровождаться исходным кодом.

Часть UI-иконок предоставлена проектом Lucide по лицензии ISC. Подробности: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).


🧾 **Copyright**

Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
