/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system;

import io.xseries.xclip.config.AppPaths;
import io.xseries.xclip.data.db.Database;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * User-facing ownership actions for XClip's local data.
 *
 * DAO connections are released before SQLite files are deleted. The DAO
 * objects remain reusable when deletion fails, allowing Settings to restore
 * the running application and let the user retry after removing the lock.
 */
public final class DataOwnershipService {

    private final Database database;
    private final Path dataDir;
    private final Path configPath;
    private final List<Runnable> databaseConnectionReleasers;

    public DataOwnershipService(Database database) {
        this(database, new Runnable[0]);
    }

    public DataOwnershipService(
            Database database,
            Runnable... databaseConnectionReleasers
    ) {
        this(
                database,
                AppPaths.dataDir(),
                AppPaths.configPath(),
                databaseConnectionReleasers == null
                        ? List.of()
                        : Arrays.stream(databaseConnectionReleasers)
                                .filter(Objects::nonNull)
                                .toList()
        );
    }

    DataOwnershipService(
            Database database,
            Path dataDir,
            Path configPath,
            List<Runnable> databaseConnectionReleasers
    ) {
        this.database = Objects.requireNonNull(database, "database");
        this.dataDir = Objects.requireNonNull(dataDir, "dataDir");
        this.configPath = Objects.requireNonNull(configPath, "configPath");
        this.databaseConnectionReleasers = List.copyOf(
                Objects.requireNonNullElse(databaseConnectionReleasers, List.of())
        );
    }

    public void openDataFolder() {
        try {
            Files.createDirectories(dataDir);
            Desktop.getDesktop().open(dataDir.toFile());
        } catch (Exception ignored) {
        }
    }

    public void clearAllData() {
        releaseDatabaseConnections();

        // Delete configuration first. If SQLite deletion is blocked by another
        // process, the still-running app can reopen its intact database and the
        // missing config will be recreated safely on the next launch.
        try {
            Files.deleteIfExists(configPath);
        } catch (Exception error) {
            throw new RuntimeException(
                    "Failed to delete configuration file: " + configPath,
                    error
            );
        }

        database.deleteDatabaseFile();
    }

    private void releaseDatabaseConnections() {
        RuntimeException failure = null;

        for (Runnable releaser : databaseConnectionReleasers) {
            try {
                releaser.run();
            } catch (RuntimeException error) {
                if (failure == null) {
                    failure = new RuntimeException(
                            "Failed to release a database connection owner",
                            error
                    );
                } else {
                    failure.addSuppressed(error);
                }
            }
        }

        if (failure != null) throw failure;
    }
}
