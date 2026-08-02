/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import io.xseries.xclip.config.Config;

import java.util.Objects;

/**
 * Pure lifecycle model for one Settings editing session.
 *
 * The session owns exactly one saved baseline and one current draft. Dirty and
 * Apply state are derived, never toggled manually, so reverting all controls to
 * their baseline values immediately returns the window to a clean state.
 */
public final class SettingsDraftSession {

    private SettingsDraft baseline;
    private SettingsDraft current;
    private SettingsDraftValidation validation;

    public SettingsDraftSession(Config config) {
        commit(config);
    }

    public SettingsDraft baseline() {
        return baseline;
    }

    public SettingsDraft current() {
        return current;
    }

    public SettingsDraftValidation validation() {
        return validation;
    }

    public boolean dirty() {
        return !current.equals(baseline);
    }

    public boolean canApply() {
        return dirty() && validation.valid();
    }

    public void replaceCurrent(SettingsDraft draft) {
        current = Objects.requireNonNull(draft, "draft");
        validation = current.validate();
    }

    public void discard() {
        current = baseline;
        validation = current.validate();
    }

    public void commit(Config config) {
        baseline = SettingsDraft.fromConfig(
                Objects.requireNonNull(config, "config")
        );
        current = baseline;
        validation = current.validate();
    }
}
