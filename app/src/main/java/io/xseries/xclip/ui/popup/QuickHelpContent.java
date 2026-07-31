/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import java.util.List;
import java.util.Objects;

/** Immutable content contract used by the Quick Help popover and its tests. */
public final class QuickHelpContent {

    public record Shortcut(String keys, String description) {
        public Shortcut {
            keys = Objects.requireNonNull(keys, "keys");
            description = Objects.requireNonNull(description, "description");
        }
    }

    public record Section(String title, List<Shortcut> shortcuts) {
        public Section {
            title = Objects.requireNonNull(title, "title");
            shortcuts = List.copyOf(shortcuts);
        }
    }

    private static final List<Section> SECTIONS = List.of(
            new Section("Search", List.of(
                    new Shortcut("Ctrl+K / Ctrl+F", "Focus and select search"),
                    new Shortcut("Ctrl+L", "Clear search"),
                    new Shortcut("Enter", "Jump to first search result"),
                    new Shortcut("Tab / Shift+Tab", "Move through every interactive control"),
                    new Shortcut("F6 / Shift+F6", "Move between Search, Filters, List, Actions, and Header")
            )),
            new Section("Selection", List.of(
                    new Shortcut("Click", "Select one clip"),
                    new Shortcut("Ctrl+Click", "Toggle one clip"),
                    new Shortcut("Shift+Click", "Select a range"),
                    new Shortcut("Ctrl+A", "Select all visible clips"),
                    new Shortcut("Ctrl+Shift+A", "Select visible clips in the Recent section"),
                    new Shortcut("Ctrl+I", "Invert the visible clip selection"),
                    new Shortcut("Ctrl+D", "Clear selection"),
                    new Shortcut("↑ / ↓", "Move between clips without selecting section headings"),
                    new Shortcut("Home / End", "Jump to the first or last visible clip"),
                    new Shortcut("Shift+↑ / Shift+↓", "Extend the keyboard selection range")
            )),
            new Section("Actions", List.of(
                    new Shortcut("Enter", "Paste selection"),
                    new Shortcut("Ctrl+C", "Copy only"),
                    new Shortcut("Ctrl+P", "Pin or unpin"),
                    new Shortcut("E", "Expand or collapse preview"),
                    new Shortcut("Esc", "Close menu, collapse preview, then clear or hide"),
                    new Shortcut("F2", "Rename pinned clip"),
                    new Shortcut("Alt+↑ / Alt+↓", "Move pinned clip"),
                    new Shortcut("Delete", "Delete selection; multiple clips require confirmation"),
                    new Shortcut("Shift+F10 / Menu", "Open keyboard-navigable clip actions"),
                    new Shortcut("Ctrl+,", "Open Settings"),
                    new Shortcut("Double-click", "Paste one clip"),
                    new Shortcut("Click type badge", "Run safe type action"),
                    new Shortcut("Right-click", "Open context actions")
            ))
    );

    private QuickHelpContent() {}

    public static List<Section> sections() {
        return SECTIONS;
    }
}

