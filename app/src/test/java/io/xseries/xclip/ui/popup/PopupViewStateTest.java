
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PopupViewStateTest {

    @Test
    void defaultsToAllScopeWithoutTypeFilter() {
        PopupViewState state = PopupViewState.defaults();

        assertEquals(ClipViewScope.ALL, state.scope());
        assertNull(state.contentType());
        assertFalse(state.filtersActive());
    }

    @Test
    void normalizesNullScopeAndReportsActiveFilters() {
        PopupViewState typeOnly = new PopupViewState(null, ClipContentType.JSON);
        PopupViewState pinnedOnly = new PopupViewState(ClipViewScope.PINNED, null);

        assertEquals(ClipViewScope.ALL, typeOnly.scope());
        assertTrue(typeOnly.filtersActive());
        assertTrue(pinnedOnly.filtersActive());
    }

    @Test
    void popupRowsExposeStableSectionAndClipContracts() {
        ClipEntry entry = new ClipEntry(7L, "content", null, false, null, 10L);

        PopupRow.SectionRow section = new PopupRow.SectionRow(null, -3);
        PopupRow.ClipRow clip = new PopupRow.ClipRow(entry);

        assertEquals("", section.title());
        assertEquals(0, section.count());
        assertSame(entry, clip.entry());
        assertThrows(NullPointerException.class, () -> new PopupRow.ClipRow(null));
    }
}
