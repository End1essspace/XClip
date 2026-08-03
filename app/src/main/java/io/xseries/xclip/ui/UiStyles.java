/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui;

import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.net.URL;
import java.util.List;
import java.util.Objects;

/**
 * Single source of truth for JavaFX stylesheet composition.
 *
 * Styles are deliberately split by responsibility:
 * - theme.css: shared design tokens and typography;
 * - controls.css: reusable JavaFX controls and SVG primitives;
 * - popup.css: popup shell, rows, filters, actions, and compact density;
 * - dialogs.css: Settings and modal surfaces.
 *
 * Resource resolution is strict. A missing stylesheet is a packaging defect
 * and must fail close during UI construction instead of silently producing an
 * unstyled or partially styled window.
 */
public final class UiStyles {

    private static final String THEME = "/ui/theme.css";
    private static final String CONTROLS = "/ui/controls.css";
    private static final String POPUP = "/ui/popup.css";
    private static final String DIALOGS = "/ui/dialogs.css";

    private static final List<String> POPUP_RESOURCES =
            List.of(THEME, CONTROLS, POPUP);
    private static final List<String> SETTINGS_RESOURCES =
            List.of(THEME, CONTROLS, DIALOGS);
    private static final List<String> DIALOG_RESOURCES =
            List.of(THEME, CONTROLS, DIALOGS);

    private UiStyles() {}

    public static void applyPopup(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        apply(scene.getStylesheets(), POPUP_RESOURCES);
    }

    public static void applySettings(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        apply(scene.getStylesheets(), SETTINGS_RESOURCES);
    }

    public static void applyDialog(Parent root) {
        Objects.requireNonNull(root, "root");
        apply(root.getStylesheets(), DIALOG_RESOURCES);
    }

    static List<String> popupResourcePaths() {
        return POPUP_RESOURCES;
    }

    static List<String> settingsResourcePaths() {
        return SETTINGS_RESOURCES;
    }

    static List<String> dialogResourcePaths() {
        return DIALOG_RESOURCES;
    }

    static URL requireResource(String path) {
        URL resource = UiStyles.class.getResource(path);
        if (resource == null) {
            throw new IllegalStateException("Missing UI stylesheet resource: " + path);
        }
        return resource;
    }

    private static void apply(
            ObservableList<String> target,
            List<String> resourcePaths
    ) {
        for (String path : resourcePaths) {
            String external = requireResource(path).toExternalForm();
            if (!target.contains(external)) {
                target.add(external);
            }
        }
    }
}
