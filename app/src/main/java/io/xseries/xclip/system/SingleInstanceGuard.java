package io.xseries.xclip.system;

import javafx.application.Platform;

import java.io.*;
import java.net.*;

public final class SingleInstanceGuard {
    private static final int PORT = 32145;
    private static ServerSocket server;

    private SingleInstanceGuard() {}

    public static boolean tryBecomePrimary(Runnable onSecondaryPing) {
        try {
            server = new ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1"));
            Thread t = new Thread(() -> listenLoop(onSecondaryPing), "xclip-single-instance");
            t.setDaemon(true);
            t.start();
            return true;
        } catch (IOException alreadyRunning) {
            pingPrimary();
            return false;
        }
    }

    private static void listenLoop(Runnable onSecondaryPing) {
        while (!server.isClosed()) {
            try (Socket s = server.accept();
                 BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                String msg = br.readLine();
                if ("SHOW".equalsIgnoreCase(msg)) {
                    Platform.runLater(onSecondaryPing);
                }
            } catch (IOException ignored) {}
        }
    }

    private static void pingPrimary() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", PORT), 300);
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true)) {
                pw.println("SHOW");
            }
        } catch (IOException ignored) {}
    }
}
