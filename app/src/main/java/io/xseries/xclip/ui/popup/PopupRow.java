/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipEntry;

import java.util.Objects;

/**
 * Immutable rows rendered by the popup list.
 *
 * Keeping section and clip rows outside PopupWindow makes the list contract
 * explicit and prepares the UI for a dedicated reusable cell implementation.
 */
public sealed interface PopupRow permits PopupRow.SectionRow, PopupRow.ClipRow {

    record SectionRow(String title) implements PopupRow {
        public SectionRow {
            title = Objects.requireNonNullElse(title, "");
        }
    }

    record ClipRow(ClipEntry entry) implements PopupRow {
        public ClipRow {
            Objects.requireNonNull(entry, "entry");
        }
    }
}
