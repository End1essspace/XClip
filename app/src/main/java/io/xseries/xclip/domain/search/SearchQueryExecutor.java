/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.search;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.domain.model.ClipContentType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Applies the non-SQL portion of one advanced-search execution plan.
 *
 * Text, title, selected-tag, and tag-operator constraints are pushed into the
 * bounded DAO query. This stage rechecks scope and evaluates derived content
 * types while preserving the exact deterministic DAO ordering.
 */
public final class SearchQueryExecutor {

    private SearchQueryExecutor() {}

    public static List<ClipEntry> apply(
            List<ClipEntry> candidates,
            SearchExecutionPlan plan,
            int limit,
            Function<ClipEntry, ClipContentType> classifier
    ) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }

        SearchExecutionPlan effectivePlan = Objects.requireNonNull(plan, "plan");
        Function<ClipEntry, ClipContentType> effectiveClassifier =
                Objects.requireNonNull(classifier, "classifier");
        if (effectivePlan.unsatisfiable()) return List.of();

        List<ClipEntry> result = new ArrayList<>(Math.min(candidates.size(), limit));
        for (ClipEntry entry : candidates) {
            if (entry == null) continue;
            if (!effectivePlan.matchesScope(entry.favorite())) continue;

            if (effectivePlan.derivedTypeFilteringActive()
                    && !effectivePlan.matchesType(effectiveClassifier.apply(entry))) {
                continue;
            }

            result.add(entry);
            if (result.size() >= limit) break;
        }
        return List.copyOf(result);
    }
}
