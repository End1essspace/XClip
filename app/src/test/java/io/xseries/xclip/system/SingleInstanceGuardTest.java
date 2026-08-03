/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleInstanceGuardTest {

    @Test
    void secondaryLaunchRequiresProtocolAcknowledgementAndActivatesPrimary() throws Exception {
        int port = freePort();
        CountDownLatch activated = new CountDownLatch(1);

        SingleInstanceGuard.Acquisition primary = SingleInstanceGuard.acquire(
                port,
                activated::countDown,
                Runnable::run,
                250
        );
        assertEquals(SingleInstanceGuard.AcquireStatus.PRIMARY, primary.status());
        assertNotNull(primary.guard());

        try (SingleInstanceGuard guard = primary.guard()) {
            SingleInstanceGuard.Acquisition secondary = SingleInstanceGuard.acquire(
                    port,
                    () -> {},
                    Runnable::run,
                    250
            );
            assertEquals(
                    SingleInstanceGuard.AcquireStatus.SECONDARY_NOTIFIED,
                    secondary.status()
            );
            assertTrue(activated.await(1, TimeUnit.SECONDS));
        }

        SingleInstanceGuard.Acquisition restarted = SingleInstanceGuard.acquire(
                port,
                () -> {},
                Runnable::run,
                250
        );
        assertEquals(SingleInstanceGuard.AcquireStatus.PRIMARY, restarted.status());
        restarted.guard().close();
    }

    @Test
    void unrelatedPortOwnerIsReportedAsConflict() throws Exception {
        int port = freePort();
        try (ServerSocket unrelated = new ServerSocket(
                port,
                1,
                InetAddress.getByName("127.0.0.1")
        )) {
            SingleInstanceGuard.Acquisition acquisition = SingleInstanceGuard.acquire(
                    port,
                    () -> {},
                    Runnable::run,
                    100
            );
            assertEquals(
                    SingleInstanceGuard.AcquireStatus.PORT_CONFLICT,
                    acquisition.status()
            );
        }
    }

    private int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(
                0,
                1,
                InetAddress.getByName("127.0.0.1")
        )) {
            return socket.getLocalPort();
        }
    }
}
