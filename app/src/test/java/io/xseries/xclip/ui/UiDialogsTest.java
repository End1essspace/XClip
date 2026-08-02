
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDialogsTest {

    @Test
    void batchDeleteCopyCallsOutPinnedEntriesAndCount() {
        UiDialogs.DialogCopy copy = UiDialogs.batchDeleteCopy(4, 2);

        assertEquals("Delete 4 selected clips?", copy.heading());
        assertEquals("Delete 4 clips", copy.actionLabel());
        assertTrue(copy.body().contains("2 pinned clips"));
        assertTrue(copy.body().contains("cannot be undone"));
    }

    @Test
    void clearVisibleCopyExplainsSearchAndFilterBoundary() {
        UiDialogs.DialogCopy copy = UiDialogs.clearVisibleCopy(1);

        assertEquals("Clear 1 visible clip?", copy.heading());
        assertEquals("Clear 1 clip", copy.actionLabel());
        assertTrue(copy.body().contains("active search and filters"));
        assertTrue(copy.body().contains("hidden clips stay untouched"));
    }

    @Test
    void titleNormalizationTrimsAndBoundsInput() {
        assertEquals("Release checklist", UiDialogs.normalizeTitle("  Release checklist  ", 120));
        assertEquals("abcd", UiDialogs.normalizeTitle("abcdef", 4));
        assertEquals("", UiDialogs.normalizeTitle(null, 10));
        assertThrows(IllegalArgumentException.class, () -> UiDialogs.normalizeTitle("x", 0));
    }

    @Test
    void invalidConfirmationCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> UiDialogs.batchDeleteCopy(1, 0));
        assertThrows(IllegalArgumentException.class, () -> UiDialogs.batchDeleteCopy(3, 4));
        assertThrows(IllegalArgumentException.class, () -> UiDialogs.clearVisibleCopy(0));
    }
    @Test
    void clearRecentConfirmationPreservesPinnedAndSettingsInCopy() {
        UiDialogs.DialogCopy copy = UiDialogs.clearRecentCopy();

        assertEquals("Clear RECENT", copy.actionLabel());
        assertTrue(copy.body().contains("PINNED"));
        assertTrue(copy.body().contains("settings"));
    }

}
