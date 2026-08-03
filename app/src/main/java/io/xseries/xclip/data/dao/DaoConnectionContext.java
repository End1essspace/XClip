/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.dao;

import io.xseries.xclip.data.db.SqliteConnectionConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared connection and transaction lifecycle for SQLite DAO implementations.
 *
 * Every DAO owns its own context, preserving one connection per thread and DAO.
 * The context also tracks every opened connection so terminal DAO shutdown can
 * release worker-owned connections before database files are deleted.
 */
final class DaoConnectionContext {

    private final String jdbcUrl;
    private final ConnectionFactory connectionFactory;
    private final ThreadLocal<Connection> threadConnection = new ThreadLocal<>();
    private final Set<Connection> activeConnections = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Object lifecycleLock = new Object();

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
        if (closed.get()) {
            throw new IllegalStateException("DAO connection context is closed");
        }

        Connection connection = threadConnection.get();
        try {
            if (connection == null || connection.isClosed()) {
                connection = openConnection();
                threadConnection.set(connection);
            }
            return connection;
        } catch (Exception error) {
            invalidate(connection);
            if (error instanceof IllegalStateException state) throw state;
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
        threadConnection.remove();
        closeTracked(connection);
    }

    /**
     * Closes every currently tracked connection without terminally closing the DAO.
     *
     * Threads that still hold a closed ThreadLocal value will transparently open
     * a replacement connection on their next DAO operation. This supports a safe
     * retry when user-data deletion fails because another process owns a file.
     */
    void releaseAllConnections() {
        synchronized (lifecycleLock) {
            threadConnection.remove();
            closeActiveConnections();
        }
    }

    /**
     * Terminally closes every connection opened by this DAO context.
     *
     * After this method returns, the context cannot open replacement connections.
     */
    void closeAll() {
        synchronized (lifecycleLock) {
            if (!closed.compareAndSet(false, true)) return;

            threadConnection.remove();
            closeActiveConnections();
        }
    }

    private void closeActiveConnections() {
        List<Connection> snapshot = new ArrayList<>(activeConnections);
        for (Connection connection : snapshot) {
            closeTracked(connection);
        }
    }

    private Connection openConnection() {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                throw new IllegalStateException("DAO connection context is closed");
            }

            Connection connection = null;
            try {
                connection = connectionFactory.open();
                SqliteConnectionConfig.configureWorkingConnection(connection);

                if (closed.get()) {
                    closeQuietly(connection);
                    throw new IllegalStateException("DAO connection context is closed");
                }

                activeConnections.add(connection);
                return connection;
            } catch (Exception error) {
                closeQuietly(connection);
                if (error instanceof IllegalStateException state) throw state;
                throw new RuntimeException(
                        "Failed to open SQLite connection: " + jdbcUrl,
                        error
                );
            }
        }
    }

    private void invalidate(Connection connection) {
        if (connection == null) return;

        Connection current = threadConnection.get();
        if (current == connection) {
            threadConnection.remove();
        }
        closeTracked(connection);
    }

    private void closeTracked(Connection connection) {
        if (connection == null) return;
        activeConnections.remove(connection);
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
