/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.duplicate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DuplicateContentKeysTest {

    @Test
    void computesAllFourStableEqualityKeys() {
        DuplicateContentKeys first = DuplicateContentKeys.from(" Alpha\tValue ");
        DuplicateContentKeys second = DuplicateContentKeys.from("alpha value");

        assertNotEquals(first.exactHash(), second.exactHash());
        assertNotEquals(first.exactCaseInsensitiveHash(), second.exactCaseInsensitiveHash());
        assertNotEquals(first.normalizedHash(), second.normalizedHash());
        assertEquals(first.normalizedCaseInsensitiveHash(),
                second.normalizedCaseInsensitiveHash());
    }

    @Test
    void selectsHashWithoutRewritingRowsWhenPolicyChanges() {
        DuplicateContentKeys keys = DuplicateContentKeys.from("Alpha  Value");

        DuplicateBehaviorPolicy normalizedInsensitive = new DuplicateBehaviorPolicy(
                DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                DuplicateBehaviorPolicy.WhitespaceMode.NORMALIZE,
                DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE,
                0,
                false
        );
        DuplicateBehaviorPolicy exact = new DuplicateBehaviorPolicy(
                DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                DuplicateBehaviorPolicy.WhitespaceMode.NORMALIZE,
                DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE,
                0,
                true
        );

        assertEquals(DuplicateContentKeys.KeyKind.NORMALIZED_CASE_INSENSITIVE,
                keys.selectedKind(normalizedInsensitive));
        assertEquals(keys.normalizedCaseInsensitiveHash(),
                keys.selectedHash(normalizedInsensitive));
        assertEquals(DuplicateContentKeys.KeyKind.EXACT, keys.selectedKind(exact));
        assertEquals(keys.exactHash(), keys.selectedHash(exact));
    }
}
