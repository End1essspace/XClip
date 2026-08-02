/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Shared connection lifecycle for SQLite DAO implementations.
 *
 * Every DAO owns its own context, preserving the existing one-connection-per-
 * thread-and-DAO semantics. The class centralizes only repeated mechanical
 * setup and transaction cleanup; SQL ownership remains inside each DAO.
 */
final class DaoConnectionContext {

    private final String jdbcUrl;
    private final ThreadLocal<Connection> threadConnection =
            ThreadLocal.withInitial(this::openConnection);

    DaoConnectionContext(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    Connection connection() {
        try {
            Connection connection = threadConnection.get();
            if (connection == null || connection.isClosed()) {
                connection = openConnection();
                threadConnection.set(connection);
            }
            return connection;
        } catch (Exception error) {
            throw new RuntimeException("Failed to obtain SQLite connection", error);
        }
    }

    boolean beginTransaction(Connection connection, String failureMessage) {
        try {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            return previousAutoCommit;
        } catch (SQLException error) {
            throw new RuntimeException(failureMessage, error);
        }
    }

    void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (Exception ignored) {
        }
    }

    void restoreAutoCommit(Connection connection, boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (Exception ignored) {
        }
    }

    void closeForCurrentThread() {
        try {
            Connection connection = threadConnection.get();
            if (connection != null) connection.close();
        } catch (Exception ignored) {
        } finally {
            threadConnection.remove();
        }
    }

    private Connection openConnection() {
        try {
            Connection connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON;");
                statement.execute("PRAGMA busy_timeout=3000;");
            }
            return connection;
        } catch (Exception error) {
            throw new RuntimeException(
                    "Failed to open SQLite connection: " + jdbcUrl,
                    error
            );
        }
    }
}
