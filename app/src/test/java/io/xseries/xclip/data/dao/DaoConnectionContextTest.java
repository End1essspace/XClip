/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaoConnectionContextTest {

    @TempDir
    Path tempDir;

    @Test
    void reusesConfiguredConnectionAndReopensAfterThreadClose() throws Exception {
        String jdbcUrl = "jdbc:sqlite:" + tempDir.resolve("context.db").toAbsolutePath();
        DaoConnectionContext context = new DaoConnectionContext(jdbcUrl);

        Connection first = context.connection();
        try {
            assertSame(first, context.connection());
            assertEquals(1, pragmaInt(first, "foreign_keys"));
            assertEquals(1, pragmaInt(first, "synchronous"));
            assertEquals(2, pragmaInt(first, "temp_store"));
            assertEquals(3_000, pragmaInt(first, "busy_timeout"));
        } finally {
            context.closeForCurrentThread();
        }

        assertTrue(first.isClosed());

        Connection reopened = context.connection();
        try {
            assertNotSame(first, reopened);
            assertFalse(reopened.isClosed());
            assertEquals(1, pragmaInt(reopened, "foreign_keys"));
            assertEquals(1, pragmaInt(reopened, "synchronous"));
            assertEquals(2, pragmaInt(reopened, "temp_store"));
            assertEquals(3_000, pragmaInt(reopened, "busy_timeout"));
        } finally {
            context.closeForCurrentThread();
        }
    }

    @Test
    void transactionFailureRollsBackRestoresAndKeepsConnectionReusable() throws Exception {
        String jdbcUrl = "jdbc:sqlite:" + tempDir.resolve("rollback.db").toAbsolutePath();
        DaoConnectionContext context = new DaoConnectionContext(jdbcUrl);
        Connection connection = context.connection();

        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE values_table(value TEXT NOT NULL)");
        }

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> context.inTransaction("insert failed", c -> {
                    try (Statement statement = c.createStatement()) {
                        statement.executeUpdate(
                                "INSERT INTO values_table(value) VALUES ('rolled back')"
                        );
                    }
                    throw new IllegalStateException("primary failure");
                })
        );

        assertEquals("primary failure", failure.getMessage());
        assertTrue(connection.getAutoCommit());
        assertSame(connection, context.connection());
        assertEquals(0, rowCount(connection));

        context.inTransaction("retry failed", c -> {
            try (Statement statement = c.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO values_table(value) VALUES ('committed')"
                );
            }
            return null;
        });

        assertTrue(connection.getAutoCommit());
        assertEquals(1, rowCount(connection));
        context.closeForCurrentThread();
    }

    @Test
    void restoreFailureIsSuppressedWithoutMaskingPrimaryFailure() throws Exception {
        String jdbcUrl = "jdbc:sqlite:" + tempDir.resolve("restore-failure.db").toAbsolutePath();
        AtomicBoolean failRestore = new AtomicBoolean(false);
        AtomicInteger opened = new AtomicInteger();

        DaoConnectionContext context = new DaoConnectionContext(jdbcUrl, () -> {
            opened.incrementAndGet();
            Connection delegate = DriverManager.getConnection(jdbcUrl);
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("setAutoCommit")
                                && args != null
                                && args.length == 1
                                && Boolean.TRUE.equals(args[0])
                                && failRestore.get()) {
                            throw new SQLException("restore failed");
                        }
                        try {
                            return method.invoke(delegate, args);
                        } catch (InvocationTargetException invocationFailure) {
                            throw invocationFailure.getCause();
                        }
                    }
            );
        });

        Connection first = context.connection();
        failRestore.set(true);

        IllegalArgumentException primary = assertThrows(
                IllegalArgumentException.class,
                () -> context.inTransaction(
                        "operation failed",
                        c -> { throw new IllegalArgumentException("primary failure"); }
                )
        );

        assertEquals("primary failure", primary.getMessage());
        assertEquals(1, primary.getSuppressed().length);
        assertEquals("restore failed", primary.getSuppressed()[0].getMessage());
        assertTrue(first.isClosed());

        failRestore.set(false);
        Connection reopened = context.connection();
        try {
            assertNotSame(first, reopened);
            assertEquals(2, opened.get());
        } finally {
            context.closeForCurrentThread();
        }
    }





    @Test
    void releaseAllConnectionsClosesTrackedConnectionsButAllowsReopen() throws Exception {
        String jdbcUrl = "jdbc:sqlite:" + tempDir.resolve("release-all.db").toAbsolutePath();
        DaoConnectionContext context = new DaoConnectionContext(jdbcUrl);

        Connection first = context.connection();
        context.releaseAllConnections();

        assertTrue(first.isClosed());

        Connection reopened = context.connection();
        try {
            assertNotSame(first, reopened);
            assertFalse(reopened.isClosed());
        } finally {
            context.closeAll();
        }
    }

    @Test
    void closeAllClosesConnectionsOwnedByMultipleThreadsAndPreventsReopen() throws Exception {
        String jdbcUrl = "jdbc:sqlite:" + tempDir.resolve("close-all.db").toAbsolutePath();
        DaoConnectionContext context = new DaoConnectionContext(jdbcUrl);
        ExecutorService worker = Executors.newSingleThreadExecutor();

        Connection workerConnection = worker.submit(context::connection).get();
        Connection mainConnection = context.connection();

        try {
            assertNotSame(workerConnection, mainConnection);

            context.closeAll();

            assertTrue(workerConnection.isClosed());
            assertTrue(mainConnection.isClosed());
            assertThrows(IllegalStateException.class, context::connection);
        } finally {
            worker.shutdownNow();
            context.closeAll();
        }
    }

    @Test
    void closingUnusedContextDoesNotOpenConnection() {
        AtomicInteger opened = new AtomicInteger();
        DaoConnectionContext context = new DaoConnectionContext(
                "jdbc:sqlite:unused",
                () -> {
                    opened.incrementAndGet();
                    throw new SQLException("must not open");
                }
        );

        context.closeForCurrentThread();
        assertEquals(0, opened.get());
    }

    private int pragmaInt(Connection connection, String name) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA " + name + ";")) {
            return result.next() ? result.getInt(1) : -1;
        }
    }

    private int rowCount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM values_table")) {
            return result.next() ? result.getInt(1) : -1;
        }
    }
}