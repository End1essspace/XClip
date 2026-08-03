/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.clipboard;

import java.util.Objects;

/**
 * Tracks the exact capped clipboard value already observed by the watcher.
 *
 * Privacy suppression happens after markIfChanged returns true. Therefore an
 * excluded value remains observed and cannot be ingested later merely because
 * the foreground application changed while the clipboard stayed unchanged.
 */
final class ClipboardObservationState {

    private String lastSeenText;

    boolean markIfChanged(String value) {
        if (Objects.equals(value, lastSeenText)) return false;
        lastSeenText = value;
        return true;
    }

    void snapshot(String value) {
        if (value != null && !value.isBlank()) {
            lastSeenText = value;
        }
    }

    String lastSeenText() {
        return lastSeenText;
    }
}
