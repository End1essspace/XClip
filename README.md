

[ENG]

📋 **XClip**

**XClip** is a production-grade Windows clipboard manager built with **Java 17 + JavaFX 21**, designed for performance, reliability, and clean architecture.

Unlike simple clipboard utilities, XClip focuses on engineering quality: layered architecture, SQLite WAL mode, single-instance control, MSI packaging, and Windows integration.


🚀 **Core Features**

🔄 **Real-Time Clipboard Monitoring**
- Adaptive polling strategy with idle backoff
- Smart deduplication using SHA-256 hashing
- Protection against clipboard lock issues
- Safe background execution

🔍 **Instant Search with Highlighting**
- Live search across full clipboard history
- Highlighted matching substrings
- Optimized rendering with preview caching
- Smooth scrolling without UI stutter

📌 **Pin Important Clips**
- Mark entries as favorites
- Favorites are preserved during history pruning
- Clear visual separation of pinned items

🏷 **Create and Assign Tags**
- Create tags directly from the popup Actions menu
- Assign or remove tags for one clip or a multi-selection
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


🖥 **System Requirements**

* Windows 10 / 11 (64-bit)
* No external Java installation required


🔄 **Versioning**

Current version: **v1.3.0**


👨‍💻 **Author**

**XCON | RX**

Telegram: [@End1essspace](https://t.me/End1essspace)

GitHub: [End1essspace](https://github.com/End1essspace)


🧾 **License**

XClip is licensed under the GNU General Public License v3.0 (GPL-3.0).

You are free to use, modify, and distribute this software under the terms of the GPL v3. Any distributed modifications must also be licensed under GPL v3 and include source code.

Selected UI icons are provided by the Lucide project under the ISC License. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).


🧾 **Copyright**

Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)

---

[RUS]

📋 **XClip**

**XClip** — это production-grade менеджер буфера обмена для Windows, написанный на **Java 17 + JavaFX 21**, с акцентом на производительность, надёжность и чистую архитектуру.

В отличие от простых clipboard-утилит, XClip построен как инженерный продукт: слоистая архитектура, SQLite в режиме WAL, защита от двойного запуска, MSI-упаковка и интеграция с Windows.


🚀 **Основные возможности**

🔄 **Мониторинг буфера обмена в реальном времени**
- Адаптивный polling с умным снижением нагрузки в простое
- Дедупликация через SHA-256
- Защита от проблем с блокировкой буфера обмена
- Безопасная работа в фоне

🔍 **Мгновенный поиск с подсветкой**
- Живой поиск по всей истории буфера обмена
- Подсветка найденных совпадений
- Оптимизированный рендеринг с кэшированием preview
- Плавный скролл без подвисаний интерфейса

📌 **Закрепление важных клипов**
- Возможность отмечать записи как избранные
- Избранные записи сохраняются при очистке и ограничении истории
- Чёткое визуальное разделение закреплённых элементов

🏷 **Создание и назначение тегов**
- Создание тегов прямо через меню Actions в popup
- Назначение и снятие тегов для одного клипа или множественного выбора
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


🖥 **Системные требования**

* Windows 10 / 11 (64-bit)
* Внешняя установка Java не требуется


🔄 **Версионирование**

Текущая версия: **v1.3.0**


👨‍💻 **Автор**

**XCON | RX**

Telegram: [@End1essspace](https://t.me/End1essspace)

GitHub: [End1essspace](https://github.com/End1essspace)


🧾 **Лицензия**

XClip распространяется под лицензией GNU General Public License v3.0 (GPL-3.0).

Вы можете использовать, изменять и распространять это программное обеспечение в соответствии с условиями GPL v3. Любые распространяемые модифицированные версии также должны быть лицензированы под GPL v3 и сопровождаться исходным кодом.

Часть UI-иконок предоставлена проектом Lucide по лицензии ISC. Подробности: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).


🧾 **Copyright**

Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)



