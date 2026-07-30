/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.components;

import javafx.scene.control.Label;

/**
 * Segoe MDL2 Assets glyphs used by the Windows-only XClip desktop UI.
 *
 * Keeping glyph creation in one place gives every toolbar and window-control
 * icon the same font, optical sizing, and accessibility-independent behavior.
 */
public final class WindowsGlyphs {

    public static final String SEARCH = "\uE721";
    public static final String SETTINGS = "\uE713";
    public static final String HELP = "\uE897";
    public static final String DELETE = "\uE74D";
    public static final String PAUSE = "\uE769";
    public static final String RESUME = "\uE768";
    public static final String COPY = "\uE8C8";
    public static final String PASTE = "\uE77F";
    public static final String PIN = "\uE718";
    public static final String MORE = "\uE712";
    public static final String CHEVRON_DOWN = "\uE70D";
    public static final String HISTORY = "\uE81C";
    public static final String ALL = "\uE8FD";
    public static final String RESET = "\uE72C";
    public static final String CHECK = "\uE73E";
    public static final String MINIMIZE = "\uE921";
    public static final String MAXIMIZE = "\uE922";
    public static final String RESTORE = "\uE923";
    public static final String CLOSE = "\uE8BB";

    private WindowsGlyphs() {}

    public static Label icon(String glyph) {
        Label label = new Label(glyph == null ? "" : glyph);
        label.getStyleClass().add("mdl2-icon");
        label.setMouseTransparent(true);
        label.setFocusTraversable(false);
        return label;
    }

    public static Label icon(String glyph, String extraStyleClass) {
        Label label = icon(glyph);
        if (extraStyleClass != null && !extraStyleClass.isBlank()) {
            label.getStyleClass().add(extraStyleClass);
        }
        return label;
    }
}
