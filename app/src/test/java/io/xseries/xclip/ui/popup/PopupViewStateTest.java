/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.data.model.ClipTag;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PopupViewStateTest {

    @Test
    void defaultsToAllScopeWithoutTypeOrTagFilter() {
        PopupViewState state = PopupViewState.defaults();

        assertEquals(ClipViewScope.ALL, state.scope());
        assertNull(state.contentType());
        assertNull(state.tagId());
        assertFalse(state.filtersActive());
    }

    @Test
    void normalizesNullScopeAndReportsActiveFilters() {
        PopupViewState typeOnly = new PopupViewState(null, ClipContentType.JSON);
        PopupViewState pinnedOnly = new PopupViewState(ClipViewScope.PINNED, null);
        PopupViewState tagOnly = new PopupViewState(ClipViewScope.ALL, null, 12L);

        assertEquals(ClipViewScope.ALL, typeOnly.scope());
        assertTrue(typeOnly.filtersActive());
        assertTrue(pinnedOnly.filtersActive());
        assertTrue(tagOnly.filtersActive());
        assertThrows(
                IllegalArgumentException.class,
                () -> new PopupViewState(ClipViewScope.ALL, null, 0L)
        );
    }

    @Test
    void popupRowsExposeStableSectionClipAndTagContracts() {
        ClipEntry entry = new ClipEntry(7L, "content", null, false, null, 10L);
        ClipTag tag = new ClipTag(4L, "Work", 20L);

        PopupRow.SectionRow section = new PopupRow.SectionRow(null, -3);
        PopupRow.ClipRow clip = new PopupRow.ClipRow(entry, List.of(tag));

        assertEquals("", section.title());
        assertEquals(0, section.count());
        assertSame(entry, clip.entry());
        assertEquals(List.of(tag), clip.tags());
        assertThrows(NullPointerException.class, () -> new PopupRow.ClipRow(null));
        assertThrows(
                UnsupportedOperationException.class,
                () -> clip.tags().add(new ClipTag(5L, "Other", 30L))
        );
    }
}
