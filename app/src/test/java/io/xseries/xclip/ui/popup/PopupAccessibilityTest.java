package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.domain.model.ClipContentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PopupAccessibilityTest {

    @Test
    void sectionLabelsUseReadableCounts() {
        assertTrue(PopupAccessibility.sectionLabel("Pinned", 1)
                .contains("1 clip"));
        assertTrue(PopupAccessibility.sectionLabel("Recent", 4)
                .contains("4 clips"));
    }

    @Test
    void clipLabelIncludesStateTypeAndBoundedContent() {
        ClipEntry entry = new ClipEntry(
                7,
                "first\nsecond\t" + "x".repeat(500),
                "Build command",
                true,
                0,
                1_700_000_000_000L
        );

        String label = PopupAccessibility.clipLabel(
                entry,
                ClipContentType.COMMAND,
                "12:30",
                true,
                false
        );

        assertTrue(label.startsWith("Selected, pinned clip"));
        assertTrue(label.contains("title Build command"));
        assertTrue(label.contains("type COMMAND"));
        assertTrue(label.contains("copied 12:30"));
        assertTrue(label.contains("first second"));
        assertTrue(label.contains("…"));
        assertFalse(label.contains("\n"));
        assertFalse(label.contains("\t"));
        assertTrue(label.length() < 500);
    }

    @Test
    void recentClipAnnouncesPreviewState() {
        ClipEntry entry = new ClipEntry(
                9,
                "hello",
                null,
                false,
                null,
                0
        );

        assertTrue(PopupAccessibility.clipLabel(
                entry,
                ClipContentType.TEXT,
                "",
                false,
                true
        ).contains("preview expanded"));
    }
}
