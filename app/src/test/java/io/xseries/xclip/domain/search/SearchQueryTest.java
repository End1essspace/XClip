/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.search;

import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchQueryTest {

    @Test
    void defensivelyCopiesAllCollections() {
        List<SearchQuery.TypeTerm> types = new ArrayList<>();
        types.add(new SearchQuery.TypeTerm(ClipContentType.CODE, false));

        SearchQuery query = new SearchQuery(
                "type:code",
                "",
                types,
                List.of(),
                List.of(),
                List.of(),
                false
        );

        types.clear();

        assertEquals(1, query.typeTerms().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> query.typeTerms().add(
                        new SearchQuery.TypeTerm(ClipContentType.URL, false)
                )
        );
    }

    @Test
    void canonicalTextsAreStable() {
        assertEquals(
                "-type:command",
                new SearchQuery.TypeTerm(ClipContentType.COMMAND, true).canonicalText()
        );
        assertEquals(
                "is:pinned",
                new SearchQuery.ScopeTerm(ClipViewScope.PINNED).canonicalText()
        );
        assertEquals(
                "tag:\"Project Work\"",
                new SearchQuery.TagTerm("Project Work", "project work", false)
                        .canonicalText()
        );
        assertEquals(
                "tag:\"Client\\\"A\\\"\"",
                new SearchQuery.TagTerm("Client\"A\"", "client\"a\"", false)
                        .canonicalText()
        );
    }

    @Test
    void rejectsAllScopeAndInvalidIssueSpans() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SearchQuery.ScopeTerm(ClipViewScope.ALL)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SearchQueryIssue(
                        SearchQueryIssue.Code.INVALID_VALUE,
                        "bad",
                        4,
                        3
                )
        );
    }

    @Test
    void reportsPresenceOfOperatorsAndIssues() {
        SearchQuery query = new SearchQuery(
                "type:url",
                "",
                List.of(new SearchQuery.TypeTerm(ClipContentType.URL, false)),
                List.of(),
                List.of(),
                List.of(new SearchQueryIssue(
                        SearchQueryIssue.Code.INVALID_VALUE,
                        "example",
                        0,
                        1
                )),
                true
        );

        assertTrue(query.hasOperators());
        assertTrue(query.hasIssues());
        assertTrue(query.fallbackApplied());
    }
}
