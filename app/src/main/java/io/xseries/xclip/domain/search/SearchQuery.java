
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.search;

import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;
import io.xseries.xclip.util.TextValues;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable result of parsing one advanced-search expression.
 *
 * The model has no DAO or JavaFX dependencies and can be consumed by both
 * execution and presentation layers.
 */
public record SearchQuery(
        String rawQuery,
        String text,
        List<TypeTerm> typeTerms,
        List<ScopeTerm> scopeTerms,
        List<TagTerm> tagTerms,
        List<SearchQueryIssue> issues,
        boolean fallbackApplied
) {
    public SearchQuery {
        rawQuery = Objects.requireNonNullElse(rawQuery, "");
        text = Objects.requireNonNullElse(text, "").trim();
        typeTerms = List.copyOf(Objects.requireNonNullElse(typeTerms, List.of()));
        scopeTerms = List.copyOf(Objects.requireNonNullElse(scopeTerms, List.of()));
        tagTerms = List.copyOf(Objects.requireNonNullElse(tagTerms, List.of()));
        issues = List.copyOf(Objects.requireNonNullElse(issues, List.of()));
    }

    public boolean hasOperators() {
        return !typeTerms.isEmpty() || !scopeTerms.isEmpty() || !tagTerms.isEmpty();
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public record TypeTerm(ClipContentType type, boolean negated) {
        public TypeTerm {
            type = Objects.requireNonNull(type, "type");
        }

        public String canonicalText() {
            return (negated ? "-" : "")
                    + "type:"
                    + type.name().toLowerCase(Locale.ROOT);
        }
    }

    public record ScopeTerm(ClipViewScope scope) {
        public ScopeTerm {
            scope = Objects.requireNonNull(scope, "scope");
            if (scope == ClipViewScope.ALL) {
                throw new IllegalArgumentException("Advanced search scope cannot be ALL");
            }
        }

        public String canonicalText() {
            return "is:" + scope.name().toLowerCase(Locale.ROOT);
        }
    }

    public record TagTerm(String name, String identity, boolean negated) {
        public TagTerm {
            name = TextValues.requireNonBlank(name, "name");
            identity = TextValues.requireNonBlank(identity, "identity");
        }

        public String canonicalText() {
            String escaped = name
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
            boolean quoted = name.chars().anyMatch(Character::isWhitespace)
                    || name.indexOf('"') >= 0
                    || name.indexOf('\\') >= 0;
            return (negated ? "-" : "")
                    + "tag:"
                    + (quoted ? "\"" + escaped + "\"" : escaped);
        }

    }
}
