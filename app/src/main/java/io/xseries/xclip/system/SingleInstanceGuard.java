/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system;

import javafx.application.Platform;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Loopback-only single-instance ownership and activation protocol.
 *
 * A failed bind is not automatically treated as another XClip process. The
 * secondary process must receive the explicit XClip acknowledgement before it
 * exits. This prevents an unrelated process that happens to own the port from
 * silently suppressing XClip startup.
 */
public final class SingleInstanceGuard implements AutoCloseable {

    public static final int DEFAULT_PORT = 32_145;

    private static final String SHOW_COMMAND = "SHOW";
    private static final String ACK_RESPONSE = "XCLIP_OK";
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 500;
    private static final int PING_ATTEMPTS = 3;
    private static final AtomicReference<SingleInstanceGuard> LEGACY_GUARD =
            new AtomicReference<>();

    private final ServerSocket server;
    private final Runnable onSecondaryPing;
    private final Consumer<Runnable> dispatcher;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Thread listenerThread;

    private SingleInstanceGuard(
            ServerSocket server,
            Runnable onSecondaryPing,
            Consumer<Runnable> dispatcher
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.onSecondaryPing = Objects.requireNonNull(
                onSecondaryPing,
                "onSecondaryPing"
        );
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.listenerThread = new Thread(this::listenLoop, "xclip-single-instance");
        this.listenerThread.setDaemon(true);
        this.listenerThread.start();
    }

    public static Acquisition acquire(Runnable onSecondaryPing) {
        return acquire(
                DEFAULT_PORT,
                onSecondaryPing,
                Platform::runLater,
                DEFAULT_CONNECT_TIMEOUT_MILLIS
        );
    }

    static Acquisition acquire(
            int port,
            Runnable onSecondaryPing,
            Consumer<Runnable> dispatcher,
            int connectTimeoutMillis
    ) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        Runnable callback = Objects.requireNonNull(
                onSecondaryPing,
                "onSecondaryPing"
        );
        Consumer<Runnable> callbackDispatcher = Objects.requireNonNull(
                dispatcher,
                "dispatcher"
        );
        int timeout = Math.max(100, connectTimeoutMillis);

        ServerSocket candidate = null;
        try {
            candidate = new ServerSocket();
            candidate.setReuseAddress(true);
            candidate.bind(new InetSocketAddress(loopback(), port), 50);
            return new Acquisition(
                    AcquireStatus.PRIMARY,
                    new SingleInstanceGuard(candidate, callback, callbackDispatcher)
            );
        } catch (IOException bindFailure) {
            if (candidate != null) {
                try {
                    candidate.close();
                } catch (IOException ignored) {
                }
            }
            boolean notified = pingPrimary(port, timeout);
            return new Acquisition(
                    notified
                            ? AcquireStatus.SECONDARY_NOTIFIED
                            : AcquireStatus.PORT_CONFLICT,
                    null
            );
        }
    }

    /**
     * Compatibility wrapper retained for existing callers.
     */
    public static boolean tryBecomePrimary(Runnable onSecondaryPing) {
        Acquisition acquisition = acquire(onSecondaryPing);
        if (!acquisition.primary()) return false;

        SingleInstanceGuard previous = LEGACY_GUARD.getAndSet(acquisition.guard());
        if (previous != null) previous.close();
        return true;
    }

    public static void closeLegacyGuard() {
        SingleInstanceGuard guard = LEGACY_GUARD.getAndSet(null);
        if (guard != null) guard.close();
    }

    private void listenLoop() {
        while (!closed.get()) {
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(DEFAULT_CONNECT_TIMEOUT_MILLIS);
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(),
                        StandardCharsets.UTF_8
                ));
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                        socket.getOutputStream(),
                        StandardCharsets.UTF_8
                ), true);

                String message = reader.readLine();
                if (!SHOW_COMMAND.equalsIgnoreCase(message)) continue;

                writer.println(ACK_RESPONSE);
                dispatchActivation();
            } catch (IOException error) {
                if (!closed.get()) {
                    // Transient loopback failures do not surrender ownership.
                }
            }
        }
    }

    private void dispatchActivation() {
        try {
            dispatcher.accept(onSecondaryPing);
        } catch (Throwable ignored) {
            // Single-instance ownership must survive a failing UI callback.
        }
    }

    private static boolean pingPrimary(int port, int timeoutMillis) {
        for (int attempt = 0; attempt < PING_ATTEMPTS; attempt++) {
            try (Socket socket = new Socket()) {
                socket.connect(
                        new InetSocketAddress(loopback(), port),
                        timeoutMillis
                );
                socket.setSoTimeout(timeoutMillis);

                PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                        socket.getOutputStream(),
                        StandardCharsets.UTF_8
                ), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(),
                        StandardCharsets.UTF_8
                ));

                writer.println(SHOW_COMMAND);
                return ACK_RESPONSE.equals(reader.readLine());
            } catch (IOException ignored) {
                if (attempt + 1 < PING_ATTEMPTS) {
                    try {
                        Thread.sleep(75L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private static InetAddress loopback() throws IOException {
        return InetAddress.getByName("127.0.0.1");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;

        try {
            server.close();
        } catch (IOException ignored) {
        }

        if (Thread.currentThread() == listenerThread) return;
        try {
            listenerThread.join(750L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public enum AcquireStatus {
        PRIMARY,
        SECONDARY_NOTIFIED,
        PORT_CONFLICT
    }

    public record Acquisition(
            AcquireStatus status,
            SingleInstanceGuard guard
    ) {
        public Acquisition {
            status = Objects.requireNonNull(status, "status");
            if ((status == AcquireStatus.PRIMARY) != (guard != null)) {
                throw new IllegalArgumentException(
                        "Only PRIMARY acquisitions may carry a guard"
                );
            }
        }

        public boolean primary() {
            return status == AcquireStatus.PRIMARY;
        }
    }
}
