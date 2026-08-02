
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.search;

import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;
import io.xseries.xclip.domain.service.TagNamePolicy;
import io.xseries.xclip.domain.service.TagNamePolicy.NormalizedTagName;
import io.xseries.xclip.util.TextValues;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic, local-only parser for XClip advanced-search syntax.
 *
 * Supported operators:
 * - type:url, type:code, type:path, type:json, type:command, type:text
 * - is:pinned, is:recent
 * - tag:work and tag:"Project Work"
 * - negative type and tag operators, for example -type:text and -tag:private
 *
 * Unknown syntax remains ordinary search text. Recognized but invalid operator
 * fragments also remain text and produce a non-fatal diagnostic. An
 * unterminated quote falls back to the complete raw query.
 */
public final class SearchQueryParser {

    private SearchQueryParser() {}

    public static SearchQuery parse(String rawQuery) {
        String raw = Objects.requireNonNullElse(rawQuery, "");
        Tokenization tokenization = tokenize(raw);

        if (tokenization.unterminatedQuoteStart() >= 0) {
            SearchQueryIssue issue = new SearchQueryIssue(
                    SearchQueryIssue.Code.UNTERMINATED_QUOTE,
                    "Unterminated quoted value",
                    tokenization.unterminatedQuoteStart(),
                    raw.length()
            );
            return new SearchQuery(
                    raw,
                    TextValues.collapseWhitespace(raw),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(issue),
                    true
            );
        }

        List<SearchQuery.TypeTerm> typeTerms = new ArrayList<>();
        List<SearchQuery.ScopeTerm> scopeTerms = new ArrayList<>();
        List<SearchQuery.TagTerm> tagTerms = new ArrayList<>();
        List<SearchQueryIssue> issues = new ArrayList<>();
        List<String> textTerms = new ArrayList<>();
        boolean fallbackApplied = false;

        for (Token token : tokenization.tokens()) {
            ParseOutcome outcome = parseOperator(token);

            switch (outcome.kind()) {
                case TEXT -> textTerms.add(token.value());
                case TYPE -> typeTerms.add(outcome.typeTerm());
                case SCOPE -> scopeTerms.add(outcome.scopeTerm());
                case TAG -> tagTerms.add(outcome.tagTerm());
                case INVALID -> {
                    textTerms.add(token.value());
                    issues.add(outcome.issue());
                    fallbackApplied = true;
                }
            }
        }

        return new SearchQuery(
                raw,
                TextValues.collapseWhitespace(String.join(" ", textTerms)),
                typeTerms,
                scopeTerms,
                tagTerms,
                issues,
                fallbackApplied
        );
    }

    private static ParseOutcome parseOperator(Token token) {
        String value = token.value();
        int colon = value.indexOf(':');
        if (colon <= 0) return ParseOutcome.text();

        String operator = value.substring(0, colon);
        String operand = value.substring(colon + 1);
        boolean negated = operator.startsWith("-");

        if (negated) operator = operator.substring(1);
        String normalizedOperator = operator.toLowerCase(Locale.ROOT);

        if (!normalizedOperator.equals("type")
                && !normalizedOperator.equals("is")
                && !normalizedOperator.equals("tag")) {
            return ParseOutcome.text();
        }

        if (operand.isBlank()) {
            return invalid(
                    token,
                    SearchQueryIssue.Code.MISSING_VALUE,
                    "Operator value is required"
            );
        }

        return switch (normalizedOperator) {
            case "type" -> parseType(token, operand, negated);
            case "is" -> parseScope(token, operand, negated);
            case "tag" -> parseTag(token, operand, negated);
            default -> ParseOutcome.text();
        };
    }

    private static ParseOutcome parseType(Token token, String operand, boolean negated) {
        try {
            ClipContentType type = ClipContentType.valueOf(
                    operand.trim().toUpperCase(Locale.ROOT)
            );
            return ParseOutcome.type(new SearchQuery.TypeTerm(type, negated));
        } catch (IllegalArgumentException ignored) {
            return invalid(
                    token,
                    SearchQueryIssue.Code.INVALID_VALUE,
                    "Unknown content type: " + operand
            );
        }
    }

