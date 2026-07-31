/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.search;

import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable execution snapshot combining parsed operators with popup toolbar filters.
 *
 * Combination rules are intentionally explicit:
 * - toolbar filters and search operators are ANDed;
 * - repeated positive type terms are ORed because one clip has one derived type;
 * - repeated positive tag terms are ANDed;
 * - every negative type/tag term excludes a match;
 * - contradictory scope, type, or tag constraints produce an unsatisfiable plan.
 */
public record SearchExecutionPlan(
        String text,
        ClipViewScope scope,
        ClipContentType toolbarType,
        Long toolbarTagId,
        List<ClipContentType> includedTypes,
        List<ClipContentType> excludedTypes,
        List<String> requiredTagIdentities,
        List<String> excludedTagIdentities,
        boolean unsatisfiable
) {
    public SearchExecutionPlan {
        text = Objects.requireNonNullElse(text, "").trim();
        scope = scope == null ? ClipViewScope.ALL : scope;
        if (toolbarTagId != null && toolbarTagId <= 0) {
            throw new IllegalArgumentException("toolbarTagId must be positive");
        }
        includedTypes = List.copyOf(Objects.requireNonNullElse(includedTypes, List.of()));
        excludedTypes = List.copyOf(Objects.requireNonNullElse(excludedTypes, List.of()));
        requiredTagIdentities = List.copyOf(
                Objects.requireNonNullElse(requiredTagIdentities, List.of())
        );
        excludedTagIdentities = List.copyOf(
                Objects.requireNonNullElse(excludedTagIdentities, List.of())
        );
    }

    public static SearchExecutionPlan combine(
            SearchQuery query,
            ClipViewScope toolbarScope,
            ClipContentType toolbarType,
            Long toolbarTagId
    ) {
        SearchQuery parsed = query == null ? SearchQueryParser.parse("") : query;
        ClipViewScope normalizedToolbarScope =
                toolbarScope == null ? ClipViewScope.ALL : toolbarScope;

        boolean impossible = false;

        ClipViewScope operatorScope = null;
        for (SearchQuery.ScopeTerm term : parsed.scopeTerms()) {
            if (operatorScope == null) {
                operatorScope = term.scope();
            } else if (operatorScope != term.scope()) {
                impossible = true;
            }
        }

        ClipViewScope effectiveScope;
        if (normalizedToolbarScope != ClipViewScope.ALL) {
            effectiveScope = normalizedToolbarScope;
            if (operatorScope != null && operatorScope != normalizedToolbarScope) {
                impossible = true;
            }
        } else {
            effectiveScope = operatorScope == null ? ClipViewScope.ALL : operatorScope;
        }

        LinkedHashSet<ClipContentType> included = new LinkedHashSet<>();
        LinkedHashSet<ClipContentType> excluded = new LinkedHashSet<>();
        for (SearchQuery.TypeTerm term : parsed.typeTerms()) {
            (term.negated() ? excluded : included).add(term.type());
        }

        if (toolbarType != null) {
            if (excluded.contains(toolbarType)) impossible = true;
            if (!included.isEmpty() && !included.contains(toolbarType)) impossible = true;
        } else if (!included.isEmpty() && excluded.containsAll(included)) {
            impossible = true;
        }

        LinkedHashSet<String> requiredTags = new LinkedHashSet<>();
        LinkedHashSet<String> excludedTags = new LinkedHashSet<>();
        for (SearchQuery.TagTerm term : parsed.tagTerms()) {
            (term.negated() ? excludedTags : requiredTags).add(term.identity());
        }
        for (String required : requiredTags) {
            if (excludedTags.contains(required)) {
                impossible = true;
                break;
            }
        }

        return new SearchExecutionPlan(
                parsed.text(),
                effectiveScope,
                toolbarType,
                toolbarTagId,
                List.copyOf(included),
                List.copyOf(excluded),
                List.copyOf(requiredTags),
                List.copyOf(excludedTags),
                impossible
        );
    }

    public boolean derivedTypeFilteringActive() {
        return toolbarType != null || !includedTypes.isEmpty() || !excludedTypes.isEmpty();
    }

    public boolean matchesType(ClipContentType actualType) {
        if (unsatisfiable || actualType == null) return false;
        if (toolbarType != null && actualType != toolbarType) return false;
        if (!includedTypes.isEmpty() && !includedTypes.contains(actualType)) return false;
        return !excludedTypes.contains(actualType);
    }

    public boolean matchesScope(boolean pinned) {
        if (unsatisfiable) return false;
        return switch (scope) {
            case ALL -> true;
            case PINNED -> pinned;
            case RECENT -> !pinned;
        };
    }
}
