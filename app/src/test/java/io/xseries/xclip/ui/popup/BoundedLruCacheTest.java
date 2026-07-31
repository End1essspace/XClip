/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoundedLruCacheTest {

    @Test
    void evictsLeastRecentlyUsedEntryAtCapacity() {
        BoundedLruCache<Integer, String> cache = new BoundedLruCache<>(3);
        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        assertEquals("one", cache.get(1));
        cache.put(4, "four");

        assertNull(cache.get(2));
        assertEquals("one", cache.get(1));
        assertEquals(3, cache.size());
    }

    @Test
    void remainsBoundedAcrossFiftyThousandEntries() {
        BoundedLruCache<Integer, Integer> cache = new BoundedLruCache<>(512);
        for (int i = 0; i < 50_000; i++) {
            cache.put(i, i);
        }
        assertEquals(512, cache.size());
    }

    @Test
    void removesDeletedClipKeysInOneOperation() {
        BoundedLruCache<Long, String> cache = new BoundedLruCache<>(8);
        cache.put(1L, "one");
        cache.put(2L, "two");
        cache.put(3L, "three");

        cache.removeKeys(List.of(1L, 3L));

        assertNull(cache.get(1L));
        assertEquals("two", cache.get(2L));
        assertNull(cache.get(3L));
    }
}
