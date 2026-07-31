/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.components;

/**
 * Typed registry of every Lucide SVG resource shipped with XClip.
 *
 * Keeping resource names in one enum removes stringly-typed icon paths from
 * the UI and lets tests verify that every declared icon is packaged and
 * renderable before an installer is produced.
 */
public enum UiIcon {
    BRACES("braces"),
    CHECK_CHECK("check-check"),
    CHECK("check"),
    CHEVRON_DOWN("chevron-down"),
    CIRCLE_QUESTION_MARK("circle-question-mark"),
    CLIPBOARD_PASTE("clipboard-paste"),
    CODE_XML("code-xml"),
    COPY("copy"),
    ELLIPSIS_VERTICAL("ellipsis-vertical"),
    ELLIPSIS("ellipsis"),
    EXTERNAL_LINK("external-link"),
    FOLDER_OPEN("folder-open"),
    FUNNEL("funnel"),
    LIST("list"),
    MINUS("minus"),
    PAUSE("pause"),
    PENCIL("pencil"),
    PIN_OFF("pin-off"),
    PIN("pin"),
    PLAY("play"),
    PLUS("plus"),
    ROTATE_CCW_CLOCK("rotate-ccw-clock"),
    ROTATE_CCW("rotate-ccw"),
    SEARCH("search"),
    SETTINGS("settings"),
    SQUARE("square"),
    TAG("tag"),
    TAGS("tags"),
    TERMINAL("terminal"),
    TRASH_2("trash-2"),
    X("x"),
    ZAP("zap");

    private final String resourceName;

    UiIcon(String resourceName) {
        this.resourceName = resourceName;
    }

    public String resourceName() {
        return resourceName;
    }

    public String resourcePath() {
        return "/icons/ui/" + resourceName + ".svg";
    }
}
