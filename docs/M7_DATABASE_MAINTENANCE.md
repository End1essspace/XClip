# XClip M7.2 Database Maintenance Validation

**Application:** XClip v1.3.0
**Database schema:** 6
**Config schema:** 5
**UI contract:** revision 16
**Gate:** `m7DatabaseGate`

## Scope

M7.2 adds explicit, local-only database diagnostics and recovery operations to
Settings → Data:

- database size, schema, journal mode, WAL/SHM size, and reclaimable-page estimate;
- `PRAGMA integrity_check`;
- explicit `PRAGMA wal_checkpoint(TRUNCATE)`;
- explicit `VACUUM` plus `PRAGMA optimize`;
- portable `.xclip-backup` creation;
- validated backup restore followed by application exit;
- transactional migration rollback and retry;
- future database schema rejection before base-schema mutation;
- recovery from an interrupted, partially applied legacy migration.

Every potentially long or exclusive operation runs outside the JavaFX
Application Thread. Clipboard capture and retention cleanup are paused while
checkpoint, vacuum, backup, or restore owns the database.

## Backup format

A backup is a ZIP-compatible file with the `.xclip-backup` extension. It contains
exactly three root entries:

```text
manifest.properties
xclip.db
config.json
```

`xclip.db` is created with SQLite `VACUUM INTO`; live `-wal`, `-shm`, and
`-journal` sidecars are never copied into the archive.

The manifest records:

- backup format version;
- creation timestamp;
- product version;
- SQLite schema version;
- config schema version;
- canonical entry names.

Archive validation rejects:

- missing or additional entries;
- duplicate entries;
- directory or path-traversal entries;
- oversized manifest/config/database entries;
- unsupported backup format;
- manifest/schema mismatch;
- future database or config schemas;
- invalid configuration;
- failed SQLite `integrity_check`.

## Restore contract

Restore is intentionally terminal:

1. validate the complete archive without touching live data;
2. pause watcher and cleanup;
3. release DAO-owned connections;
4. truncate-checkpoint the live WAL;
5. stage restored files inside the XClip data directory;
6. move current database/config to rollback paths;
7. atomically replace live files where supported;
8. run `integrity_check` and schema verification on the installed database;
9. restore rollback files if installation fails, without deleting originals that were never replaced;
10. exit XClip after success.

The next launch reconstructs all runtime state from the restored database and
configuration.

## Migration contract

Database initialization now performs base-schema application and every migration
step inside one SQLite transaction. An injected failure before commit must roll
back schema changes and reset the in-memory initialization guard so the same
`Database` instance can retry.

A database with `PRAGMA user_version` above the supported schema is rejected
before XClip creates or alters tables.

A legacy database that contains only part of a historical migration is repaired
idempotently by the normal migration path.

## Automated validation

```powershell
.\gradlew.bat test --tests "io.xseries.xclip.data.db.*" --tests "io.xseries.xclip.system.DataOwnershipServiceTest" --tests "io.xseries.xclip.ui.settings.DatabaseMaintenanceTextTest" --tests "io.xseries.xclip.ui.UiDialogsTest" --tests "io.xseries.xclip.ui.UiContractFreezeTest" --no-daemon
```

```powershell
.\gradlew.bat clean m7DatabaseGate --no-daemon
```

```powershell
.\gradlew.bat build --no-daemon
```

```powershell
git diff --check
```

## Manual validation

1. Open Settings → Data and verify that status loads without freezing the UI.
2. Run integrity check and confirm an `OK` result.
3. Copy several clips, run WAL checkpoint, and verify the status text.
4. Create/decode enough history to grow the database, then run Optimize.
5. Confirm the popup, watcher, search, tags, and PINNED history still work.
6. Create a backup outside `%USERPROFILE%\.xclip`.
7. Add a recognizable new clip and change one setting after the backup.
8. Select Restore backup and inspect the metadata confirmation.
9. Cancel restore once and confirm no data changes.
10. Restore again, allow XClip to exit, and restart it.
11. Confirm the backed-up clip, tags, PINNED state, and settings returned.
12. Confirm data added after the backup is absent.
13. Try selecting a renamed or damaged archive and verify restore is refused.
14. Verify Apply/Cancel and window closing remain blocked while an exclusive
    database operation is running.
15. Re-run integrity check after restore.
