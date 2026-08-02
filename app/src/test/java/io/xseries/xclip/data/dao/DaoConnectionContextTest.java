/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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
            assertEquals(3_000, pragmaInt(first, "busy_timeout"));

            boolean previousAutoCommit = context.beginTransaction(
                    first,
                    "transaction setup failed"
            );
            assertTrue(previousAutoCommit);
            assertFalse(first.getAutoCommit());

            context.rollbackQuietly(first);
            context.restoreAutoCommit(first, previousAutoCommit);
            assertTrue(first.getAutoCommit());
        } finally {
            context.closeForCurrentThread();
        }

        assertTrue(first.isClosed());

        Connection reopened = context.connection();
        try {
            assertNotSame(first, reopened);
            assertFalse(reopened.isClosed());
            assertEquals(1, pragmaInt(reopened, "foreign_keys"));
            assertEquals(3_000, pragmaInt(reopened, "busy_timeout"));
        } finally {
            context.closeForCurrentThread();
        }
    }

    private int pragmaInt(Connection connection, String name) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA " + name + ";")) {
            return result.next() ? result.getInt(1) : -1;
        }
    }
}
