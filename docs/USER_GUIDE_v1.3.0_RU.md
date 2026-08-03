# XClip 1.3.0 — руководство пользователя

Статус: документационная база перед ручной проверкой
Платформа: Windows 10/11 x64
Application: 1.3.0
Config schema: 5
SQLite schema: 6
UI contract: 18

## 1. Назначение

XClip сохраняет скопированный в Windows текст в локальную историю. Приложение предоставляет popup с поиском, PINNED-записи, теги, Direct Paste, безопасные действия по типу содержимого, privacy exclusions, retention и инструменты восстановления базы данных.

XClip не выполняет команды из буфера обмена. Все документированные пользовательские данные остаются в профиле Windows, пока пользователь сам не экспортирует backup или не скопирует файлы.

## 2. Открытие XClip

Popup открывается:

- глобальной комбинацией `Ctrl+Shift+V`;
- левым кликом по tray icon;
- повторным запуском XClip, который активирует primary instance.

Правый клик по tray icon открывает tray menu.

Если Windows сообщает conflict глобальной комбинации, XClip продолжает работать, а статус отображается в Settings → Shortcuts.

## 3. Захват буфера обмена

XClip сохраняет поддерживаемый текст, пока watcher включён.

На capture влияют:

- minimum clip length;
- maximum clip characters;
- duplicate behavior;
- foreground application exclusions;
- sensitive-content rules;
- состояние watcher.

Clipboard writes, созданные самим приложением, подавляются от немедленного повторного захвата.

### Duplicate behavior

Настройки определяют:

- перемещается ли RECENT duplicate наверх;
- сохраняет ли PINNED duplicate ручной порядок;
- нормализуются ли пробелы;
- учитывается ли регистр;
- используется ли finite или unlimited window;
- включён ли exact-content mode.

Смена duplicate policy не требует переписывания истории: база хранит четыре policy-independent equality hashes.

## 4. Popup

Popup содержит:

- title bar и status;
- search field и search assist;
- scope/type/tag filters;
- virtualized rows;
- action controls;
- footer/status information.

Загружается ограниченное количество clips согласно `uiClipLimit`. Большое содержимое показывается через bounded preview.

## 5. PINNED и RECENT

RECENT — обычная история. PINNED — явно закреплённые записи.

Контракт PINNED:

- не удаляются обычной очисткой RECENT;
- не участвуют в age/type retention;
- поддерживают пользовательский title;
- сохраняют manual order;
- сохраняют tag assignments.

## 6. Выбор и действия

Можно использовать мышь и keyboard navigation. Multi-selection поддерживает range и toggle selection.

Действия:

- Copy;
- Direct Paste;
- Pin/Unpin;
- Delete;
- назначение и снятие тегов;
- изменение PINNED title;
- изменение PINNED order;
- безопасное действие по типу.

### Direct Paste

Direct Paste копирует выбранное содержимое, восстанавливает ранее активное окно и отправляет стандартный `Ctrl+V`.

Target очищается после lock, unlock, resume, Explorer recovery и display topology change. Если безопасного target нет, используй Copy и вставь вручную.

## 7. Поиск

Обычный текст ищет в content, PINNED titles и назначенных tag names.

Операторы:

```text
type:text
type:code
type:url
type:path
type:json
type:command
is:pinned
is:recent
tag:work
tag:"Project Work"
-tag:private
-type:text
```

Правила:

- toolbar filters и operators объединяются;
- положительные type operators имеют OR semantics;
- required tags имеют AND semantics;
- negative operators исключают совпадения;
- противоречивый запрос даёт пустой результат;
- ошибки синтаксиса показываются без блокировки UI.

## 8. Типы и безопасные действия

XClip вычисляет тип:

- TEXT;
- CODE;
- URL;
- PATH;
- JSON;
- COMMAND.

Безопасные действия:

- URL → открыть в системном браузере;
- PATH → показать в Explorer;
- JSON → скопировать форматированный JSON;
- CODE → скопировать code;
- COMMAND → скопировать command.

COMMAND никогда не выполняется XClip.

## 9. Теги

Tag identity не зависит от регистра.

Поддерживаются:

- создание;
- назначение одному clip;
- batch assignment/removal;
- фильтрация;
- поиск по имени;
- rename;
- delete;
- usage count;
- cleanup unused.

Изменения выполняются transactionally. Ошибка сохранения не должна оставлять частично применённое batch state.

## 10. Settings

Страницы:

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

### Draft, Apply и Cancel

- Apply сохраняет валидный изменённый draft.
- Cancel отменяет unapplied changes.
- Закрытие Settings использует тот же discard path.
- Ошибки блокируют Apply.
- Validation message переводит на первое invalid field.
- Scoped reset меняет только соответствующую группу.

Settings поддерживает compact, standard и wide layouts, keyboard navigation, accessible names, visible focus и активацию validation мышью, Enter или Space.

