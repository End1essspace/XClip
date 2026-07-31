package io.xseries.xclip.ui.popup;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickHelpContentTest {

    @Test
    void helpIncludesPreviewAndEscapeRecovery() {
        Set<String> keys = QuickHelpContent.sections().stream()
                .flatMap(section -> section.shortcuts().stream())
                .map(QuickHelpContent.Shortcut::keys)
                .collect(Collectors.toSet());

        assertTrue(keys.contains("E"));
        assertTrue(keys.contains("Esc"));
        assertTrue(keys.contains("Ctrl+K / Ctrl+F"));

        assertTrue(QuickHelpContent.sections().stream()
                .flatMap(section -> section.shortcuts().stream())
                .filter(shortcut -> shortcut.keys().equals("Delete"))
                .anyMatch(shortcut -> shortcut.description().contains("require confirmation")));
    }
}