    private static ParseOutcome parseScope(Token token, String operand, boolean negated) {
        if (negated) {
            return invalid(
                    token,
                    SearchQueryIssue.Code.UNSUPPORTED_NEGATION,
                    "The is: operator does not support negation"
            );
        }

        String normalized = operand.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pinned" -> ParseOutcome.scope(
                    new SearchQuery.ScopeTerm(ClipViewScope.PINNED)
            );
            case "recent" -> ParseOutcome.scope(
                    new SearchQuery.ScopeTerm(ClipViewScope.RECENT)
            );
            default -> invalid(
                    token,
                    SearchQueryIssue.Code.INVALID_VALUE,
                    "Unknown state: " + operand
            );
        };
    }

    private static ParseOutcome parseTag(Token token, String operand, boolean negated) {
        try {
            NormalizedTagName normalized = TagNamePolicy.normalize(operand);
            return ParseOutcome.tag(new SearchQuery.TagTerm(
                    normalized.displayName(),
                    normalized.identity(),
                    negated
            ));
        } catch (IllegalArgumentException error) {
            return invalid(
                    token,
                    SearchQueryIssue.Code.INVALID_VALUE,
                    error.getMessage() == null ? "Invalid tag name" : error.getMessage()
            );
        }
    }

    private static ParseOutcome invalid(
            Token token,
            SearchQueryIssue.Code code,
            String message
    ) {
        return ParseOutcome.invalid(new SearchQueryIssue(
                code,
                message,
                token.startIndex(),
                token.endIndex()
        ));
    }

    private static Tokenization tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        int tokenStart = -1;
        int quoteStart = -1;
        boolean inQuote = false;
        boolean escaping = false;

        for (int index = 0; index < source.length(); index++) {
            char ch = source.charAt(index);

            if (tokenStart < 0 && Character.isWhitespace(ch)) {
                continue;
            }
            if (tokenStart < 0) tokenStart = index;

            if (inQuote) {
                if (escaping) {
                    if (ch == '"' || ch == '\\') {
                        current.append(ch);
                    } else {
                        current.append('\\').append(ch);
                    }
                    escaping = false;
                    continue;
                }
                if (ch == '\\') {
                    escaping = true;
                    continue;
                }
                if (ch == '"') {
                    inQuote = false;
                    quoteStart = -1;
                    continue;
                }
                current.append(ch);
                continue;
            }

            if (ch == '"') {
                inQuote = true;
                quoteStart = index;
                continue;
            }

            if (Character.isWhitespace(ch)) {
                tokens.add(new Token(current.toString(), tokenStart, index));
                current.setLength(0);
                tokenStart = -1;
                continue;
            }

            current.append(ch);
        }

        if (escaping) current.append('\\');

        if (inQuote) {
            return new Tokenization(List.of(), quoteStart);
        }

        if (tokenStart >= 0) {
            tokens.add(new Token(current.toString(), tokenStart, source.length()));
        }

        return new Tokenization(List.copyOf(tokens), -1);
    }

    private record Token(String value, int startIndex, int endIndex) {}

    private record Tokenization(List<Token> tokens, int unterminatedQuoteStart) {}

    private enum OutcomeKind {
        TEXT,
        TYPE,
        SCOPE,
        TAG,
        INVALID
    }

    private record ParseOutcome(
            OutcomeKind kind,
            SearchQuery.TypeTerm typeTerm,
            SearchQuery.ScopeTerm scopeTerm,
            SearchQuery.TagTerm tagTerm,
            SearchQueryIssue issue
    ) {
        private static ParseOutcome text() {
            return new ParseOutcome(OutcomeKind.TEXT, null, null, null, null);
        }

        private static ParseOutcome type(SearchQuery.TypeTerm term) {
            return new ParseOutcome(OutcomeKind.TYPE, term, null, null, null);
        }

        private static ParseOutcome scope(SearchQuery.ScopeTerm term) {
            return new ParseOutcome(OutcomeKind.SCOPE, null, term, null, null);
        }

        private static ParseOutcome tag(SearchQuery.TagTerm term) {
            return new ParseOutcome(OutcomeKind.TAG, null, null, term, null);
        }

        private static ParseOutcome invalid(SearchQueryIssue issue) {
            return new ParseOutcome(OutcomeKind.INVALID, null, null, null, issue);
        }
    }
}
