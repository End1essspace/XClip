
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.tray;

import javafx.application.Platform;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;

public final class TrayController {

    private TrayIcon trayIcon;
    private JWindow trayMenuWindow;

    private Rectangle trayMenuScreenBounds;
    private javax.swing.Timer trayMenuOutsideClickTimer;
    private long trayMenuShownAtMs;
    private boolean trayMenuMouseWasDown;

    private final AtomicBoolean paused = new AtomicBoolean(false);

    // Notify UI (PopupWindow) about pause changes
    private Consumer<Boolean> onPausedChanged = b -> {};

    // -------------------------
    // Tray menu theme
    // -------------------------
    private static final Color MENU_BG = new Color(21, 25, 34);          // #151922
    private static final Color MENU_HOVER = new Color(36, 42, 54);       // #242A36
    private static final Color MENU_BORDER = new Color(52, 59, 74);      // #343B4A
    private static final Color MENU_SEPARATOR = new Color(45, 52, 66);   // #2D3442
    private static final Color MENU_TEXT = new Color(232, 236, 243);     // #E8ECF3
    private static final Color MENU_TEXT_HOVER = Color.WHITE;
    private static final Color MENU_ACCENT = new Color(245, 197, 66);    // #F5C542

    private static final int MENU_WIDTH = 218;
    private static final int MENU_ITEM_HEIGHT = 30;
    private static final int MENU_SEPARATOR_HEIGHT = 7;

    // -------------------------
    // Windows Global Hotkey
    // -------------------------
    private static final int HOTKEY_ID = 1;
    private static final int MOD_CTRL = 0x0002;
    private static final int MOD_SHIFT = 0x0004;
    private static final int MOD_NOREPEAT = 0x4000;
    private static final int VK_V = 0x56; // 'V'

    private static final int VK_LBUTTON = 0x01;
    private static final int VK_RBUTTON = 0x02;
    private static final int VK_MBUTTON = 0x04;

    private interface MouseApi extends com.sun.jna.Library {
        MouseApi INSTANCE = com.sun.jna.Native.load("user32", MouseApi.class);

        short GetAsyncKeyState(int vKey);
    }

    private volatile boolean hotkeyRunning = false;
    private volatile int hotkeyNativeThreadId = 0;
    private Thread hotkeyThread;

    public void install(Runnable onOpen, Runnable onExit) {
        install(onOpen, onOpen, onExit);
    }

