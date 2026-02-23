[ENG]

📋 **XClip**

**XClip** is a production-grade Windows clipboard manager  
built with **Java 17 + JavaFX 21**, designed for performance, reliability, and clean architecture.

Unlike simple clipboard utilities, XClip focuses on engineering quality:
layered architecture, SQLite WAL mode, single-instance control, MSI packaging, and Windows integration.


🚀 **Core Features**

🔄 **Real-Time Clipboard Monitoring**
- Adaptive polling strategy (idle backoff)
- Smart deduplication using SHA-256 hashing
- Protection against clipboard lock issues
- Safe background execution

🔍 **Instant Search with Highlighting**
- Live search across full clipboard history
- Highlighted matching substrings
- Optimized rendering (preview caching)
- Smooth scrolling without UI stutter

📌 **Pin Important Clips**
- Mark entries as favorites
- Favorites are preserved during history pruning
- Clear visual separation of pinned items

🧠 **Multi-Selection Support**
- **Shift** — range selection
- **Ctrl** — toggle selection
- Synchronized selection state
- Batch operations ready

🗂 **Persistent Local Storage**
- SQLite database (WAL mode enabled)
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
- Secondary launch signals primary process
- No duplicate tray icons

⚡ **Windows Autostart**
- Optional autostart via Registry (HKCU Run)
- Proper EXE path detection in packaged mode
- Clean enable/disable logic

📦 **Professional MSI Installer**
- Built with `jlink` (bundled runtime)
- Packaged via `jpackage` + WiX
- Fixed Upgrade UUID
- Proper uninstall support
- Start Menu integration


💎 **Engineering Strengths**

What makes XClip strong technically:

✔ Clean layered architecture
✔ Strict separation of system/domain/data/ui
✔ SQLite WAL mode for concurrent stability
✔ Thread-local database connection reuse
✔ Adaptive clipboard polling
✔ Preview caching to reduce GC pressure
✔ Optimized ListView cell rendering
✔ MSI upgrade-safe packaging
✔ Production-grade release process

This is not a “student demo project” —
it is a properly engineered desktop utility.


🏗 **Architecture Overview**

Layered design:

```
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

```

%USERPROFILE%.xclip\

````

Files:

|     Purpose     |    File     |
|-----------------|-------------|
| Database        | xclip.db    |
| Configuration   | config.json |


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

Current version: **v1.1.0**


👨‍💻 **Author**

**XCON | RX**
Telegram: [@End1essspace](https://t.me/End1essspace)
GitHub: [End1essspace](https://github.com/End1essspace)

🧾 **License**

XClip is licensed under the GNU General Public License v3.0 (GPL-3.0).

You are free to use, modify, and distribute this software under the terms of the GPL v3.
Any distributed modifications must also be licensed under GPL v3 and include source code.


🧾 **Copyright**

Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)



[RUS]

📋 **XClip**

**XClip** — это production-grade менеджер буфера обмена для Windows,  
написанный на **Java 17 + JavaFX 21**, с акцентом на производительность, стабильность и чистую архитектуру.

В отличие от простых clipboard-утилит, XClip построен как инженерный продукт:
слоистая архитектура, SQLite в режиме WAL, защита от двойного запуска, MSI-упаковка и глубокая интеграция с Windows.


🚀 **Основные возможности**

🔄 **Мониторинг буфера обмена в реальном времени**
- Адаптивный polling (умное снижение нагрузки в простое)
- Дедупликация через SHA-256
- Защита от блокировки буфера
- Безопасная работа в фоне

🔍 **Мгновенный поиск с подсветкой**
- Поиск по всей истории
- Подсветка совпадений
- Кэширование preview
- Плавный скролл без лагов

📌 **Закрепление записей**
- Возможность отметить запись как избранную
- Избранные не удаляются при очистке истории
- Визуальное разделение

🧠 **Множественный выбор**
- **Shift** — диапазон
- **Ctrl** — переключение
- Синхронизация состояния выделения

🗂 **Постоянное хранение данных**
- SQLite (режим WAL)
- Индексированные запросы
- Переиспользование соединения
- Автоматическое ограничение истории

🖥 **Системный трей**
- Работа в фоне
- ЛКМ — открыть окно
- ПКМ — меню
- Корректный жизненный цикл

🚫 **Защита от двойного запуска**
- Разрешён только один экземпляр
- Второй запуск активирует первый
- Нет дублирующихся иконок в трее

⚡ **Автозапуск Windows**
- Регистрация в HKCU Run
- Корректное определение EXE в режиме MSI
- Чистое включение/отключение

📦 **Профессиональный MSI-установщик**
- Встроенный runtime (jlink)
- Сборка через jpackage + WiX
- Поддержка обновлений (Upgrade UUID)
- Корректное удаление
- Интеграция в меню Пуск


💎 **Инженерные сильные стороны**

Почему XClip технически сильный проект:

✔ Чёткая слоистая архитектура
✔ Разделение system/domain/data/ui
✔ SQLite в режиме WAL
✔ Переиспользование соединений
✔ Адаптивный polling буфера
✔ Кэширование preview
✔ Оптимизированный ListView
✔ MSI с поддержкой обновлений
✔ Production-подход к релизу

Это не демо-проект,
а полноценно спроектированная desktop-утилита.


🏗 **Архитектура**


```
system  → интеграция с Windows
domain  → бизнес-логика
data    → SQLite
ui      → JavaFX
config  → управление конфигурацией

```

🗃 **Хранение данных**

По умолчанию:

```

%USERPROFILE%.xclip\

```

Файлы:

- xclip.db
- config.json


🔄 **Версионирование**

Текущая версия: **v1.1.0**

👨‍💻 **Автор**

**XCON | RX**
TG: [@End1essspace](https://t.me/End1essspace)
GitHub: [End1essspace](https://github.com/End1essspace)

🧾 **Лицензия**

XClip распространяется под лицензией GNU General Public License версии 3.0 (GPL-3.0).

Вы имеете право использовать, изменять и распространять данное программное обеспечение в соответствии с условиями GPL v3.
Любые распространяемые модифицированные версии также должны быть лицензированы по GPL v3 и сопровождаться исходным кодом.



🧾 **Copyright**

Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)