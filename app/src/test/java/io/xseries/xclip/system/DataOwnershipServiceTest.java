/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system;

import io.xseries.xclip.data.dao.ClipEntryDao;
import io.xseries.xclip.data.dao.TagDao;
import io.xseries.xclip.data.db.Database;
import io.xseries.xclip.domain.duplicate.DuplicateContentKeys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataOwnershipServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void clearAllDataClosesDaoConnectionsAndDeletesCompleteSqliteFileSet()
            throws Exception {
        Path dbPath = tempDir.resolve("xclip.db");
        Path configPath = tempDir.resolve("config.json");
        Database database = new Database(dbPath);
        database.init();

        ClipEntryDao clipDao = new ClipEntryDao(database.jdbcUrl());
        TagDao tagDao = new TagDao(database.jdbcUrl());

        DuplicateContentKeys keys = DuplicateContentKeys.from("owned value");
        clipDao.insertNew("owned value", "owned value", keys, 1_000L);
        tagDao.listAll();
        Files.writeString(configPath, "{}");

        Path wal = Path.of(dbPath.toString() + "-wal");
        Path shm = Path.of(dbPath.toString() + "-shm");
        assertTrue(Files.exists(dbPath));

        DataOwnershipService service = new DataOwnershipService(
                database,
                tempDir,
                configPath,
                List.of(tagDao::releaseConnections, clipDao::releaseConnections)
        );

        service.clearAllData();

        assertFalse(Files.exists(dbPath));
        assertFalse(Files.exists(wal));
        assertFalse(Files.exists(shm));
        assertFalse(Files.exists(configPath));
    }
}
