package io.xseries.xclip.system;

import io.xseries.xclip.config.AppPaths;
import io.xseries.xclip.data.db.Database;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User-facing data ownership actions:
 * - open data folder
 * - clear ALL data (DB + config)
 *
 * Works with instance-based Database (your architecture).
 */
public final class DataOwnershipService {

    private final Database database;

    public DataOwnershipService(Database database) {
        this.database = database;
    }

    public void openDataFolder() {
        try {
            Path dir = AppPaths.dataDir();
            Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
        } catch (Exception ignored) {
        }
    }

    public void clearAllData() {
        // Delete DB file
        if (database != null) {
            database.deleteDatabaseFile();
        }

        // Delete config.json
        try {
            Files.deleteIfExists(AppPaths.configPath());
        } catch (Exception ignored) {
        }
    }
}
