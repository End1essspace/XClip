
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
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

    @Test
    void preparedValueKeepsNormalizedTextLookupHashAndCanonicalKeyAligned() {
        DuplicateBehaviorPolicy policy = new DuplicateBehaviorPolicy(
                DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                DuplicateBehaviorPolicy.WhitespaceMode.NORMALIZE,
                DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE,
                0,
                false
        );

        DuplicateContentKeys.Prepared prepared =
                DuplicateContentKeys.prepare(" Alpha\tValue ", policy);

        assertEquals("Alpha Value", prepared.normalizedContent());
        assertEquals(DuplicateContentKeys.KeyKind.NORMALIZED_CASE_INSENSITIVE,
                prepared.selectedKind());
        assertEquals("alpha value", prepared.canonicalKey());
        assertEquals(prepared.keys().normalizedCaseInsensitiveHash(),
                prepared.selectedHash());
    }

    @Test
    void selectedOnlyHashMatchesPersistedHashForEveryEqualityMode() {
        String content = " Alpha\tValue ";

        for (DuplicateBehaviorPolicy policy : java.util.List.of(
                new DuplicateBehaviorPolicy(
                        DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                        DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                        DuplicateBehaviorPolicy.WhitespaceMode.PRESERVE,
                        DuplicateBehaviorPolicy.CaseSensitivity.SENSITIVE,
                        0,
                        false
                ),
                new DuplicateBehaviorPolicy(
                        DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                        DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                        DuplicateBehaviorPolicy.WhitespaceMode.PRESERVE,
                        DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE,
                        0,
                        false
                ),
                DuplicateBehaviorPolicy.defaults(),
                new DuplicateBehaviorPolicy(
                        DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                        DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                        DuplicateBehaviorPolicy.WhitespaceMode.NORMALIZE,
                        DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE,
                        0,
                        false
                ),
                new DuplicateBehaviorPolicy(
                        DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                        DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                        DuplicateBehaviorPolicy.WhitespaceMode.NORMALIZE,
                        DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE,
                        0,
                        true
                )
        )) {
            DuplicateContentKeys keys = DuplicateContentKeys.from(content);
            assertEquals(
                    keys.selectedHash(policy),
                    DuplicateContentKeys.selectedHashFor(content, policy)
            );
        }
    }

}
