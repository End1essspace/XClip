package io.xseries.xclip.config;

import java.nio.file.Path;

/**
 * Single source of truth for XClip filesystem layout.
 *
 * v1.0 contract:
 * - All user data lives in one folder and can be removed safely.
 */
public final class AppPaths {

    private static final String APP_DIR = ".xclip";

    private AppPaths() {}

    public static Path dataDir() {
        return Path.of(System.getProperty("user.home"), APP_DIR);
    }

    public static Path dbPath() {
        return dataDir().resolve("xclip.db");
    }

    public static Path configPath() {
        return dataDir().resolve("config.json");
    }
}
