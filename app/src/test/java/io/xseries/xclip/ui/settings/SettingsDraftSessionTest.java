/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import io.xseries.xclip.config.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsDraftSessionTest {

    @Test
    void dirtyAndApplyStateAreDerivedFromBaselineCurrentAndValidation() {
        Config base = Config.defaults();
        SettingsDraftSession session = new SettingsDraftSession(base);

        assertFalse(session.dirty());
        assertFalse(session.canApply());
        assertTrue(session.validation().valid());

        SettingsDraft baseline = session.baseline();
        SettingsDraft changed = new SettingsDraft(
                new SettingsDraft.General(
                        !baseline.general().watcherEnabled(),
                        baseline.general().startMinimized(),
                        baseline.general().startOnBoot()
                ),
                baseline.capture(),
                baseline.history(),
                baseline.duplicate(),
                baseline.privacy(),
                baseline.retention()
        );
        session.replaceCurrent(changed);

        assertTrue(session.dirty());
        assertTrue(session.canApply());

        session.replaceCurrent(baseline);
        assertFalse(session.dirty());
        assertFalse(session.canApply());
    }

    @Test
    void invalidCurrentDraftIsDirtyButCannotApplyAndDiscardRestoresBaseline() {
        SettingsDraftSession session = new SettingsDraftSession(Config.defaults());
        SettingsDraft baseline = session.baseline();
        SettingsDraft invalid = new SettingsDraft(
                baseline.general(),
                new SettingsDraft.Capture(
                        "",
                        baseline.capture().maxClipChars(),
                        baseline.capture().uiClipLimit()
                ),
                baseline.history(),
                baseline.duplicate(),
                baseline.privacy(),
                baseline.retention()
        );

        session.replaceCurrent(invalid);
        assertTrue(session.dirty());
        assertFalse(session.canApply());
        assertEquals(SettingsField.MIN_CLIP_LENGTH,
                session.validation().firstIssue().orElseThrow().field());

        session.discard();
        assertEquals(session.baseline(), session.current());
        assertFalse(session.dirty());
        assertTrue(session.validation().valid());
    }

    @Test
    void commitCreatesANewCanonicalBaseline() {
        SettingsDraftSession session = new SettingsDraftSession(Config.defaults());
        Config saved = Config.defaults().withMaxHistory(1_500);

        session.commit(saved);

        assertEquals("1500", session.baseline().history().maxHistory());
        assertEquals(session.baseline(), session.current());
        assertFalse(session.dirty());
        assertFalse(session.canApply());
    }
}
