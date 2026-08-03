/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Small synchronized access-order cache with a strict entry bound.
 *
 * Popup classification can run on the DB executor while virtualized cells read
 * cached values on the JavaFX thread, so synchronization is intentional.
 */
public final class BoundedLruCache<K, V> {

    private final int capacity;
    private final LinkedHashMap<K, V> values;

    public BoundedLruCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.values = new LinkedHashMap<>(Math.min(capacity, 256), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > BoundedLruCache.this.capacity;
            }
        };
    }

    public synchronized V get(K key) {
        return values.get(key);
    }

    public synchronized void put(K key, V value) {
        values.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
    }

    public synchronized V remove(K key) {
        return values.remove(key);
    }

    public synchronized void removeKeys(Collection<? extends K> keys) {
        if (keys == null || keys.isEmpty()) return;
        for (K key : keys) values.remove(key);
    }

    public synchronized void clear() {
        values.clear();
    }

    public synchronized int size() {
        return values.size();
    }

    public int capacity() {
        return capacity;
    }
}
