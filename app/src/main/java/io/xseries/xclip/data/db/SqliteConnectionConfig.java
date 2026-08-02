/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Single source of truth for SQLite connection PRAGMA configuration.
 *
 * journal_mode is database-persistent and is therefore applied only during
 * Database initialization. Connection-local settings are applied to every
 * initialization and DAO connection.
 */
public final class SqliteConnectionConfig {

    public static final int BUSY_TIMEOUT_MILLIS = 3_000;

    private SqliteConnectionConfig() {}

    public static void configureDatabase(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection");

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL;");
            configureConnection(statement);
        }
    }

    public static void configureWorkingConnection(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection");

        try (Statement statement = connection.createStatement()) {
            configureConnection(statement);
        }
    }

    private static void configureConnection(Statement statement) throws SQLException {
        statement.execute("PRAGMA synchronous=NORMAL;");
        statement.execute("PRAGMA foreign_keys=ON;");
        statement.execute("PRAGMA temp_store=MEMORY;");
        statement.execute("PRAGMA busy_timeout=" + BUSY_TIMEOUT_MILLIS + ";");
    }
}