    public void install(Runnable onOpen, Runnable onHotkeyOpen, Runnable onExit) {
        if (!SystemTray.isSupported()) return;
        if (trayIcon != null) return;

        EventQueue.invokeLater(() -> {
            try {
                SystemTray tray = SystemTray.getSystemTray();

                Image img = loadBestTrayImage(paused.get());

                trayIcon = new TrayIcon(img, "XClip " + io.xseries.xclip.AppVersion.VERSION);
                trayIcon.setImageAutoSize(true);

                // Left click / default action
                trayIcon.addActionListener(e -> Platform.runLater(onOpen));

                // Right click menu
                trayIcon.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseReleased(MouseEvent e) {
                        maybeShowTrayMenu(e, onOpen, onExit);
                    }
                });

                tray.add(trayIcon);

                updateIcon(paused.get());
                startGlobalHotkey(onHotkeyOpen);

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private void maybeShowTrayMenu(MouseEvent e, Runnable onOpen, Runnable onExit) {
        if (e == null) return;

        boolean popupTrigger = e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3;
        if (!popupTrigger) return;

        EventQueue.invokeLater(() -> showDarkTrayMenu(onOpen, onExit));
    }

    private void showDarkTrayMenu(Runnable onOpen, Runnable onExit) {
        hideTrayMenu();

        JPanel menu = new JPanel();
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBackground(MENU_BG);
        menu.setOpaque(true);
        menu.setBorder(new CompoundBorder(
                new LineBorder(MENU_BORDER, 1),
                new EmptyBorder(4, 0, 4, 0)
        ));

        menu.add(createDarkMenuItem(
                "Open XClip (Ctrl+Shift+V)",
                false,
                () -> {
                    hideTrayMenu();
                    Platform.runLater(onOpen);
                }
        ));

        menu.add(createDarkSeparator());

        boolean isPaused = paused.get();
        menu.add(createDarkMenuItem(
                isPaused ? "Resume capturing" : "Pause capturing",
                isPaused,
                () -> {
                    boolean next = !paused.get();
                    paused.set(next);
                    updateIcon(next);
                    hideTrayMenu();
                    Platform.runLater(() -> onPausedChanged.accept(next));
                }
        ));

        menu.add(createDarkSeparator());

        menu.add(createDarkMenuItem(
                "Exit",
                false,
                () -> {
                    hideTrayMenu();
                    Platform.runLater(onExit);
                }
        ));

        Dimension size = menu.getPreferredSize();

        Point p = MouseInfo.getPointerInfo().getLocation();
        Rectangle bounds = getVirtualScreenBounds();

        int x = p.x - size.width + 14;
        int y = p.y - size.height - 8;

        if (x < bounds.x + 4) {
            x = bounds.x + 4;
        }

        if (y < bounds.y + 4) {
            y = p.y + 10;
        }

        if (x + size.width > bounds.x + bounds.width - 4) {
            x = bounds.x + bounds.width - size.width - 4;
        }

        if (y + size.height > bounds.y + bounds.height - 4) {
            y = bounds.y + bounds.height - size.height - 4;
        }

        if (trayMenuWindow == null) {
            trayMenuWindow = new JWindow();
            trayMenuWindow.setAlwaysOnTop(true);

            // Focus is useful for Esc, but outside-click closing does NOT depend on focus.
            trayMenuWindow.setFocusableWindowState(true);
            trayMenuWindow.setAutoRequestFocus(true);

            trayMenuWindow.getRootPane().registerKeyboardAction(
                    e -> hideTrayMenu(),
                    KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW
            );
        }

        trayMenuWindow.setContentPane(menu);
        trayMenuWindow.pack();
        trayMenuWindow.setLocation(x, y);

        Dimension actualSize = trayMenuWindow.getSize();
        trayMenuScreenBounds = new Rectangle(x, y, actualSize.width, actualSize.height);

        trayMenuWindow.setVisible(true);
        trayMenuWindow.toFront();

        startTrayMenuOutsideClickWatch();

        EventQueue.invokeLater(() -> {
            if (trayMenuWindow != null && trayMenuWindow.isVisible()) {
                trayMenuWindow.requestFocus();
                trayMenuWindow.requestFocusInWindow();
            }
        });
    }

    private JComponent createDarkMenuItem(String text, boolean accentText, Runnable action) {
        JPanel item = new JPanel(new BorderLayout());
        item.setOpaque(true);
        item.setBackground(MENU_BG);
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Dimension d = new Dimension(MENU_WIDTH, MENU_ITEM_HEIGHT);
        item.setPreferredSize(d);
        item.setMinimumSize(d);
        item.setMaximumSize(d);

        JPanel accentBar = new JPanel();
        accentBar.setOpaque(true);
        accentBar.setBackground(accentText ? MENU_ACCENT : MENU_BG);

        Dimension accentSize = new Dimension(3, MENU_ITEM_HEIGHT);
        accentBar.setPreferredSize(accentSize);
        accentBar.setMinimumSize(accentSize);
        accentBar.setMaximumSize(accentSize);

        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(accentText ? MENU_ACCENT : MENU_TEXT);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(6, 10, 6, 16));
        content.add(label, BorderLayout.CENTER);

        item.add(accentBar, BorderLayout.WEST);
        item.add(content, BorderLayout.CENTER);

        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                item.setBackground(MENU_HOVER);
                accentBar.setBackground(MENU_ACCENT);
                label.setForeground(MENU_TEXT_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                item.setBackground(MENU_BG);
                accentBar.setBackground(accentText ? MENU_ACCENT : MENU_BG);
                label.setForeground(accentText ? MENU_ACCENT : MENU_TEXT);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                if (action != null) action.run();
            }
        });

