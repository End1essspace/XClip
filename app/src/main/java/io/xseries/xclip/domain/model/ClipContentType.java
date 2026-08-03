/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.model;

/**
 * Derived clipboard-content category used by the popup UI.
 *
 * The value is intentionally not persisted: it is deterministic metadata that
 * can be recalculated from the original clipboard content at any time.
 */
public enum ClipContentType {
    TEXT("TEXT", "text"),
    CODE("CODE", "code"),
    URL("URL", "url"),
    PATH("PATH", "path"),
    JSON("JSON", "json"),
    COMMAND("COMMAND", "command");

    private final String label;
    private final String cssClass;

    ClipContentType(String label, String cssClass) {
        this.label = label;
        this.cssClass = cssClass;
    }

    public String label() {
        return label;
    }

    public String cssClass() {
        return cssClass;
    }
}
