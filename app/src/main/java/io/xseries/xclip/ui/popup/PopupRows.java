/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.ui.popup.PopupRow.ClipRow;
import io.xseries.xclip.ui.popup.PopupRow.SectionRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds immutable sectioned popup rows off the JavaFX Application Thread.
 */
public final class PopupRows {

    private PopupRows() {}

    public static List<PopupRow> build(List<ClipEntry> sorted) {
        if (sorted == null || sorted.isEmpty()) return List.of();

        int pinnedCount = 0;
        int recentCount = 0;
        for (ClipEntry entry : sorted) {
            if (entry == null) continue;
            if (entry.favorite()) pinnedCount++;
            else recentCount++;
        }

        List<PopupRow> out = new ArrayList<>(
                sorted.size() + (pinnedCount > 0 ? 1 : 0) + (recentCount > 0 ? 1 : 0)
        );

        if (pinnedCount > 0) {
            out.add(new SectionRow("PINNED", pinnedCount));
            for (ClipEntry entry : sorted) {
                if (entry != null && entry.favorite()) out.add(new ClipRow(entry));
            }
        }

        if (recentCount > 0) {
            out.add(new SectionRow("RECENT", recentCount));
            for (ClipEntry entry : sorted) {
                if (entry != null && !entry.favorite()) out.add(new ClipRow(entry));
            }
        }

        return List.copyOf(out);
    }

    public static int countClips(List<PopupRow> rows) {
        if (rows == null || rows.isEmpty()) return 0;

        int count = 0;
        for (PopupRow row : rows) {
            if (row instanceof ClipRow) count++;
        }
        return count;
    }
}
