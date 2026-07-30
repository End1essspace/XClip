package io.xseries.xclip.ui.popup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipPreviewPolicyTest {

    @Test
    void expandedPreviewIsBoundedByLines() {
        String input = String.join("\n", java.util.Collections.nCopies(40, "line"));

        String result = ClipPreviewPolicy.expandedPreview(input);

        assertTrue(result.endsWith("…"));
        assertTrue(result.lines().count() <= ClipPreviewPolicy.MAX_EXPANDED_LINES);
    }

    @Test
    void expandedPreviewIsBoundedByCharacters() {
        String input = "x".repeat(ClipPreviewPolicy.MAX_EXPANDED_CHARS + 200);

        String result = ClipPreviewPolicy.expandedPreview(input);

        assertTrue(result.endsWith("…"));
        assertEquals(ClipPreviewPolicy.MAX_EXPANDED_CHARS + 1, result.length());
    }

    @Test
    void shortPreviewIsPreserved() {
        assertEquals("alpha\nbeta", ClipPreviewPolicy.expandedPreview("alpha\nbeta"));
    }
}
