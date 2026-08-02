/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Reuses popup reload metadata that is stable across incremental search requests.
 *
 * Clip rows themselves are never cached here. The cache only retains:
 * - total persisted clip count until a storage mutation invalidates it;
 * - the tag library until tag metadata changes;
 * - bounded immutable tag assignments keyed by clip id.
 *
 * Loaders are invoked by PopupWindow's single DB executor. Public invalidation
 * methods remain thread-safe because external cleanup can request a refresh
 * outside the JavaFX Application Thread.
 */
public final class PopupReloadCache {

    private final BoundedLruCache<Long, List<ClipTag>> assignmentsByClipId;

    private List<ClipTag> availableTags = List.of();
    private boolean availableTagsLoaded;

    private int totalClipCount;
    private boolean totalClipCountLoaded;

    public PopupReloadCache(int tagAssignmentCapacity) {
        assignmentsByClipId = new BoundedLruCache<>(tagAssignmentCapacity);
    }

    public synchronized int totalClipCount(IntSupplier loader) {
        Objects.requireNonNull(loader, "loader");
        if (!totalClipCountLoaded) {
            totalClipCount = Math.max(0, loader.getAsInt());
            totalClipCountLoaded = true;
        }
        return totalClipCount;
    }

    public synchronized List<ClipTag> availableTags(Supplier<List<ClipTag>> loader) {
        Objects.requireNonNull(loader, "loader");
        if (!availableTagsLoaded) {
            availableTags = immutableTags(loader.get());
            availableTagsLoaded = true;
        }
        return availableTags;
    }

    /**
     * Returns assignments in caller id order and loads only ids not already cached.
     * Empty assignments are cached too, preventing repeated lookups for untagged clips.
     */
    public Map<Long, List<ClipTag>> tagAssignments(
            Collection<Long> clipIds,
            Function<List<Long>, Map<Long, List<ClipTag>>> loader
    ) {
        Objects.requireNonNull(loader, "loader");
        if (clipIds == null || clipIds.isEmpty()) return Map.of();

        LinkedHashSet<Long> orderedIds = new LinkedHashSet<>();
        for (Long clipId : clipIds) {
            if (clipId != null && clipId > 0) orderedIds.add(clipId);
        }
        if (orderedIds.isEmpty()) return Map.of();

        List<Long> missing = new ArrayList<>();
        LinkedHashMap<Long, List<ClipTag>> resolved = new LinkedHashMap<>();
        for (Long clipId : orderedIds) {
            List<ClipTag> cached = assignmentsByClipId.get(clipId);
            if (cached == null) {
                missing.add(clipId);
            } else {
                resolved.put(clipId, cached);
            }
        }

        if (!missing.isEmpty()) {
            Map<Long, List<ClipTag>> loaded = loader.apply(List.copyOf(missing));
            Map<Long, List<ClipTag>> effectiveLoaded =
                    loaded == null ? Map.of() : loaded;

            for (Long clipId : missing) {
                List<ClipTag> tags = immutableTags(effectiveLoaded.get(clipId));
                assignmentsByClipId.put(clipId, tags);
                resolved.put(clipId, tags);
            }
        }

        LinkedHashMap<Long, List<ClipTag>> ordered = new LinkedHashMap<>();
        for (Long clipId : orderedIds) {
            ordered.put(clipId, resolved.getOrDefault(clipId, List.of()));
        }
        return Collections.unmodifiableMap(ordered);
    }

    public synchronized void invalidateTotalClipCount() {
        totalClipCountLoaded = false;
    }

    public synchronized void invalidateAvailableTags() {
        availableTags = List.of();
        availableTagsLoaded = false;
    }

    public void invalidateTagAssignments(Collection<Long> clipIds) {
        assignmentsByClipId.removeKeys(clipIds);
    }

    public void invalidateAllTagAssignments() {
        assignmentsByClipId.clear();
    }

    public void clear() {
        invalidateTotalClipCount();
        invalidateAvailableTags();
        invalidateAllTagAssignments();
    }

    int cachedAssignmentCount() {
        return assignmentsByClipId.size();
    }

    private static List<ClipTag> immutableTags(List<ClipTag> tags) {
        if (tags == null || tags.isEmpty()) return List.of();

        List<ClipTag> valid = new ArrayList<>(tags.size());
        for (ClipTag tag : tags) {
            if (tag != null) valid.add(tag);
        }
        return List.copyOf(valid);
    }
}
