/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.search;

import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchExecutionPlanTest {

    @Test
    void combinesToolbarAndOperatorsWithoutMutatingToolbarState() {
        SearchExecutionPlan plan = SearchExecutionPlan.combine(
                SearchQueryParser.parse("deploy type:code -type:text tag:Work -tag:Private is:pinned"),
                ClipViewScope.ALL,
                null,
                9L
        );

        assertEquals("deploy", plan.text());
        assertEquals(ClipViewScope.PINNED, plan.scope());
        assertEquals(9L, plan.toolbarTagId().longValue());
        assertEquals(List.of(ClipContentType.CODE), plan.includedTypes());
        assertEquals(List.of(ClipContentType.TEXT), plan.excludedTypes());
        assertEquals(List.of("work"), plan.requiredTagIdentities());
        assertEquals(List.of("private"), plan.excludedTagIdentities());
        assertFalse(plan.unsatisfiable());
        assertTrue(plan.derivedTypeFilteringActive());
        assertTrue(plan.matchesType(ClipContentType.CODE));
        assertFalse(plan.matchesType(ClipContentType.TEXT));
    }

    @Test
    void positiveTypesUseOrWhilePositiveTagsUseAndAtTheDaoBoundary() {
        SearchExecutionPlan plan = SearchExecutionPlan.combine(
                SearchQueryParser.parse("type:url type:json tag:Work tag:Urgent"),
                ClipViewScope.ALL,
                null,
                null
        );

        assertTrue(plan.matchesType(ClipContentType.URL));
        assertTrue(plan.matchesType(ClipContentType.JSON));
        assertFalse(plan.matchesType(ClipContentType.CODE));
        assertEquals(List.of("work", "urgent"), plan.requiredTagIdentities());
    }

    @Test
    void deduplicatesTermsWhilePreservingFirstSeenOrder() {
        SearchExecutionPlan plan = SearchExecutionPlan.combine(
                SearchQueryParser.parse(
                        "type:json type:url type:json tag:Z tag:a tag:Z -tag:x -tag:x"
                ),
                ClipViewScope.ALL,
                null,
                null
        );

        assertEquals(
                List.of(ClipContentType.JSON, ClipContentType.URL),
                plan.includedTypes()
        );
        assertEquals(List.of("z", "a"), plan.requiredTagIdentities());
        assertEquals(List.of("x"), plan.excludedTagIdentities());
    }

    @Test
    void conflictingScopeTypeAndTagConstraintsAreUnsatisfiable() {
        SearchExecutionPlan scopeConflict = SearchExecutionPlan.combine(
                SearchQueryParser.parse("is:recent"),
                ClipViewScope.PINNED,
                null,
                null
        );
        SearchExecutionPlan typeConflict = SearchExecutionPlan.combine(
                SearchQueryParser.parse("type:url -type:url"),
                ClipViewScope.ALL,
                null,
                null
        );
        SearchExecutionPlan toolbarTypeConflict = SearchExecutionPlan.combine(
                SearchQueryParser.parse("type:url"),
                ClipViewScope.ALL,
                ClipContentType.JSON,
                null
        );
        SearchExecutionPlan tagConflict = SearchExecutionPlan.combine(
                SearchQueryParser.parse("tag:Work -tag:work"),
                ClipViewScope.ALL,
                null,
                null
        );

        assertTrue(scopeConflict.unsatisfiable());
        assertTrue(typeConflict.unsatisfiable());
        assertTrue(toolbarTypeConflict.unsatisfiable());
        assertTrue(tagConflict.unsatisfiable());
    }

    @Test
    void toolbarTypeCanNarrowAQueryTypeOrSet() {
        SearchExecutionPlan plan = SearchExecutionPlan.combine(
                SearchQueryParser.parse("type:url type:json -type:text"),
                ClipViewScope.ALL,
                ClipContentType.JSON,
                null
        );

        assertFalse(plan.unsatisfiable());
        assertTrue(plan.matchesType(ClipContentType.JSON));
        assertFalse(plan.matchesType(ClipContentType.URL));
    }
}
