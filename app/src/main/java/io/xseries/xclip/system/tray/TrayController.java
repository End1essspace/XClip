package io.xseries.xclip.system.tray;

import javafx.application.Platform;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;

public final class TrayController {

    private TrayIcon trayIcon;
    private CheckboxMenuItem pauseItem;

    private final AtomicBoolean paused = new AtomicBoolean(false);

    // Notify UI (PopupWindow) about pause changes
    private Consumer<Boolean> onPausedChanged = b -> {};

    // -------------------------
    // Windows Global Hotkey
    // -------------------------
    private static final int HOTKEY_ID = 1;
    private static final int MOD_CTRL = 0x0002;
    private static final int MOD_SHIFT = 0x0004;
    private static final int MOD_NOREPEAT = 0x4000;
    private static final int VK_V = 0x56; // 'V'

    private volatile boolean hotkeyRunning = false;
    private volatile int hotkeyNativeThreadId = 0;
    private Thread hotkeyThread;

    public void install(Runnable onOpen, Runnable onExit) {
        if (!SystemTray.isSupported()) return;
        if (trayIcon != null) return;

        EventQueue.invokeLater(() -> {
            try {
                SystemTray tray = SystemTray.getSystemTray();

                PopupMenu menu = new PopupMenu();

                MenuItem openItem = new MenuItem("Open XClip (Ctrl+Shift+V)");
                openItem.addActionListener(e -> Platform.runLater(onOpen));

                pauseItem = new CheckboxMenuItem("Pause capturing");
                pauseItem.setState(paused.get());
                pauseItem.addItemListener(e -> {
                    boolean isPaused = pauseItem.getState();
                    paused.set(isPaused);
                    updateIcon(isPaused);
                    Platform.runLater(() -> onPausedChanged.accept(isPaused));
                });

                MenuItem exitItem = new MenuItem("Exit");
                exitItem.addActionListener(e -> Platform.runLater(onExit));

                menu.add(openItem);
                menu.addSeparator();
                menu.add(pauseItem);
                menu.addSeparator();
                menu.add(exitItem);

                Image img = loadBestTrayImage(paused.get());
                trayIcon = new TrayIcon(img, "XClip " + io.xseries.xclip.AppVersion.VERSION, menu);
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener(e -> Platform.runLater(onOpen));

                tray.add(trayIcon);

                updateIcon(paused.get());
                startGlobalHotkey(onOpen);

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    public boolean isPaused() {
        return paused.get();
    }

    public void setOnPausedChanged(Consumer<Boolean> onPausedChanged) {
        this.onPausedChanged = (onPausedChanged != null) ? onPausedChanged : (b -> {});
    }

    public void shutdown() {
        stopGlobalHotkey();
        removeTrayIcon();
    }

    private void removeTrayIcon() {
        EventQueue.invokeLater(() -> {
            try {
                if (trayIcon != null) {
                    SystemTray.getSystemTray().remove(trayIcon);
                    trayIcon = null;
                }
            } catch (Exception ignored) {}
        });
    }

    private void updateIcon(boolean isPaused) {
        EventQueue.invokeLater(() -> {
            if (trayIcon == null) return;
            trayIcon.setImage(loadBestTrayImage(isPaused));
            String base = "XClip " + io.xseries.xclip.AppVersion.VERSION;
            trayIcon.setToolTip(isPaused ? base + " (paused)" : base);
        });
    }

    /**
     * Loads tray icon from /icons based on paused state.
     * Picks best size for current SystemTray.
     */
    private Image loadBestTrayImage(boolean paused) {
        int traySize = SystemTray.getSystemTray().getTrayIconSize().width;

        int[] sizes = {16, 24, 32, 48, 128, 256};

        int best = sizes[0];
        for (int s : sizes) {
            if (s >= traySize) {
                best = s;
                break;
            }
            best = s;
        }

        String name = paused
                ? "/icons/tray_paused_" + best + ".png"
                : "/icons/tray_" + best + ".png";

        Image img = tryLoad(name);
        if (img != null) return img;

        // fallback (should not normally happen)
        Image fallback = tryLoad("/icons/icon.png");
        if (fallback != null) return fallback;

        return generateFallbackIcon(paused);
    }

    private Image tryLoad(String path) {
        try {
            URL url = TrayController.class.getResource(path);
            if (url == null) return null;
            return Toolkit.getDefaultToolkit().getImage(url);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Image generateFallbackIcon(boolean paused) {
        int s = 16;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(paused ? new Color(255, 153, 0) : new Color(200, 200, 200));
            g.fillRect(0, 0, s, s);
        } finally {
            g.dispose();
        }
        return img;
    }

    // -------------------------
    // Hotkey logic (Windows)
    // -------------------------
    private void startGlobalHotkey(Runnable onOpen) {
        if (!isWindows()) return;
        if (hotkeyRunning) return;

        hotkeyRunning = true;

        hotkeyThread = new Thread(() -> {
            try {
                User32 user32 = User32.INSTANCE;

                boolean ok = user32.RegisterHotKey(
                        null,
                        HOTKEY_ID,
                        MOD_CTRL | MOD_SHIFT | MOD_NOREPEAT,
                        VK_V
                );

                if (!ok) {
                    int err = Kernel32.INSTANCE.GetLastError();
                    System.err.println("RegisterHotKey failed. LastError=" + err);
                    return;
                }

                hotkeyNativeThreadId = Kernel32.INSTANCE.GetCurrentThreadId();

                WinUser.MSG msg = new WinUser.MSG();
                while (hotkeyRunning) {
                    int result = user32.GetMessage(msg, null, 0, 0);
                    if (result == 0 || result == -1) break;

                    if (msg.message == WinUser.WM_HOTKEY) {
                        Platform.runLater(onOpen);
                    }

                    user32.TranslateMessage(msg);
                    user32.DispatchMessage(msg);
                }

            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                try { User32.INSTANCE.UnregisterHotKey(null, HOTKEY_ID); } catch (Throwable ignored) {}
            }
        }, "xclip-hotkey");

        hotkeyThread.setDaemon(true);
        hotkeyThread.start();
    }

    private void stopGlobalHotkey() {
        hotkeyRunning = false;

        try {
            if (hotkeyNativeThreadId != 0) {
                User32.INSTANCE.PostThreadMessage(hotkeyNativeThreadId, WinUser.WM_QUIT, null, null);
            }
        } catch (Throwable ignored) {}

        Thread t = hotkeyThread;
        if (t != null) {
            try { t.join(250); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            hotkeyThread = null;
        }
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }
}
