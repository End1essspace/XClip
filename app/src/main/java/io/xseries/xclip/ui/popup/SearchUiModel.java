
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipTag;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.search.SearchQuery;
import io.xseries.xclip.domain.search.SearchQueryIssue;
import io.xseries.xclip.domain.search.SearchQueryParser;
import io.xseries.xclip.util.TextValues;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Pure presentation model for the advanced-search assistance surface.
 *
 * Parsing and execution remain owned by the domain layer. This class only
 * derives compact operator chips, inline diagnostics, and deterministic
 * completion suggestions from the same parser result.
 */
public final class SearchUiModel {

    public static final int MAX_VISIBLE_CHIPS = 6;
    public static final int MAX_SUGGESTIONS = 5;

    public enum OperatorKind {
        TYPE,
        SCOPE,
        TAG
    }

    public enum MessageTone {
        HINT,
        ERROR
    }

    public record OperatorChip(
            String text,
            OperatorKind kind,
            boolean negated
    ) {
        public OperatorChip {
            text = TextValues.requireNonBlank(text, "text");
            kind = Objects.requireNonNull(kind, "kind");
        }
    }

    public record AppliedQuery(String query, int caretPosition) {
        public AppliedQuery {
            query = Objects.requireNonNullElse(query, "");
            if (caretPosition < 0 || caretPosition > query.length()) {
                throw new IllegalArgumentException("caretPosition is outside the query");
            }
        }
    }

    public record Suggestion(
            String label,
            String replacement,
            int replaceStart,
            int replaceEnd
    ) {
        public Suggestion {
            label = TextValues.requireNonBlank(label, "label");
            replacement = TextValues.requireNonBlank(replacement, "replacement");
            if (replaceStart < 0) throw new IllegalArgumentException("replaceStart cannot be negative");
            if (replaceEnd < replaceStart) {
                throw new IllegalArgumentException("replaceEnd cannot precede replaceStart");
            }
        }

        public AppliedQuery applyTo(String rawQuery) {
            String source = Objects.requireNonNullElse(rawQuery, "");
            int start = Math.min(replaceStart, source.length());
            int end = Math.min(Math.max(start, replaceEnd), source.length());

            String before = source.substring(0, start);
            String after = source.substring(end);
            StringBuilder insertion = new StringBuilder(replacement);

            if (!after.isEmpty() && !Character.isWhitespace(after.charAt(0))) {
                insertion.append(' ');
            } else if (after.isEmpty()) {
                insertion.append(' ');
            }

            String result = before + insertion + after;
            int caret = before.length() + insertion.length();
            return new AppliedQuery(result, caret);
        }
    }

    public record State(
            String textRemainder,
            List<OperatorChip> chips,
            int hiddenChipCount,
            List<Suggestion> suggestions,
            String message,
            MessageTone messageTone,
            boolean visible
    ) {
        public State {
            textRemainder = Objects.requireNonNullElse(textRemainder, "");
            chips = List.copyOf(Objects.requireNonNullElse(chips, List.of()));
            if (hiddenChipCount < 0) {
                throw new IllegalArgumentException("hiddenChipCount cannot be negative");
            }
            suggestions = List.copyOf(Objects.requireNonNullElse(suggestions, List.of()));
            message = Objects.requireNonNullElse(message, "");
            messageTone = Objects.requireNonNull(messageTone, "messageTone");
        }
    }

    private SearchUiModel() {}

