/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.search;

import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchQueryParserTest {

    @Test
    void parsesSupportedOperatorsAndPureTextRemainder() {
        SearchQuery query = SearchQueryParser.parse(
                "deploy failure type:code is:pinned tag:Work -tag:private -type:text"
        );

        assertEquals("deploy failure", query.text());
        assertEquals(
                List.of(
                        new SearchQuery.TypeTerm(ClipContentType.CODE, false),
                        new SearchQuery.TypeTerm(ClipContentType.TEXT, true)
                ),
                query.typeTerms()
        );
        assertEquals(
                List.of(new SearchQuery.ScopeTerm(ClipViewScope.PINNED)),
                query.scopeTerms()
        );
        assertEquals(
                List.of(
                        new SearchQuery.TagTerm("Work", "work", false),
                        new SearchQuery.TagTerm("private", "private", true)
                ),
                query.tagTerms()
        );
        assertFalse(query.hasIssues());
        assertFalse(query.fallbackApplied());
    }


    @Test
    void parsesEveryDeclaredContentType() {
        SearchQuery query = SearchQueryParser.parse(
                "type:text type:code type:url type:path type:json type:command"
        );

        assertEquals(
                List.of(
                        new SearchQuery.TypeTerm(ClipContentType.TEXT, false),
                        new SearchQuery.TypeTerm(ClipContentType.CODE, false),
                        new SearchQuery.TypeTerm(ClipContentType.URL, false),
                        new SearchQuery.TypeTerm(ClipContentType.PATH, false),
                        new SearchQuery.TypeTerm(ClipContentType.JSON, false),
                        new SearchQuery.TypeTerm(ClipContentType.COMMAND, false)
                ),
                query.typeTerms()
        );
        assertEquals("", query.text());
        assertFalse(query.hasIssues());
    }

    @Test
    void parsesQuotedTagValuesAndEscapesDeterministically() {
        SearchQuery query = SearchQueryParser.parse(
                "tag:\"Project Work\" -tag:\"Client \\\"A\\\"\""
        );

        assertEquals("", query.text());
        assertEquals(
                List.of(
                        new SearchQuery.TagTerm("Project Work", "project work", false),
                        new SearchQuery.TagTerm("Client \"A\"", "client \"a\"", true)
                ),
                query.tagTerms()
        );
        assertEquals("tag:\"Project Work\"", query.tagTerms().get(0).canonicalText());
        assertEquals("-tag:\"Client \\\"A\\\"\"", query.tagTerms().get(1).canonicalText());
    }

    @Test
    void canonicalTagTextRoundTripsQuotesAndBackslashes() {
        SearchQuery.TagTerm original =
                new SearchQuery.TagTerm("Client\"A\\B", "client\"a\\b", false);

        SearchQuery parsed = SearchQueryParser.parse(original.canonicalText());

        assertEquals(List.of(original), parsed.tagTerms());
        assertEquals("", parsed.text());
        assertFalse(parsed.hasIssues());
    }

    @Test
    void treatsQuotedPlainTextAsOneNormalizedTextRemainder() {
        SearchQuery query = SearchQueryParser.parse(
                "\"exact phrase\"   extra type:url"
        );

        assertEquals("exact phrase extra", query.text());
        assertEquals(
                List.of(new SearchQuery.TypeTerm(ClipContentType.URL, false)),
                query.typeTerms()
        );
    }

    @Test
    void preservesDuplicateOperatorsInSourceOrder() {
        SearchQuery query = SearchQueryParser.parse(
                "type:url type:json tag:Z tag:a"
        );

        assertEquals(
                List.of(
                        new SearchQuery.TypeTerm(ClipContentType.URL, false),
                        new SearchQuery.TypeTerm(ClipContentType.JSON, false)
                ),
                query.typeTerms()
        );
        assertEquals(
                List.of(
                        new SearchQuery.TagTerm("Z", "z", false),
                        new SearchQuery.TagTerm("a", "a", false)
                ),
                query.tagTerms()
        );
    }

    @Test
    void recognizedInvalidOperatorFallsBackToTextWithDiagnostic() {
        SearchQuery query = SearchQueryParser.parse(
                "type:video is:all tag:valid"
        );

        assertEquals("type:video is:all", query.text());
        assertEquals(List.of(), query.typeTerms());
        assertEquals(List.of(), query.scopeTerms());
        assertEquals(
                List.of(new SearchQuery.TagTerm("valid", "valid", false)),
                query.tagTerms()
        );
        assertEquals(2, query.issues().size());
        assertEquals(SearchQueryIssue.Code.INVALID_VALUE, query.issues().get(0).code());
        assertEquals(SearchQueryIssue.Code.INVALID_VALUE, query.issues().get(1).code());
        assertTrue(query.fallbackApplied());
    }

    @Test
    void missingValueAndUnsupportedScopeNegationFallBackToText() {
        SearchQuery query = SearchQueryParser.parse(
                "type: -is:pinned"
        );

        assertEquals("type: -is:pinned", query.text());
        assertEquals(
                List.of(
                        SearchQueryIssue.Code.MISSING_VALUE,
                        SearchQueryIssue.Code.UNSUPPORTED_NEGATION
                ),
                query.issues().stream().map(SearchQueryIssue::code).toList()
        );
        assertTrue(query.fallbackApplied());
    }

    @Test
    void unknownOperatorSyntaxRemainsOrdinaryTextWithoutError() {
        SearchQuery query = SearchQueryParser.parse(
                "site:example.com owner:\"Jane Doe\""
        );

        assertEquals("site:example.com owner:Jane Doe", query.text());
        assertFalse(query.hasOperators());
        assertFalse(query.hasIssues());
        assertFalse(query.fallbackApplied());
    }

    @Test
    void unterminatedQuoteFallsBackToCompleteRawQuery() {
        String raw = "before type:url tag:\"Project Work";
        SearchQuery query = SearchQueryParser.parse(raw);

        assertEquals(raw, query.text());
        assertFalse(query.hasOperators());
        assertEquals(1, query.issues().size());
        assertEquals(
                SearchQueryIssue.Code.UNTERMINATED_QUOTE,
                query.issues().get(0).code()
        );
        assertTrue(query.fallbackApplied());
    }

    @Test
    void emptyAndNullQueriesProduceStableEmptyResults() {
        SearchQuery empty = SearchQueryParser.parse("   ");
        SearchQuery nil = SearchQueryParser.parse(null);

        assertEquals("", empty.text());
        assertFalse(empty.hasOperators());
        assertFalse(empty.hasIssues());

        assertEquals("", nil.rawQuery());
        assertEquals("", nil.text());
        assertFalse(nil.hasOperators());
        assertFalse(nil.hasIssues());
    }

    @Test
    void operatorNamesAndEnumValuesAreCaseInsensitive() {
        SearchQuery query = SearchQueryParser.parse(
                "TYPE:URL IS:RECENT TAG:Project"
        );

        assertEquals(
                List.of(new SearchQuery.TypeTerm(ClipContentType.URL, false)),
                query.typeTerms()
        );
        assertEquals(
                List.of(new SearchQuery.ScopeTerm(ClipViewScope.RECENT)),
                query.scopeTerms()
        );
        assertEquals(
                List.of(new SearchQuery.TagTerm("Project", "project", false)),
                query.tagTerms()
        );
    }
}
