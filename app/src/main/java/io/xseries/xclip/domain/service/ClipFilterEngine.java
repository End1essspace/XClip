/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.service;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Applies popup view filters without changing the source ordering.
 *
 * Scope filtering can also be pushed into the DAO for efficiency, but this
 * engine remains the single deterministic rule set for combining scope,
 * derived content type, and the final UI limit.
 */
public final class ClipFilterEngine {

    private ClipFilterEngine() {}

    public static List<ClipEntry> apply(
            List<ClipEntry> source,
            ClipViewScope scope,
            ClipContentType contentType,
            int limit
    ) {
        return apply(
                source,
                scope,
                contentType,
                limit,
                entry -> ClipContentClassifier.classify(entry == null ? null : entry.content())
        );
    }

    public static List<ClipEntry> apply(
            List<ClipEntry> source,
            ClipViewScope scope,
            ClipContentType contentType,
            int limit,
            Function<ClipEntry, ClipContentType> classifier
    ) {
        if (source == null || source.isEmpty() || limit <= 0) {
            return List.of();
        }

        ClipViewScope effectiveScope = scope == null ? ClipViewScope.ALL : scope;
        Function<ClipEntry, ClipContentType> effectiveClassifier =
                Objects.requireNonNull(classifier, "classifier");

        List<ClipEntry> result = new ArrayList<>(Math.min(source.size(), limit));

        for (ClipEntry entry : source) {
            if (entry == null) continue;
            if (!matchesScope(entry, effectiveScope)) continue;

            if (contentType != null && effectiveClassifier.apply(entry) != contentType) {
                continue;
            }

            result.add(entry);
            if (result.size() >= limit) break;
        }

        return List.copyOf(result);
    }

    private static boolean matchesScope(ClipEntry entry, ClipViewScope scope) {
        return switch (scope) {
            case ALL -> true;
            case PINNED -> entry.favorite();
            case RECENT -> !entry.favorite();
        };
    }
}
