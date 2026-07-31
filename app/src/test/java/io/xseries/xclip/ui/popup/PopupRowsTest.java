/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.data.model.ClipTag;
import io.xseries.xclip.ui.popup.PopupRow.ClipRow;
import io.xseries.xclip.ui.popup.PopupRow.SectionRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PopupRowsTest {

    @Test
    void buildsSectionsWithoutChangingClipOrTagOrder() {
        List<ClipEntry> clips = List.of(
                new ClipEntry(1, "pinned", null, true, 0, 30),
                new ClipEntry(2, "recent-a", null, false, null, 20),
                new ClipEntry(3, "recent-b", null, false, null, 10)
        );
        ClipTag alpha = new ClipTag(10, "Alpha", 1);
        ClipTag beta = new ClipTag(11, "Beta", 2);

        List<PopupRow> rows = PopupRows.build(
                clips,
                Map.of(2L, List.of(alpha, beta))
        );

        assertEquals(new SectionRow("PINNED", 1), rows.get(0));
        assertEquals(1, ((ClipRow) rows.get(1)).entry().id());
        assertTrue(((ClipRow) rows.get(1)).tags().isEmpty());
        assertEquals(new SectionRow("RECENT", 2), rows.get(2));
        assertEquals(2, ((ClipRow) rows.get(3)).entry().id());
        assertEquals(List.of(alpha, beta), ((ClipRow) rows.get(3)).tags());
        assertEquals(3, ((ClipRow) rows.get(4)).entry().id());
        assertEquals(3, PopupRows.countClips(rows));
    }

    @Test
    void fiftyThousandClipFixtureRemainsDeterministic() {
        List<ClipEntry> clips = new ArrayList<>(50_000);
        for (int i = 0; i < 50_000; i++) {
            boolean pinned = i < 250;
            clips.add(new ClipEntry(
                    i + 1L,
                    "clip-" + i,
                    null,
                    pinned,
                    pinned ? i : null,
                    50_000L - i
            ));
        }

        List<PopupRow> rows = PopupRows.build(clips);

        assertEquals(50_002, rows.size());
        assertEquals(50_000, PopupRows.countClips(rows));
        assertEquals(new SectionRow("PINNED", 250), rows.get(0));
        assertEquals(new SectionRow("RECENT", 49_750), rows.get(251));
    }
}