        return item;
    }

    private JComponent createDarkSeparator() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(true);
        wrap.setBackground(MENU_BG);
        wrap.setBorder(new EmptyBorder(3, 3, 3, 0));

        Dimension d = new Dimension(MENU_WIDTH, MENU_SEPARATOR_HEIGHT);
        wrap.setPreferredSize(d);
        wrap.setMinimumSize(d);
        wrap.setMaximumSize(d);

        JSeparator sep = new JSeparator();
        sep.setForeground(MENU_SEPARATOR);
        sep.setBackground(MENU_BG);

        wrap.add(sep, BorderLayout.CENTER);
        return wrap;
    }

    private void startTrayMenuOutsideClickWatch() {
        stopTrayMenuOutsideClickWatch();

        trayMenuShownAtMs = System.currentTimeMillis();
        trayMenuMouseWasDown = isAnyMouseButtonDown();

        trayMenuOutsideClickTimer = new javax.swing.Timer(35, e -> {
            if (trayMenuWindow == null || !trayMenuWindow.isVisible()) {
                stopTrayMenuOutsideClickWatch();
                return;
            }

            boolean down = isAnyMouseButtonDown();
            long ageMs = System.currentTimeMillis() - trayMenuShownAtMs;

            // Ignore the original right-click that opened the tray menu.
            if (ageMs < 180) {
                trayMenuMouseWasDown = down;
                return;
            }

            // Detect a new mouse press after menu opening.
            if (down && !trayMenuMouseWasDown) {
                Point mp = MouseInfo.getPointerInfo().getLocation();
                Rectangle r = trayMenuScreenBounds;

                if (r == null || !r.contains(mp)) {
                    hideTrayMenu();
                    return;
                }
            }

            trayMenuMouseWasDown = down;
        });

        trayMenuOutsideClickTimer.setRepeats(true);
        trayMenuOutsideClickTimer.start();
    }

    private void stopTrayMenuOutsideClickWatch() {
        try {
            if (trayMenuOutsideClickTimer != null) {
                trayMenuOutsideClickTimer.stop();
                trayMenuOutsideClickTimer = null;
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isAnyMouseButtonDown() {
        if (!isWindows()) return false;

        return isMouseButtonDown(VK_LBUTTON)
                || isMouseButtonDown(VK_RBUTTON)
                || isMouseButtonDown(VK_MBUTTON);
    }

    private boolean isMouseButtonDown(int vk) {
        try {
            int state = MouseApi.INSTANCE.GetAsyncKeyState(vk);

            // High-order bit means the key/button is currently down.
            // Do not use the low-order "clicked since last call" bit here:
            // it can be stale and close the tray menu unexpectedly.
            return (state & 0x8000) != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void hideTrayMenu() {
        try {
            stopTrayMenuOutsideClickWatch();
            trayMenuScreenBounds = null;
            trayMenuMouseWasDown = false;

            if (trayMenuWindow != null) {
                trayMenuWindow.setVisible(false);
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean isPaused() {
        return paused.get();
    }

    public void setOnPausedChanged(Consumer<Boolean> onPausedChanged) {
        this.onPausedChanged = (onPausedChanged != null) ? onPausedChanged : (b -> {});
    }

    public void shutdown() {
        stopGlobalHotkey();
        hideTrayMenu();
        removeTrayIcon();

        EventQueue.invokeLater(() -> {
            try {
                if (trayMenuWindow != null) {
                    trayMenuWindow.dispose();
                    trayMenuWindow = null;
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private Rectangle getVirtualScreenBounds() {
        Rectangle result = new Rectangle();

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] devices = ge.getScreenDevices();

        for (GraphicsDevice device : devices) {
            GraphicsConfiguration config = device.getDefaultConfiguration();
            Rectangle bounds = config.getBounds();

            if (result.isEmpty()) {
                result = new Rectangle(bounds);
            } else {
                result = result.union(bounds);
            }
        }

        return result;
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
                try {
                    User32.INSTANCE.UnregisterHotKey(null, HOTKEY_ID);
                } catch (Throwable ignored) {
                }
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
        } catch (Throwable ignored) {
        }

        Thread t = hotkeyThread;
        if (t != null) {
            try {
                t.join(250);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            hotkeyThread = null;
        }
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }
}