    public static State build(
            String rawQuery,
            int caretPosition,
            List<ClipTag> availableTags,
            boolean focused
    ) {
        String raw = Objects.requireNonNullElse(rawQuery, "");
        int caret = Math.max(0, Math.min(caretPosition, raw.length()));
        SearchQuery parsed = SearchQueryParser.parse(raw);

        List<OperatorChip> allChips = operatorChips(parsed);
        int visibleChipCount = Math.min(MAX_VISIBLE_CHIPS, allChips.size());
        List<OperatorChip> visibleChips = List.copyOf(
                allChips.subList(0, visibleChipCount)
        );
        int hiddenChipCount = allChips.size() - visibleChipCount;

        boolean completionPending = isCompletionPending(raw, caret, parsed);
        List<Suggestion> suggestions = focused && (!parsed.hasIssues() || completionPending)
                ? suggestions(raw, caret, availableTags, parsed)
                : List.of();

        String message;
        MessageTone tone;
        if (completionPending) {
            message = "Choose a value for the current search operator.";
            tone = MessageTone.HINT;
        } else if (parsed.hasIssues()) {
            SearchQueryIssue issue = parsed.issues().get(0);
            message = issue.message() + " — this fragment is treated as ordinary text.";
            tone = MessageTone.ERROR;
        } else if (focused && raw.isBlank()) {
            message = "Use type:, is:, tag:, -type:, or -tag:. Quote tag names that contain spaces.";
            tone = MessageTone.HINT;
        } else if (focused && parsed.hasOperators()) {
            message = parsed.text().isEmpty()
                    ? "Operators filter results. Add ordinary words to search clip text, titles, and tag names."
                    : "Only the ordinary text part is highlighted in matching clips.";
            tone = MessageTone.HINT;
        } else if (focused) {
            message = "Add operators such as type:url, is:pinned, or tag:work.";
            tone = MessageTone.HINT;
        } else {
            message = "";
            tone = MessageTone.HINT;
        }

        return new State(
                parsed.text(),
                visibleChips,
                hiddenChipCount,
                suggestions,
                message,
                tone,
                focused || parsed.hasOperators() || parsed.hasIssues()
        );
    }

    private static boolean isCompletionPending(
            String raw,
            int caret,
            SearchQuery parsed
    ) {
        if (parsed.issues().size() != 1
                || parsed.issues().get(0).code() != SearchQueryIssue.Code.MISSING_VALUE) {
            return false;
        }

        TokenRange range = currentTokenRange(raw, caret);
        if (range.start() == range.end()) return false;

        String token = raw.substring(range.start(), range.end())
                .toLowerCase(Locale.ROOT);
        return token.equals("type:")
                || token.equals("-type:")
                || token.equals("is:")
                || token.equals("tag:")
                || token.equals("-tag:");
    }

    private static List<OperatorChip> operatorChips(SearchQuery parsed) {
        List<OperatorChip> chips = new ArrayList<>();

        for (SearchQuery.TypeTerm term : parsed.typeTerms()) {
            chips.add(new OperatorChip(
                    term.canonicalText(),
                    OperatorKind.TYPE,
                    term.negated()
            ));
        }
        for (SearchQuery.ScopeTerm term : parsed.scopeTerms()) {
            chips.add(new OperatorChip(
                    term.canonicalText(),
                    OperatorKind.SCOPE,
                    false
            ));
        }
        for (SearchQuery.TagTerm term : parsed.tagTerms()) {
            chips.add(new OperatorChip(
                    term.canonicalText(),
                    OperatorKind.TAG,
                    term.negated()
            ));
        }

        return List.copyOf(chips);
    }

    private static List<Suggestion> suggestions(
            String raw,
            int caret,
            List<ClipTag> availableTags,
            SearchQuery parsed
    ) {
        TokenRange range = currentTokenRange(raw, caret);
        String token = raw.substring(range.start(), Math.min(caret, range.end()));
        String normalizedToken = token.toLowerCase(Locale.ROOT);

        List<String> candidates = suggestionCandidates(availableTags, normalizedToken);
        Set<String> activeOperators = activeCanonicalOperators(parsed);
        List<Suggestion> result = new ArrayList<>();

        for (String candidate : candidates) {
            if (!normalizedToken.isBlank()
                    && !matchesCandidate(candidate, normalizedToken)) {
                continue;
            }
            if (candidate.equalsIgnoreCase(token)) continue;
            if (activeOperators.contains(candidate.toLowerCase(Locale.ROOT))) continue;

            result.add(new Suggestion(
                    candidate,
                    candidate,
                    range.start(),
                    range.end()
            ));
            if (result.size() >= MAX_SUGGESTIONS) break;
        }

        return List.copyOf(result);
    }

    private static boolean matchesCandidate(
            String candidate,
            String normalizedToken
    ) {
        String normalizedCandidate = candidate.toLowerCase(Locale.ROOT);
        if (normalizedCandidate.startsWith(normalizedToken)) return true;

        boolean tagToken = normalizedToken.startsWith("tag:")
                || normalizedToken.startsWith("-tag:");
        if (!tagToken) return false;

        int tokenColon = normalizedToken.indexOf(':');
        int candidateColon = normalizedCandidate.indexOf(':');
        if (tokenColon < 0 || candidateColon < 0) return false;

        String tokenOperand = normalizedToken.substring(tokenColon + 1);
        String candidateOperand = normalizedCandidate.substring(candidateColon + 1);
        if (tokenOperand.startsWith("\"")) tokenOperand = tokenOperand.substring(1);
        if (candidateOperand.startsWith("\"")) candidateOperand = candidateOperand.substring(1);

        return candidateOperand.startsWith(tokenOperand);
    }

