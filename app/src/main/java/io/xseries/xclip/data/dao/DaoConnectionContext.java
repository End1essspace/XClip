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
import java.util.Objects;

/**
 * Shared connection and transaction lifecycle for SQLite DAO implementations.
 *
 * Every DAO owns its own context, preserving the existing one-connection-per-
 * thread-and-DAO semantics. SQL ownership remains inside each DAO while this
 * class guarantees rollback, auto-commit restoration, and unusable-connection
 * eviction for every multi-statement write transaction.
 */
final class DaoConnectionContext {

    private final String jdbcUrl;
    private final ConnectionFactory connectionFactory;
    private final ThreadLocal<Connection> threadConnection = new ThreadLocal<>();

    DaoConnectionContext(String jdbcUrl) {
        this(jdbcUrl, () -> DriverManager.getConnection(jdbcUrl));
    }

    DaoConnectionContext(
            String jdbcUrl,
            ConnectionFactory connectionFactory
    ) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory,
                "connectionFactory"
        );
    }

    Connection connection() {
        Connection connection = threadConnection.get();
        try {
            if (connection == null || connection.isClosed()) {
                connection = openConnection();
                threadConnection.set(connection);
            }
            return connection;
        } catch (Exception error) {
            invalidate(connection);
            throw new RuntimeException("Failed to obtain SQLite connection", error);
        }
    }

    <T> T inTransaction(
            String failureMessage,
            TransactionWork<T> work
    ) {
        String message = Objects.requireNonNull(failureMessage, "failureMessage");
        TransactionWork<T> operation = Objects.requireNonNull(work, "work");
        Connection connection = connection();

        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
        } catch (SQLException setupFailure) {
            invalidate(connection);
            throw new RuntimeException(message, setupFailure);
        }

        if (!previousAutoCommit) {
            throw new IllegalStateException("Nested DAO transactions are not supported");
        }

        try {
            connection.setAutoCommit(false);
        } catch (SQLException setupFailure) {
            invalidate(connection);
            throw new RuntimeException(message, setupFailure);
        }

        Throwable primaryFailure = null;
        try {
            T result = operation.execute(connection);
            connection.commit();
            return result;
        } catch (Throwable failure) {
            primaryFailure = failure;
            try {
                connection.rollback();
            } catch (Throwable rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                invalidate(connection);
            }
            throw propagate(message, failure);
        } finally {
            try {
                if (!connection.isClosed()) {
                    connection.setAutoCommit(previousAutoCommit);
                }
            } catch (Throwable restoreFailure) {
                invalidate(connection);
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(restoreFailure);
                } else {
                    throw propagate(message + " during transaction cleanup", restoreFailure);
                }
            }
        }
    }

    void closeForCurrentThread() {
        Connection connection = threadConnection.get();
        try {
            if (connection != null) connection.close();
        } catch (Exception ignored) {
        } finally {
            threadConnection.remove();
        }
    }

    private Connection openConnection() {
        Connection connection = null;
        try {
            connection = connectionFactory.open();
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON;");
                statement.execute("PRAGMA busy_timeout=3000;");
            }
            return connection;
        } catch (Exception error) {
            closeQuietly(connection);
            throw new RuntimeException(
                    "Failed to open SQLite connection: " + jdbcUrl,
                    error
            );
        }
    }

    private void invalidate(Connection connection) {
        if (connection == null) return;

        Connection current = threadConnection.get();
        if (current == connection) {
            threadConnection.remove();
        }
        closeQuietly(connection);
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) return;
        try {
            connection.close();
        } catch (Exception ignored) {
        }
    }

    private RuntimeException propagate(String message, Throwable failure) {
        if (failure instanceof RuntimeException runtime) return runtime;
        if (failure instanceof Error error) throw error;
        return new RuntimeException(message, failure);
    }

    @FunctionalInterface
    interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    @FunctionalInterface
    interface TransactionWork<T> {
        T execute(Connection connection) throws Exception;
    }
}
