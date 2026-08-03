/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.privacy;

import io.xseries.xclip.config.Config;
import io.xseries.xclip.domain.privacy.ExcludedApplicationPolicy;
import io.xseries.xclip.domain.privacy.SensitiveContentPolicy;
import io.xseries.xclip.system.privacy.ForegroundApplicationResolver.ForegroundApplication;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Runtime clipboard-capture gate for process exclusions and sensitive-content rules.
 *
 * Fail-open is applied independently to each best-effort inspection path. A
 * positive process exclusion or an explicitly enabled sensitive-content rule
 * blocks capture; resolver or detector failures never silently discard data.
 */
public final class ClipboardPrivacyGate {

    private final Supplier<Optional<ForegroundApplication>> resolver;
    private final AtomicReference<GatePolicy> policy = new AtomicReference<>(
            new GatePolicy(
                    ExcludedApplicationPolicy.defaults(),
                    SensitiveContentPolicy.defaults()
            )
    );

    public ClipboardPrivacyGate(ForegroundApplicationResolver resolver) {
        this(Objects.requireNonNull(resolver, "resolver")::resolve);
    }

    ClipboardPrivacyGate(Supplier<Optional<ForegroundApplication>> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public void applyConfig(Config config) {
        Config effective = config == null ? Config.defaults() : config.normalized();
        policy.set(new GatePolicy(
                effective.excludedApplicationPolicy(),
                effective.sensitiveContentPolicy()
        ));
    }

    public ExcludedApplicationPolicy excludedApplicationPolicy() {
        return policy.get().excludedApplications();
    }

    public SensitiveContentPolicy sensitiveContentPolicy() {
        return policy.get().sensitiveContent();
    }

    public boolean isCaptureAllowed(String content) {
        GatePolicy current = policy.get();

        try {
            if (!current.sensitiveContent().allowsCapture(content)) return false;
        } catch (Throwable ignored) {
            // Sensitive-content inspection is best effort and fail-open.
        }

        ExcludedApplicationPolicy excluded = current.excludedApplications();
        if (excluded.empty()) return true;

        try {
            Optional<ForegroundApplication> application = resolver.get();
            if (application == null || application.isEmpty()) return true;

            ForegroundApplication foreground = application.get();
            if (!foreground.hasExecutableName()) return true;

            return !excluded.excludes(foreground.executableName());
        } catch (Throwable ignored) {
            return true;
        }
    }

    private record GatePolicy(
            ExcludedApplicationPolicy excludedApplications,
            SensitiveContentPolicy sensitiveContent
    ) {
        private GatePolicy {
            excludedApplications = Objects.requireNonNull(excludedApplications);
            sensitiveContent = Objects.requireNonNull(sensitiveContent);
        }
    }
}
