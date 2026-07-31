package io.xseries.xclip.ui.popup;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PopupKeyBindingsTest {

    @Test
    void nativeTextEditingShortcutsAreNotHijacked() {
        assertEquals(
                PopupKeyBindings.Action.NONE,
                resolve("A", true, false, false, false, true)
        );
        assertEquals(
                PopupKeyBindings.Action.NONE,
                resolve("C", true, false, false, false, true)
        );
        assertEquals(
                PopupKeyBindings.Action.NONE,
                resolve("DELETE", false, false, false, false, true)
        );
        assertEquals(
                PopupKeyBindings.Action.NONE,
                resolve("ENTER", false, false, false, false, true)
        );
    }

    @Test
    void popupShortcutsRemainAvailableOutsideTextInputs() {
        assertEquals(
                PopupKeyBindings.Action.SELECT_ALL,
                resolve("A", true, false, false, false, false)
        );
        assertEquals(
                PopupKeyBindings.Action.COPY,
                resolve("C", true, false, false, false, false)
        );
        assertEquals(
                PopupKeyBindings.Action.DELETE,
                resolve("DELETE", false, false, false, false, false)
        );
        assertEquals(
                PopupKeyBindings.Action.OPEN_ACTIONS,
                resolve("F10", false, true, false, false, false)
        );
        assertEquals(
                PopupKeyBindings.Action.FOCUS_NEXT_ZONE,
                resolve("F6", false, false, false, false, true)
        );
        assertEquals(
                PopupKeyBindings.Action.FOCUS_PREVIOUS_ZONE,
                resolve("F6", false, true, false, false, true)
        );
    }

    @Test
    void bindingTableHasNoDuplicateStrokes() {
        Set<PopupKeyBindings.Stroke> unique = new HashSet<>();

        for (PopupKeyBindings.Binding binding : PopupKeyBindings.bindings()) {
            assertTrue(unique.add(binding.stroke()), () ->
                    "Duplicate shortcut: " + binding.stroke());
        }
    }

    private PopupKeyBindings.Action resolve(
            String code,
            boolean control,
            boolean shift,
            boolean alt,
            boolean meta,
            boolean textInput
    ) {
        return PopupKeyBindings.resolve(
                code,
                control,
                shift,
                alt,
                meta,
                textInput
        );
    }
}
