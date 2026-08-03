/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;
import io.xseries.xclip.ui.settings.DuplicateSettingsModel.WindowPreset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateSettingsModelTest {

    @Test
    void presetCatalogIsStableAndStartsWithSafeUnlimitedDefault() {
        assertEquals(
                List.of(WindowPreset.values()),
                DuplicateSettingsModel.windowPresets()
        );
        assertEquals(WindowPreset.UNLIMITED, DuplicateSettingsModel.windowPresets().get(0));
        assertEquals(0L, WindowPreset.UNLIMITED.millis());
    }

    @Test
    void knownWindowUsesPresetWithoutCustomText() {
        assertEquals(
                WindowPreset.FIVE_MINUTES,
                DuplicateSettingsModel.presetFor(300_000L)
        );
        assertEquals("", DuplicateSettingsModel.customWindowText(300_000L));
        assertEquals(
                300_000L,
                DuplicateSettingsModel.resolveWindowMillis(
                        WindowPreset.FIVE_MINUTES,
                        "ignored"
                )
        );
    }

    @Test
    void unknownWindowRoundTripsThroughCustomMilliseconds() {
        long custom = 12_345L;

        assertEquals(WindowPreset.CUSTOM, DuplicateSettingsModel.presetFor(custom));
        assertEquals("12345", DuplicateSettingsModel.customWindowText(custom));
        assertEquals(
                custom,
                DuplicateSettingsModel.resolveWindowMillis(
                        WindowPreset.CUSTOM,
                        " 12345 "
                )
        );
    }

    @Test
    void customWindowRejectsBlankNonDigitsAndOverflow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DuplicateSettingsModel.resolveWindowMillis(
                        WindowPreset.CUSTOM,
                        ""
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DuplicateSettingsModel.resolveWindowMillis(
                        WindowPreset.CUSTOM,
                        "-1"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DuplicateSettingsModel.resolveWindowMillis(
                        WindowPreset.CUSTOM,
                        "999999999999999999999999"
                )
        );
    }

    @Test
    void controlsProduceCompleteDuplicatePolicy() {
        DuplicateBehaviorPolicy policy = DuplicateSettingsModel.toPolicy(
                DuplicateBehaviorPolicy.RecentDuplicatePosition.PRESERVE_EXISTING_POSITION,
                DuplicateBehaviorPolicy.PinnedDuplicatePosition.MOVE_PIN_TO_TOP,
                DuplicateBehaviorPolicy.WhitespaceMode.PRESERVE,
                DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE,
                WindowPreset.TEN_SECONDS,
                "",
                true
        );

        assertEquals(
                DuplicateBehaviorPolicy.RecentDuplicatePosition.PRESERVE_EXISTING_POSITION,
                policy.recentDuplicatePosition()
        );
        assertEquals(
                DuplicateBehaviorPolicy.PinnedDuplicatePosition.MOVE_PIN_TO_TOP,
                policy.pinnedDuplicatePosition()
        );
        assertEquals(DuplicateBehaviorPolicy.WhitespaceMode.PRESERVE, policy.whitespaceMode());
        assertEquals(
                DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE,
                policy.caseSensitivity()
        );
        assertEquals(10_000L, policy.duplicateWindowMillis());
        assertTrue(policy.exactContentMode());
    }

    @Test
    void labelsAreProductFacingRatherThanEnumNames() {
        assertEquals(
                "Move duplicate to top",
                DuplicateSettingsModel.recentPositionLabel(
                        DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP
                )
        );
        assertEquals(
                "Keep pinned position",
                DuplicateSettingsModel.pinnedPositionLabel(
                        DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION
                )
        );
        assertEquals(
                "Normalize whitespace",
                DuplicateSettingsModel.whitespaceLabel(
                        DuplicateBehaviorPolicy.WhitespaceMode.NORMALIZE
                )
        );
        assertEquals(
                "Ignore case",
                DuplicateSettingsModel.caseSensitivityLabel(
                        DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE
                )
        );
    }
}