    private static List<String> suggestionCandidates(
            List<ClipTag> availableTags,
            String normalizedToken
    ) {
        List<String> positiveTypes = new ArrayList<>();
        List<String> negativeTypes = new ArrayList<>();
        for (ClipContentType type : ClipContentType.values()) {
            String value = type.name().toLowerCase(Locale.ROOT);
            positiveTypes.add("type:" + value);
            negativeTypes.add("-type:" + value);
        }

        List<String> positiveTags = new ArrayList<>();
        List<String> negativeTags = new ArrayList<>();
        if (availableTags != null) {
            for (ClipTag tag : availableTags) {
                if (tag == null || tag.name() == null || tag.name().isBlank()) continue;
                String value = canonicalTag(tag.name());
                positiveTags.add("tag:" + value);
                negativeTags.add("-tag:" + value);
            }
        }

        if (normalizedToken.startsWith("-type")) return List.copyOf(negativeTypes);
        if (normalizedToken.startsWith("type")) return List.copyOf(positiveTypes);
        if (normalizedToken.startsWith("-tag")) return List.copyOf(negativeTags);
        if (normalizedToken.startsWith("tag")) return List.copyOf(positiveTags);
        if (normalizedToken.startsWith("is")) {
            return List.of("is:pinned", "is:recent");
        }

        List<String> all = new ArrayList<>();
        if (normalizedToken.isBlank()) {
            all.add("type:url");
            all.add("type:code");
            all.add("is:pinned");
            if (!positiveTags.isEmpty()) all.add(positiveTags.get(0));
            if (positiveTags.size() > 1) all.add(positiveTags.get(1));
            if (all.size() < MAX_SUGGESTIONS) all.add("-type:text");
            return List.copyOf(all);
        }

        all.addAll(positiveTypes);
        all.add("is:pinned");
        all.add("is:recent");
        all.addAll(positiveTags);
        all.addAll(negativeTypes);
        all.addAll(negativeTags);
        return List.copyOf(all);
    }

    private static Set<String> activeCanonicalOperators(SearchQuery parsed) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (SearchQuery.TypeTerm term : parsed.typeTerms()) {
            values.add(term.canonicalText().toLowerCase(Locale.ROOT));
        }
        for (SearchQuery.ScopeTerm term : parsed.scopeTerms()) {
            values.add(term.canonicalText().toLowerCase(Locale.ROOT));
        }
        for (SearchQuery.TagTerm term : parsed.tagTerms()) {
            values.add(term.canonicalText().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(values);
    }

    private static TokenRange currentTokenRange(String source, int caret) {
        List<TokenRange> ranges = tokenRanges(source);
        for (TokenRange range : ranges) {
            boolean inside = caret >= range.start() && caret < range.end();
            boolean atTokenEnd = caret == range.end()
                    && caret > range.start()
                    && !Character.isWhitespace(source.charAt(caret - 1));
            if (inside || atTokenEnd) return range;
        }
        return new TokenRange(caret, caret);
    }

    private static List<TokenRange> tokenRanges(String source) {
        if (source == null || source.isEmpty()) return List.of();

        List<TokenRange> ranges = new ArrayList<>();
        int index = 0;

        while (index < source.length()) {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
            if (index >= source.length()) break;

            int start = index;
            boolean inQuote = false;
            boolean escaping = false;

            while (index < source.length()) {
                char ch = source.charAt(index);

                if (inQuote) {
                    if (escaping) {
                        escaping = false;
                        index++;
                        continue;
                    }
                    if (ch == '\\') {
                        escaping = true;
                        index++;
                        continue;
                    }
                    if (ch == '"') {
                        inQuote = false;
                        index++;
                        continue;
                    }
                    index++;
                    continue;
                }

                if (ch == '"') {
                    inQuote = true;
                    index++;
                    continue;
                }
                if (Character.isWhitespace(ch)) break;
                index++;
            }

            ranges.add(new TokenRange(start, index));
        }

        return List.copyOf(ranges);
    }

    private static String canonicalTag(String name) {
        String escaped = name
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        boolean quoted = name.chars().anyMatch(Character::isWhitespace)
                || name.indexOf('"') >= 0
                || name.indexOf('\\') >= 0;
        return quoted ? "\"" + escaped + "\"" : escaped;
    }


    private record TokenRange(int start, int end) {}
}
