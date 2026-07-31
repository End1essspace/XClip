/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.search;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchQueryExecutorTest {

    @Test
    void preservesDaoOrderingAndStopsAtUiLimit() {
        List<ClipEntry> source = List.of(
                entry(1, true, 0, 400),
                entry(2, false, null, 300),
                entry(3, false, null, 200),
                entry(4, false, null, 100)
        );
        SearchExecutionPlan plan = SearchExecutionPlan.combine(
                SearchQueryParser.parse("-type:text"),
                ClipViewScope.ALL,
                null,
                null
        );

        List<ClipEntry> result = SearchQueryExecutor.apply(
                source,
                plan,
                2,
                entry -> entry.id() == 2L ? ClipContentType.TEXT : ClipContentType.CODE
        );

        assertEquals(List.of(1L, 3L), result.stream().map(ClipEntry::id).toList());
    }

    @Test
    void rechecksEffectiveScopeAndReturnsImmutableResults() {
        List<ClipEntry> source = List.of(
                entry(1, true, 0, 300),
                entry(2, false, null, 200)
        );
        SearchExecutionPlan plan = SearchExecutionPlan.combine(
                SearchQueryParser.parse("is:recent"),
                ClipViewScope.ALL,
                null,
                null
        );

        List<ClipEntry> result = SearchQueryExecutor.apply(
                source,
                plan,
                10,
                entry -> ClipContentType.TEXT
        );

        assertEquals(List.of(2L), result.stream().map(ClipEntry::id).toList());
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> result.add(entry(3, false, null, 100))
        );
    }

    @Test
    void unsatisfiablePlanSkipsClassifierAndReturnsEmpty() {
        SearchExecutionPlan plan = SearchExecutionPlan.combine(
                SearchQueryParser.parse("type:url -type:url"),
                ClipViewScope.ALL,
                null,
                null
        );
        int[] calls = {0};

        List<ClipEntry> result = SearchQueryExecutor.apply(
                List.of(entry(1, false, null, 100)),
                plan,
                10,
                entry -> {
                    calls[0]++;
                    return ClipContentType.URL;
                }
        );

        assertTrue(result.isEmpty());
        assertEquals(0, calls[0]);
    }

    private static ClipEntry entry(
            long id,
            boolean pinned,
            Integer pinOrder,
            long timestamp
    ) {
        return new ClipEntry(id, "clip-" + id, null, pinned, pinOrder, timestamp);
    }
}
