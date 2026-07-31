/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.search;

import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable result of parsing one advanced-search expression.
 *
 * Milestone 3.1 intentionally stops at parsing. Query execution and popup UI
 * integration are introduced by later milestones, so this model has no DAO or
 * JavaFX dependencies.
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
            name = requireText(name, "name");
            identity = requireText(identity, "identity");
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

        private static String requireText(String value, String field) {
            String normalized = Objects.requireNonNullElse(value, "").trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(field + " is required");
            }
            return normalized;
        }
    }
}
