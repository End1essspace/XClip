/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.privacy;

import io.xseries.xclip.config.Config;
import io.xseries.xclip.domain.privacy.ExcludedApplicationPolicy;
import io.xseries.xclip.system.privacy.ForegroundApplicationResolver.ForegroundApplication;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Runtime clipboard-capture gate for excluded foreground applications.
 *
 * The policy is fail-open: resolver failures and unidentified processes never
 * suppress clipboard capture. Only a positive executable-name match blocks it.
 */
public final class ClipboardPrivacyGate {

    private final Supplier<Optional<ForegroundApplication>> resolver;
    private final AtomicReference<ExcludedApplicationPolicy> policy =
            new AtomicReference<>(ExcludedApplicationPolicy.defaults());

    public ClipboardPrivacyGate(ForegroundApplicationResolver resolver) {
        this(Objects.requireNonNull(resolver, "resolver")::resolve);
    }

    ClipboardPrivacyGate(Supplier<Optional<ForegroundApplication>> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public void applyConfig(Config config) {
        policy.set(config == null
                ? ExcludedApplicationPolicy.defaults()
                : config.excludedApplicationPolicy());
    }

    public ExcludedApplicationPolicy policy() {
        return policy.get();
    }

    public boolean isCaptureAllowed() {
        ExcludedApplicationPolicy current = policy.get();
        if (current.empty()) return true;

        try {
            Optional<ForegroundApplication> application = resolver.get();
            if (application == null || application.isEmpty()) return true;

            ForegroundApplication foreground = application.get();
            if (!foreground.hasExecutableName()) return true;

            return !current.excludes(foreground.executableName());
        } catch (Throwable ignored) {
            return true;
        }
    }
}
