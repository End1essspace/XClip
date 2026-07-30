/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.service;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClipFilterEngineTest {

    @Test
    void combinesScopeAndContentTypeWithoutChangingSourceOrder() {
        List<ClipEntry> source = List.of(
                clip(1, "https://xclip.dev", true, 0),
                clip(2, "Pinned note", true, 1),
                clip(3, "https://example.com", false, null),
                clip(4, "Recent note", false, null)
        );

        List<ClipEntry> result = ClipFilterEngine.apply(
                source,
                ClipViewScope.PINNED,
                ClipContentType.URL,
                20
        );

        assertEquals(List.of(1L), result.stream().map(ClipEntry::id).toList());
    }

    @Test
    void appliesLimitAfterFiltering() {
        List<ClipEntry> source = List.of(
                clip(1, "Pinned", true, 0),
                clip(2, "Recent A", false, null),
                clip(3, "Recent B", false, null),
                clip(4, "Recent C", false, null)
        );

        List<ClipEntry> result = ClipFilterEngine.apply(
                source,
                ClipViewScope.RECENT,
                null,
                2
        );

        assertEquals(List.of(2L, 3L), result.stream().map(ClipEntry::id).toList());
    }

    @Test
    void allScopeAndAllTypesReturnTheOriginalPrefix() {
        List<ClipEntry> source = List.of(
                clip(1, "A", true, 0),
                clip(2, "B", false, null),
                clip(3, "C", false, null)
        );

        List<ClipEntry> result = ClipFilterEngine.apply(
                source,
                ClipViewScope.ALL,
                null,
                2
        );

        assertEquals(List.of(1L, 2L), result.stream().map(ClipEntry::id).toList());
    }

    @Test
    void emptyOrZeroLimitProducesNoRows() {
        assertEquals(List.of(), ClipFilterEngine.apply(
                List.of(clip(1, "A", false, null)),
                ClipViewScope.ALL,
                null,
                0
        ));
        assertEquals(List.of(), ClipFilterEngine.apply(
                List.of(),
                ClipViewScope.ALL,
                null,
                10
        ));
    }

    private ClipEntry clip(long id, String content, boolean favorite, Integer pinOrder) {
        return new ClipEntry(id, content, null, favorite, pinOrder, id * 1_000L);
    }
}