## 11. Privacy

### Excluded applications

XClip может пропускать capture, пока foreground window принадлежит указанному executable.

Сравнение выполняется по basename без учёта регистра. Введённый path сокращается до имени executable.

Если Windows не отдаёт process metadata, resolver работает fail-open, чтобы не вызвать тихую потерю обычной history.

### Sensitive-content rules

Оба правила по умолчанию выключены.

Можно пропускать:

- значения, похожие на payment card и прошедшие Luhn;
- 4–8 digit values рядом с явным OTP/verification context.

Проверяются только новые clipboard values. Existing history не сканируется и не удаляется.

## 12. Retention

Retention применяется только к RECENT.

Доступны:

- general maximum age;
- overrides для TEXT, CODE, URL, PATH, JSON и COMMAND;
- clear RECENT on exit;
- manual cleanup;
- startup, Apply, periodic и exit triggers.

Если работают несколько правил, применяется меньший срок. Граница строгая: запись ровно N дней сохраняется, пока не станет старше N дней.

PINNED никогда не участвуют в retention cleanup.

## 13. Data и database maintenance

Settings → Data показывает:

- data directory;
- database/config paths;
- SQLite schema;
- journal mode;
- размеры database/WAL/shared memory;
- page/free-page information.

Действия:

- Refresh status;
- Check integrity;
- Run retention cleanup;
- Checkpoint WAL;
- Optimize database;
- Clear RECENT;
- Create backup;
- Restore backup;
- Clear ALL.

Длительные операции выполняются вне JavaFX Application Thread и временно блокируют конфликтующие действия.

## 14. Backup и restore

### Создание backup

Backup должен сохраняться вне `%USERPROFILE%\.xclip`.

`.xclip-backup` содержит:

```text
manifest.properties
xclip.db
config.json
```

SQLite snapshot создаётся через `VACUUM INTO` и проверяется `PRAGMA integrity_check`.

Включается только persisted config. Несохранённый Settings draft не входит в backup.

### Restore

Перед заменой XClip проверяет:

- точный набор archive entries;
- duplicate/unsafe paths;
- size limits;
- backup format;
- database schema;
- config schema;
- manifest consistency;
- parseability config;
- SQLite integrity.

Restore использует staged files и rollback copies. После успешного restore XClip завершается. Запусти приложение снова.

## 15. Очистка данных

### Clear RECENT

Удаляет non-PINNED, сохраняя:

- PINNED clips;
- PINNED titles/order;
- tag library и связанные assignments;
- configuration/policies.

### Clear ALL

Clear ALL удаляет XClip-owned user data. Перед операцией создай backup.

XClip освобождает database connections и удаляет SQLite sidecars, чтобы не оставить partial state.

## 16. Windows lifecycle

Runtime hardening покрывает:

- Explorer restart;
- sleep/resume;
- lock/unlock;
- monitor connect/disconnect;
- display bounds/DPI changes;
- secondary launch;
- stale autostart path;
- normal/JVM shutdown.

Ожидаемые гарантии: один tray icon, один primary instance, watcher после resume, видимое окно и отсутствие stale Direct Paste target.

Release не считается завершённым, пока реальный MSI не пройдёт 18-case packaged matrix.

## 17. Локальные файлы

```text
%USERPROFILE%\.xclip\
```

Возможные файлы:

```text
xclip.db
xclip.db-wal
xclip.db-shm
xclip.db-journal
config.json
config.bad-<timestamp>.json
```

Не редактируй и не удаляй database files при работающем XClip.

## 18. Решение проблем

### Не работает Ctrl+Shift+V

1. Открой Settings → Shortcuts.
2. Проверь registration status.
3. Закрой программу, занявшую комбинацию.
4. Перезапусти XClip или открывай popup через tray.

### Окно оказалось на другом мониторе

Повтори открытие после изменения display topology. XClip должен вернуть полностью off-screen window в visible bounds, сохранив корректные negative-coordinate layouts.

### Integrity check не проходит

1. Останови destructive data operations.
2. Скопируй `%USERPROFILE%\.xclip` в безопасное место.
3. Не выполняй повторные записи в повреждённую базу.
4. Restore ранее проверенный backup или сохрани файлы для диагностики.

### Backup отклонён

Причиной может быть неожиданный состав ZIP, неподдерживаемая schema, invalid config или failed SQLite integrity. Live data при таком отказе не должны заменяться.

## 19. Статус релиза

Документированные версии:

```text
Application:   1.3.0
Config schema: 5
SQLite schema: 6
UI contract:   18
Backup format: 1
```

До публичного релиза остаются:

- полная ручная функциональная проверка;
- M8 packaged 18-case evidence;
- clean MSI build и installed-app validation;
- upgrade/uninstall/reinstall proof;
- финальные screenshots;
- checksums;
- tagged clean commit;
- final release notes и GitHub Release.
