/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.db;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class Database {

    private final Path dbPath;
    private final String jdbcUrl;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public Database(Path dbPath) {
        this.dbPath = dbPath;
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }

    public Path dbPath() {
        return dbPath;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Initializes database directory, enables WAL,
     * and applies schema.sql in an idempotent way.
     */
    public void init() {
        if (!initialized.compareAndSet(false, true)) {
            return; // already initialized
        }

        if (closed.get()) {
            throw new IllegalStateException("Database is closed and cannot be initialized again.");
        }

        try {
            Files.createDirectories(dbPath.getParent());
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create database directory: " + dbPath.getParent(), e
            );
        }

        // Ensure SQLite driver is loaded
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // modern JDBC auto-loads; ignore
        }

        try (Connection c = DriverManager.getConnection(jdbcUrl);
             Statement st = c.createStatement()) {

            // --- PRAGMA: must be first ---
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=NORMAL;");
            st.execute("PRAGMA foreign_keys=ON;");
            st.execute("PRAGMA temp_store=MEMORY;");
            st.execute("PRAGMA busy_timeout=3000;");

            // --- Apply schema ---
            String ddl = loadResourceText("/db/schema.sql");
            for (String sql : ddl.split(";")) {
                String s = sql.trim();
                if (!s.isEmpty()) {
                    st.execute(s + ";");
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SQLite database", e);
        }
    }

    /**
     * Safe to call multiple times.
     * Currently no persistent connections are held,
     * but this is kept for symmetry and future pooling.
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // No-op for now (connections are per-operation)
        // Reserved for future connection pool or migration locks
    }

    /**
     * Deletes the database file from disk.
     *
     * Notes:
     * - Calls close() first (idempotent).
     * - Since the app uses per-operation connections, there is no single Connection to close here.
     * - Deletion can still fail on Windows if some other process/thread holds the DB file open.
     *   In that case we throw, so caller can show a proper UI message.
     */
    public void deleteDatabaseFile() {
        close();

        try {
            Files.deleteIfExists(dbPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete database file: " + dbPath, e);
        }
    }

    private static String loadResourceText(String path) {
        try (var is = Database.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8)
            )) {
                return br.lines().collect(Collectors.joining("\n"));
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load resource: " + path, e);
        }
    }
}
