/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipTag;
import io.xseries.xclip.ui.popup.TagEditorModel.AddResult;
import io.xseries.xclip.ui.popup.TagEditorModel.EditPlan;
import io.xseries.xclip.ui.popup.TagEditorModel.SelectionState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagEditorModelTest {

    private static final ClipTag LATER = new ClipTag(1, "Later", 1);
    private static final ClipTag PRIVATE = new ClipTag(2, "Private", 2);
    private static final ClipTag WORK = new ClipTag(3, "Work", 3);

    @Test
    void derivesAssignedMixedAndUnassignedStatesForMultiSelection() {
        TagEditorModel model = model();

        assertEquals(SelectionState.UNASSIGNED, model.state(LATER.id()));
        assertEquals(SelectionState.MIXED, model.state(PRIVATE.id()));
        assertEquals(SelectionState.ASSIGNED, model.state(WORK.id()));
        assertFalse(model.hasChanges());
        assertTrue(model.plan().isEmpty());
    }

    @Test
    void producesNonDestructiveTriStateEditPlan() {
        TagEditorModel model = model();

        model.setState(PRIVATE.id(), SelectionState.ASSIGNED);
        model.setState(WORK.id(), SelectionState.UNASSIGNED);

        EditPlan plan = model.plan();
        assertEquals(List.of(PRIVATE.id()), plan.assignTagIds());
        assertEquals(List.of(WORK.id()), plan.removeTagIds());
        assertEquals(List.of(), plan.createAndAssignNames());
        assertTrue(model.hasChanges());
    }

    @Test
    void newNamesAreNormalizedAndDuplicatesAreHandledInline() {
        TagEditorModel model = model();

        assertEquals(AddResult.ADDED, model.addPendingTag("  Project   Work "));
        assertEquals(AddResult.ALREADY_PENDING, model.addPendingTag("project work"));
        assertEquals(List.of("Project Work"), model.pendingTagNames());

        assertEquals(AddResult.SELECTED_EXISTING, model.addPendingTag(" later "));
        assertEquals(SelectionState.ASSIGNED, model.state(LATER.id()));
        assertEquals(AddResult.ALREADY_ASSIGNED, model.addPendingTag("WORK"));

        EditPlan plan = model.plan();
        assertEquals(List.of(LATER.id()), plan.assignTagIds());
        assertEquals(List.of("Project Work"), plan.createAndAssignNames());

        assertTrue(model.removePendingTag("PROJECT WORK"));
        assertFalse(model.removePendingTag("Project Work"));
    }

    @Test
    void validatesSelectionAndUnknownTags() {
        assertThrows(IllegalArgumentException.class,
                () -> TagEditorModel.create(List.of(), List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> TagEditorModel.create(List.of(0L), List.of(), Map.of()));

        TagEditorModel model = model();
        assertThrows(IllegalArgumentException.class,
                () -> model.setState(999, SelectionState.ASSIGNED));
        assertThrows(IllegalArgumentException.class,
                () -> model.addPendingTag(" "));
    }

    private TagEditorModel model() {
        return TagEditorModel.create(
                List.of(10L, 20L),
                List.of(WORK, PRIVATE, LATER),
                Map.of(
                        10L, List.of(WORK, PRIVATE),
                        20L, List.of(WORK)
                )
        );
    }
}
