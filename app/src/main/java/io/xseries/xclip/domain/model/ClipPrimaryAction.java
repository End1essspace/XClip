/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.model;

/**
 * Primary safe action exposed for a derived clipboard content type.
 *
 * Commands are intentionally copy-only. XClip never executes clipboard text.
 */
public enum ClipPrimaryAction {
    NONE(""),
    OPEN_URL("Open in browser"),
    REVEAL_PATH("Show in Explorer"),
    COPY_FORMATTED_JSON("Copy formatted JSON"),
    COPY_CODE("Copy code"),
    COPY_COMMAND("Copy command");

    private final String label;

    ClipPrimaryAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean available() {
        return this != NONE;
    }
}
