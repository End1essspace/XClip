/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagFilterModelTest {

    @Test
    void emptyLibraryProducesOneDisabledNoTagsValue() {
        TagFilterModel.Snapshot snapshot = TagFilterModel.build(List.of(), 42L);

        assertFalse(snapshot.available());
        assertNull(snapshot.selectedTagId());
        assertEquals(1, snapshot.options().size());
        assertEquals("No tags", snapshot.options().get(0).label());
        assertNull(snapshot.options().get(0).tagId());
    }

    @Test
    void availableLibraryUsesCleanLabelsWithoutTagPrefix() {
        TagFilterModel.Snapshot snapshot = TagFilterModel.build(
                List.of(
                        new ClipTag(7L, "Project", 1L),
                        new ClipTag(9L, "Work", 2L)
                ),
                null
        );

        assertTrue(snapshot.available());
        assertEquals(List.of("All tags", "Project", "Work"),
                snapshot.options().stream().map(TagFilterModel.Option::label).toList());
    }

    @Test
    void existingSelectionIsPreserved() {
        TagFilterModel.Snapshot snapshot = TagFilterModel.build(
                List.of(
                        new ClipTag(7L, "Project", 1L),
                        new ClipTag(9L, "Work", 2L)
                ),
                9L
        );

        assertEquals(9L, snapshot.selectedTagId());
        assertEquals("Work", snapshot.selectedOption().label());
    }

    @Test
    void deletedSelectionFallsBackToAllTags() {
        TagFilterModel.Snapshot snapshot = TagFilterModel.build(
                List.of(new ClipTag(7L, "Project", 1L)),
                9L
        );

        assertNull(snapshot.selectedTagId());
        assertEquals("All tags", snapshot.selectedOption().label());
    }
}
