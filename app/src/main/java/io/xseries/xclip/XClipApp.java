/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip;

import io.xseries.xclip.config.AppPaths;
import io.xseries.xclip.config.Config;
import io.xseries.xclip.config.ConfigService;
import io.xseries.xclip.data.dao.ClipEntryDao;
import io.xseries.xclip.data.db.Database;
import io.xseries.xclip.domain.service.ClipService;
import io.xseries.xclip.system.DataOwnershipService;
import io.xseries.xclip.system.clipboard.ClipboardAccess;
import io.xseries.xclip.system.clipboard.WatcherController;
import io.xseries.xclip.system.tray.TrayController;
import io.xseries.xclip.ui.PopupWindow;
import io.xseries.xclip.ui.SettingsWindow;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import io.xseries.xclip.system.SingleInstanceGuard;

import java.util.concurrent.atomic.AtomicBoolean;

public final class XClipApp extends Application {

    private final AtomicBoolean exiting = new AtomicBoolean(false);
    private final AtomicBoolean shutdownOnce = new AtomicBoolean(false);

    private Database db;
    private WatcherController watcherController;
    private PopupWindow popup;
    private TrayController tray;

    @Override
    public void start(Stage unusedStage) {
        Platform.setImplicitExit(false);
        if (!SingleInstanceGuard.tryBecomePrimary(() -> {
            if (popup != null) popup.showOrFocus();
        })) {
            Platform.exit();
            return;
        }

        // --- paths + config ---
        ConfigService configService = new ConfigService(AppPaths.configPath());
        Config config = configService.loadOrCreate();
        
        // Sync Windows autostart with config on startup (best-effort)
        try {
            configService.applyRuntime(config);
        } catch (Throwable ignored) {}

        // --- DB ---
        this.db = new Database(AppPaths.dbPath());
        db.init();

        // --- services ---
        ClipEntryDao dao = new ClipEntryDao(db.jdbcUrl());
        ClipService clipService = new ClipService(dao);
        clipService.applyConfig(config);

        ClipboardAccess clipboard = new ClipboardAccess();

        // --- runtime controllers ---
        this.tray = new TrayController();

        watcherController = new WatcherController(
                clipboard,
                clipService::ingestText,
                tray::isPaused
        );

        // data ownership service (needs instance Database)
        DataOwnershipService dataOwnershipService = new DataOwnershipService(db);

        // Settings window (USED)
        SettingsWindow settingsWindow = new SettingsWindow(
                configService,
                clipService,
                watcherController,
                dataOwnershipService,
                config
        );

        // Popup (USED settings open action)
        Runnable openSettings = settingsWindow::show;
        this.popup = new PopupWindow(dao, clipboard, clipService, openSettings);
        popup.enableWindowPersistence(configService, config);
        
        tray.install(
                popup::showOrFocus,
                this::exitApplication
        );

        // sync paused UI state
        popup.setPaused(tray.isPaused());
        tray.setOnPausedChanged(popup::setPaused);

        // watcher enabled by config
        if (config.watcherEnabled()) watcherController.enable();
        else watcherController.disable();

        // optional: show on start if not minimized
        if (!config.startMinimized()) {
            Platform.runLater(popup::showOrFocus);
        }
    }

    private void exitApplication() {
        if (!exiting.compareAndSet(false, true)) return;

        Platform.runLater(() -> {
            try {
                shutdownInternal();
            } finally {
                Platform.exit();
                System.exit(0);
            }
        });
    }

    @Override
    public void stop() {
        shutdownInternal();
    }

    private void shutdownInternal() {
        if (!shutdownOnce.compareAndSet(false, true)) return;

        try {
            if (watcherController != null) {
                watcherController.close();
                watcherController = null;
            }
        } catch (Exception ignored) {}

        try {
            if (popup != null) {
                popup.shutdown();
                popup = null;
            }
        } catch (Exception ignored) {}

        try {
            if (tray != null) {
                tray.shutdown();
                tray = null;
            }
        } catch (Exception ignored) {}

        try {
            if (db != null) {
                db.close();
                db = null;
            }
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        launch(args);
    }
}
