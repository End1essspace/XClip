/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PopupReloadCacheTest {

    @Test
    void reusesCountAndTagMetadataUntilExplicitInvalidation() {
        PopupReloadCache cache = new PopupReloadCache(8);
        AtomicInteger countLoads = new AtomicInteger();
        AtomicInteger tagLoads = new AtomicInteger();
        ClipTag work = new ClipTag(1, "Work", 10);

        assertEquals(12, cache.totalClipCount(() -> {
            countLoads.incrementAndGet();
            return 12;
        }));
        assertEquals(12, cache.totalClipCount(() -> {
            countLoads.incrementAndGet();
            return 99;
        }));
        assertEquals(1, countLoads.get());

        assertEquals(List.of(work), cache.availableTags(() -> {
            tagLoads.incrementAndGet();
            return List.of(work);
        }));
        assertEquals(List.of(work), cache.availableTags(() -> {
            tagLoads.incrementAndGet();
            return List.of();
        }));
        assertEquals(1, tagLoads.get());

        cache.invalidateTotalClipCount();
        cache.invalidateAvailableTags();

        assertEquals(20, cache.totalClipCount(() -> {
            countLoads.incrementAndGet();
            return 20;
        }));
        assertTrue(cache.availableTags(() -> {
            tagLoads.incrementAndGet();
            return List.of();
        }).isEmpty());

        assertEquals(2, countLoads.get());
        assertEquals(2, tagLoads.get());
    }

    @Test
    void loadsOnlyMissingAssignmentsAndCachesEmptyResults() {
        PopupReloadCache cache = new PopupReloadCache(8);
        ClipTag alpha = new ClipTag(10, "Alpha", 1);
        AtomicInteger loads = new AtomicInteger();
        List<List<Long>> requestedBatches = new ArrayList<>();

        java.util.function.Function<List<Long>, Map<Long, List<ClipTag>>> loader = ids -> {
            loads.incrementAndGet();
            requestedBatches.add(ids);
            Map<Long, List<ClipTag>> loaded = new LinkedHashMap<>();
            if (ids.contains(1L)) loaded.put(1L, List.of(alpha));
            if (ids.contains(3L)) loaded.put(3L, List.of(alpha));
            return loaded;
        };

        Map<Long, List<ClipTag>> first =
                cache.tagAssignments(List.of(1L, 2L, 1L), loader);
        assertEquals(List.of(alpha), first.get(1L));
        assertEquals(List.of(), first.get(2L));
        assertEquals(List.of(1L, 2L), requestedBatches.get(0));

        Map<Long, List<ClipTag>> second =
                cache.tagAssignments(List.of(2L, 3L), loader);
        assertEquals(List.of(), second.get(2L));
        assertEquals(List.of(alpha), second.get(3L));
        assertEquals(List.of(3L), requestedBatches.get(1));
        assertEquals(2, loads.get());

        cache.invalidateTagAssignments(List.of(2L));
        cache.tagAssignments(List.of(1L, 2L), loader);

        assertEquals(List.of(2L), requestedBatches.get(2));
        assertEquals(3, loads.get());
        assertEquals(3, cache.cachedAssignmentCount());
    }


    @Test
    void currentResultRemainsCompleteWhenBatchExceedsCacheCapacity() {
        PopupReloadCache cache = new PopupReloadCache(2);
        ClipTag tag = new ClipTag(1, "Tag", 1);

        Map<Long, List<ClipTag>> result = cache.tagAssignments(
                List.of(1L, 2L, 3L),
                ids -> Map.of(
                        1L, List.of(tag),
                        2L, List.of(tag),
                        3L, List.of(tag)
                )
        );

        assertEquals(List.of(tag), result.get(1L));
        assertEquals(List.of(tag), result.get(2L));
        assertEquals(List.of(tag), result.get(3L));
        assertEquals(2, cache.cachedAssignmentCount());
    }

    @Test
    void clearInvalidatesEveryReloadMetadataKind() {
        PopupReloadCache cache = new PopupReloadCache(4);
        ClipTag tag = new ClipTag(1, "Tag", 1);

        cache.totalClipCount(() -> 4);
        cache.availableTags(() -> List.of(tag));
        cache.tagAssignments(
                List.of(1L),
                ids -> Map.of(1L, List.of(tag))
        );

        cache.clear();

        AtomicInteger reloads = new AtomicInteger();
        assertEquals(7, cache.totalClipCount(() -> {
            reloads.incrementAndGet();
            return 7;
        }));
        assertTrue(cache.availableTags(() -> {
            reloads.incrementAndGet();
            return List.of();
        }).isEmpty());
        cache.tagAssignments(List.of(1L), ids -> {
            reloads.incrementAndGet();
            return Map.of();
        });

        assertEquals(3, reloads.get());
    }
}
