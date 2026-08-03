/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import io.xseries.xclip.data.db.DatabaseMaintenanceService.BackupDescriptor;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.BackupResult;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.CheckpointMode;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.CheckpointResult;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.DatabaseStatus;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.IntegrityReport;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.RestoreResult;
import io.xseries.xclip.data.db.DatabaseMaintenanceService.VacuumResult;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMaintenanceTextTest {

    @Test
    void formatsDatabaseStatusAndMaintenanceResults() {
        DatabaseStatus status = new DatabaseStatus(
                true,
                2_048L,
                1_024L,
                32_768L,
                6,
                "wal",
                10L,
                2L,
                8_192L
        );

        String statusText = DatabaseMaintenanceText.status(status);
        assertTrue(statusText.contains("Schema 6"));
        assertTrue(statusText.contains("WAL"));
        assertTrue(statusText.contains("reclaimable estimate"));

        assertEquals(
                "Integrity check passed: SQLite reported OK",
                DatabaseMaintenanceText.integrity(
                        new IntegrityReport(true, List.of("ok"))
                )
        );

        assertTrue(DatabaseMaintenanceText.checkpoint(
                new CheckpointResult(CheckpointMode.TRUNCATE, 0, 12, 12)
        ).contains("completed"));

        assertTrue(DatabaseMaintenanceText.vacuum(
                new VacuumResult(10_000L, 4_000L)
        ).contains("reclaimed"));
    }

    @Test
    void formatsBackupAndRestoreMetadata() {
        Path path = Path.of("backup.xclip-backup");

        String backup = DatabaseMaintenanceText.backup(
                new BackupResult(path, 4_096L, 6, 5)
        );
        assertTrue(backup.contains("backup.xclip-backup"));
        assertTrue(backup.contains("DB schema 6"));

        String descriptor = DatabaseMaintenanceText.backupDescriptor(
                new BackupDescriptor(
                        path,
                        1,
                        1_700_000_000_000L,
                        "1.3.0",
                        6,
                        5,
                        4_096L
                )
        );
        assertTrue(descriptor.contains("XClip 1.3.0"));
        assertTrue(descriptor.contains("config schema 5"));

        String restore = DatabaseMaintenanceText.restore(
                new RestoreResult(path, 6, 5)
        );
        assertTrue(restore.contains("Backup restored"));
    }

    @Test
    void formatsByteBoundariesDeterministically() {
        assertEquals("0 B", DatabaseMaintenanceText.formatBytes(-1L));
        assertEquals("1023 B", DatabaseMaintenanceText.formatBytes(1_023L));
        assertEquals("1.00 KB", DatabaseMaintenanceText.formatBytes(1_024L));
        assertEquals("1.00 MB", DatabaseMaintenanceText.formatBytes(1_048_576L));
    }
}
