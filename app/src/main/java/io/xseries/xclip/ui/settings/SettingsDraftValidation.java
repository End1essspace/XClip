/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import io.xseries.xclip.config.Config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable validation result for one SettingsDraft.
 *
 * A materialized value exists only when every field is valid, preventing any
 * runtime or persistence callback from observing a partially parsed draft.
 */
public record SettingsDraftValidation(
        List<SettingsValidationIssue> issues,
        SettingsDraft.ValidatedValues values
) {
    public SettingsDraftValidation {
        issues = List.copyOf(Objects.requireNonNullElse(issues, List.of()));
        if (issues.isEmpty() != (values != null)) {
            throw new IllegalArgumentException(
                    "Validated values must exist exactly when there are no issues"
            );
        }
    }

    public boolean valid() {
        return issues.isEmpty();
    }

    public Optional<SettingsValidationIssue> firstIssue() {
        return issues.stream().findFirst();
    }

    public Set<SettingsPage> invalidPages() {
        LinkedHashSet<SettingsPage> pages = new LinkedHashSet<>();
        for (SettingsValidationIssue issue : issues) {
            pages.add(issue.page());
        }
        return Set.copyOf(pages);
    }

    public Config toConfig(Config base) {
        if (!valid()) {
            throw new IllegalStateException(
                    "Cannot materialize invalid Settings draft: "
                            + firstIssue().map(SettingsValidationIssue::displayMessage)
                            .orElse("unknown validation failure")
            );
        }
        return values.toConfig(base);
    }
}
