/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.data.model.ClipTag;

import java.util.List;
import java.util.Objects;

/**
 * Immutable rows rendered by the popup list.
 */
public sealed interface PopupRow permits PopupRow.SectionRow, PopupRow.ClipRow {

    record SectionRow(String title, int count) implements PopupRow {
        public SectionRow {
            title = Objects.requireNonNullElse(title, "");
            count = Math.max(0, count);
        }
    }

    record ClipRow(ClipEntry entry, List<ClipTag> tags) implements PopupRow {
        public ClipRow {
            Objects.requireNonNull(entry, "entry");
            tags = tags == null ? List.of() : List.copyOf(tags);
        }

        public ClipRow(ClipEntry entry) {
            this(entry, List.of());
        }
    }
}
