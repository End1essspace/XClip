/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import io.xseries.xclip.data.db.DatabaseMaintenanceService.BackupDescriptor;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.BackupResult;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.CheckpointResult;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.DatabaseStatus;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.IntegrityReport;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.RestoreResult;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.VacuumResult;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure presentation text for the Settings Data page.
 */
public final class DatabaseMaintenanceText {

    private static final DateTimeFormatter BACKUP_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private DatabaseMaintenanceText() {}

    public static String status(DatabaseStatus status) {
        DatabaseStatus value = Objects.requireNonNull(status, "status");
        if (!value.databasePresent()) {
            return "Database file is missing. Restart XClip to recreate local storage.";
        }

        return "Schema " + value.schemaVersion()
                + " · " + value.journalMode().toUpperCase(Locale.ROOT)
                + " · " + formatBytes(value.totalBytes()) + " total\n"
                + "Database " + formatBytes(value.databaseBytes())
                + " · WAL " + formatBytes(value.walBytes())
                + " · SHM " + formatBytes(value.sharedMemoryBytes())
                + " · reclaimable estimate "
                + formatBytes(value.estimatedReclaimableBytes());
    }

    public static String integrity(IntegrityReport report) {
        IntegrityReport value = Objects.requireNonNull(report, "report");
        return value.ok()
                ? "Integrity check passed: SQLite reported OK"
                : "Integrity check failed: " + value.summary();
    }

    public static String checkpoint(CheckpointResult result) {
        CheckpointResult value = Objects.requireNonNull(result, "result");
        if (!value.complete()) {
            return "WAL checkpoint is busy: " + value.busyConnections()
                    + " connection(s), " + value.checkpointedFrames()
                    + " of " + value.logFrames() + " frames checkpointed";
        }
        return "WAL checkpoint completed: " + value.checkpointedFrames()
                + " of " + value.logFrames() + " frames, mode "
                + value.mode().name();
    }

    public static String vacuum(VacuumResult result) {
        VacuumResult value = Objects.requireNonNull(result, "result");
        return "Database optimized: " + formatBytes(value.bytesBefore())
                + " → " + formatBytes(value.bytesAfter())
                + " (" + formatBytes(value.reclaimedBytes()) + " reclaimed)";
    }

    public static String backup(BackupResult result) {
        BackupResult value = Objects.requireNonNull(result, "result");
        return "Backup created: " + fileName(value.path())
                + " · " + formatBytes(value.archiveBytes())
                + " · DB schema " + value.databaseSchemaVersion()
                + " · config schema " + value.configSchemaVersion();
    }

    public static String backupDescriptor(BackupDescriptor descriptor) {
        BackupDescriptor value = Objects.requireNonNull(descriptor, "descriptor");
        return "Created " + BACKUP_TIME.format(
                Instant.ofEpochMilli(value.createdAtEpochMillis())
        )
                + " · XClip " + value.productVersion()
                + " · DB schema " + value.databaseSchemaVersion()
                + " · config schema " + value.configSchemaVersion()
                + " · " + formatBytes(value.archiveBytes());
    }

    public static String restore(RestoreResult result) {
        RestoreResult value = Objects.requireNonNull(result, "result");
        return "Backup restored: " + fileName(value.source())
                + " · DB schema " + value.databaseSchemaVersion()
                + " · config schema " + value.configSchemaVersion();
    }

    public static String formatBytes(long bytes) {
        long safe = Math.max(0L, bytes);
        if (safe < 1_024L) return safe + " B";

        double value = safe;
        String[] units = {"KB", "MB", "GB", "TB"};
        for (String unit : units) {
            value /= 1_024.0;
            if (value < 1_024.0 || unit.equals("TB")) {
                return value >= 100.0
                        ? String.format(Locale.ROOT, "%.0f %s", value, unit)
                        : value >= 10.0
                        ? String.format(Locale.ROOT, "%.1f %s", value, unit)
                        : String.format(Locale.ROOT, "%.2f %s", value, unit);
            }
        }
        return safe + " B";
    }

    private static String fileName(Path path) {
        Path value = Objects.requireNonNull(path, "path");
        Path name = value.getFileName();
        return name == null ? value.toString() : name.toString();
    }
}
