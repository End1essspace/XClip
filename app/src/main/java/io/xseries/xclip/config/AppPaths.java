/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.config;

import java.nio.file.Path;

/**
 * Single source of truth for the XClip filesystem layout.
 *
 * All user-owned application data lives under one removable directory.
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
