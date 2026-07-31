/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Single audited keyboard contract for the popup.
 *
 * Bindings explicitly declare whether they may run while a text input owns
 * focus. This prevents popup actions from stealing native editing shortcuts
 * such as Ctrl+A, Ctrl+C, Delete, and cursor movement from the search field.
 */
public final class PopupKeyBindings {

    public enum Action {
        NONE,
        SELECT_RECENT,
        SELECT_ALL,
        CLEAR_SELECTION,
        INVERT_SELECTION,
        FOCUS_SEARCH,
        CLEAR_SEARCH,
        COPY,
        TOGGLE_PIN,
        OPEN_SETTINGS,
        MOVE_PINNED_UP,
        MOVE_PINNED_DOWN,
        RENAME_PINNED,
        TOGGLE_PREVIEW,
        ESCAPE,
        PASTE,
        DELETE,
        OPEN_ACTIONS,
        FOCUS_NEXT_ZONE,
        FOCUS_PREVIOUS_ZONE
    }

    public record Stroke(
            String code,
            boolean control,
            boolean shift,
            boolean alt,
            boolean meta
    ) {
        public Stroke {
            code = normalizeCode(code);
        }
    }

    public record Binding(Stroke stroke, Action action, boolean allowedInTextInput) {
        public Binding {
            Objects.requireNonNull(stroke, "stroke");
            Objects.requireNonNull(action, "action");
        }
    }

    private static final List<Binding> BINDINGS = List.of(
            binding("A", true, true, false, false, Action.SELECT_RECENT, false),
            binding("A", true, false, false, false, Action.SELECT_ALL, false),
            binding("D", true, false, false, false, Action.CLEAR_SELECTION, false),
            binding("I", true, false, false, false, Action.INVERT_SELECTION, false),
            binding("F", true, false, false, false, Action.FOCUS_SEARCH, true),
            binding("K", true, false, false, false, Action.FOCUS_SEARCH, true),
            binding("L", true, false, false, false, Action.CLEAR_SEARCH, true),
            binding("C", true, false, false, false, Action.COPY, false),
            binding("P", true, false, false, false, Action.TOGGLE_PIN, false),
            binding("COMMA", true, false, false, false, Action.OPEN_SETTINGS, true),
            binding("UP", false, false, true, false, Action.MOVE_PINNED_UP, false),
            binding("DOWN", false, false, true, false, Action.MOVE_PINNED_DOWN, false),
            binding("F2", false, false, false, false, Action.RENAME_PINNED, false),
            binding("E", false, false, false, false, Action.TOGGLE_PREVIEW, false),
            binding("ESCAPE", false, false, false, false, Action.ESCAPE, true),
            binding("ENTER", false, false, false, false, Action.PASTE, false),
            binding("DELETE", false, false, false, false, Action.DELETE, false),
            binding("F10", false, true, false, false, Action.OPEN_ACTIONS, false),
            binding("CONTEXT_MENU", false, false, false, false, Action.OPEN_ACTIONS, false),
            binding("F6", false, false, false, false, Action.FOCUS_NEXT_ZONE, true),
            binding("F6", false, true, false, false, Action.FOCUS_PREVIOUS_ZONE, true)
    );

    private PopupKeyBindings() {}

    public static Action resolve(
            String code,
            boolean control,
            boolean shift,
            boolean alt,
            boolean meta,
            boolean textInputFocused
    ) {
        Stroke stroke = new Stroke(code, control, shift, alt, meta);

        for (Binding binding : BINDINGS) {
            if (!binding.stroke().equals(stroke)) continue;
            if (textInputFocused && !binding.allowedInTextInput()) {
                return Action.NONE;
            }
            return binding.action();
        }
        return Action.NONE;
    }

    public static List<Binding> bindings() {
        return BINDINGS;
    }

    private static Binding binding(
            String code,
            boolean control,
            boolean shift,
            boolean alt,
            boolean meta,
            Action action,
            boolean allowedInTextInput
    ) {
        return new Binding(
                new Stroke(code, control, shift, alt, meta),
                action,
                allowedInTextInput
        );
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) return "";
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
