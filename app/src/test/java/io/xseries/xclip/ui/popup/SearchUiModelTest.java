/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchUiModelTest {

    @Test
    void focusedEmptySearchShowsSyntaxHintAndStarterSuggestions() {
        SearchUiModel.State state = SearchUiModel.build("", 0, List.of(), true);

        assertTrue(state.visible());
        assertTrue(state.chips().isEmpty());
        assertEquals(SearchUiModel.MessageTone.HINT, state.messageTone());
        assertTrue(state.message().contains("type:"));
        assertEquals(
                List.of("type:url", "type:code", "is:pinned", "-type:text"),
                state.suggestions().stream().map(SearchUiModel.Suggestion::label).toList()
        );
    }

    @Test
    void incompleteRecognizedOperatorShowsCompletionsInsteadOfAnError() {
        SearchUiModel.State state = SearchUiModel.build(
                "type:",
                "type:".length(),
                List.of(),
                true
        );

        assertEquals(SearchUiModel.MessageTone.HINT, state.messageTone());
        assertTrue(state.message().contains("Choose a value"));
        assertEquals(
                List.of(
                        "type:text",
                        "type:code",
                        "type:url",
                        "type:path",
                        "type:json"
                ),
                state.suggestions().stream().map(SearchUiModel.Suggestion::label).toList()
        );
    }

    @Test
    void validOperatorsBecomeBoundedChipsAndTextRemainsSeparate() {
        SearchUiModel.State state = SearchUiModel.build(
                "release type:url -type:text is:pinned tag:Work -tag:Private",
                62,
                List.of(),
                false
        );

        assertTrue(state.visible());
        assertEquals("release", state.textRemainder());
        assertEquals(
                List.of(
                        "type:url",
                        "-type:text",
                        "is:pinned",
                        "tag:Work",
                        "-tag:Private"
                ),
                state.chips().stream().map(SearchUiModel.OperatorChip::text).toList()
        );
        assertEquals(0, state.hiddenChipCount());
        assertTrue(state.suggestions().isEmpty());
    }

    @Test
    void operatorChipOverflowIsDeterministic() {
        SearchUiModel.State state = SearchUiModel.build(
                "type:text type:code type:url type:path type:json type:command "
                        + "tag:one tag:two",
                82,
                List.of(),
                false
        );

        assertEquals(SearchUiModel.MAX_VISIBLE_CHIPS, state.chips().size());
        assertEquals(2, state.hiddenChipCount());
        assertEquals("type:text", state.chips().get(0).text());
        assertEquals("type:command", state.chips().get(5).text());
    }

    @Test
    void parserIssueUsesInlineErrorAndSuppressesSuggestions() {
        SearchUiModel.State state = SearchUiModel.build(
                "type:video",
                "type:video".length(),
                List.of(),
                true
        );

        assertTrue(state.visible());
        assertEquals(SearchUiModel.MessageTone.ERROR, state.messageTone());
        assertTrue(state.message().contains("Unknown content type"));
        assertTrue(state.message().contains("ordinary text"));
        assertTrue(state.suggestions().isEmpty());
        assertEquals("type:video", state.textRemainder());
    }

    @Test
    void tagSuggestionsQuoteSpacesAndReplaceOnlyCurrentToken() {
        List<ClipTag> tags = List.of(
                new ClipTag(1, "Project Work", 10),
                new ClipTag(2, "Private", 20)
        );
        String raw = "release tag:Pro -type:text";
        int caret = "release tag:Pro".length();

        SearchUiModel.State state = SearchUiModel.build(raw, caret, tags, true);

        SearchUiModel.Suggestion suggestion = state.suggestions().get(0);
        assertEquals("tag:\"Project Work\"", suggestion.label());

        SearchUiModel.AppliedQuery applied = suggestion.applyTo(raw);
        assertEquals("release tag:\"Project Work\" -type:text", applied.query());
        assertEquals("release tag:\"Project Work\"".length(), applied.caretPosition());
    }

    @Test
    void negativeTagSuggestionUsesNegativePrefix() {
        List<ClipTag> tags = List.of(new ClipTag(1, "Private Client", 10));
        SearchUiModel.State state = SearchUiModel.build(
                "-tag:Pri",
                "-tag:Pri".length(),
                tags,
                true
        );

        assertEquals(
                List.of("-tag:\"Private Client\""),
                state.suggestions().stream().map(SearchUiModel.Suggestion::label).toList()
        );
    }

    @Test
    void unfocusedPlainTextKeepsAssistHidden() {
        SearchUiModel.State state = SearchUiModel.build(
                "ordinary text",
                13,
                List.of(),
                false
        );

        assertFalse(state.visible());
        assertEquals("ordinary text", state.textRemainder());
    }
}
