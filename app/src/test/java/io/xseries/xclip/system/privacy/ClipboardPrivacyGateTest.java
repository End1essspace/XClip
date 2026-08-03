/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.privacy;

import io.xseries.xclip.config.Config;
import io.xseries.xclip.domain.privacy.SensitiveContentPolicy;
import io.xseries.xclip.system.privacy.ForegroundApplicationResolver.ForegroundApplication;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipboardPrivacyGateTest {

    @Test
    void blocksOnlyPositiveForegroundExecutableMatch() {
        AtomicReference<Optional<ForegroundApplication>> foreground =
                new AtomicReference<>(Optional.of(new ForegroundApplication(
                        42,
                        "C:\\Program Files\\Google\\Chrome.EXE",
                        "Private tab"
                )));
        ClipboardPrivacyGate gate = new ClipboardPrivacyGate(foreground::get);
        gate.applyConfig(Config.defaults().withExcludedApplications(List.of("chrome")));

        assertFalse(gate.isCaptureAllowed("ordinary clipboard text"));

        foreground.set(Optional.of(new ForegroundApplication(
                43,
                "firefox.exe",
                "Browser"
        )));
        assertTrue(gate.isCaptureAllowed("ordinary clipboard text"));
    }

    @Test
    void resolverFailureAndUnknownExecutableAreFailOpen() {
        ClipboardPrivacyGate missing = new ClipboardPrivacyGate(Optional::empty);
        missing.applyConfig(Config.defaults().withExcludedApplications(List.of("chrome.exe")));
        assertTrue(missing.isCaptureAllowed("text"));

        ClipboardPrivacyGate unidentified = new ClipboardPrivacyGate(() -> Optional.of(
                new ForegroundApplication(44, null, "Unknown window")
        ));
        unidentified.applyConfig(
                Config.defaults().withExcludedApplications(List.of("chrome.exe"))
        );
        assertTrue(unidentified.isCaptureAllowed("text"));

        Supplier<Optional<ForegroundApplication>> broken = () -> {
            throw new IllegalStateException("resolver unavailable");
        };
        ClipboardPrivacyGate failing = new ClipboardPrivacyGate(broken);
        failing.applyConfig(Config.defaults().withExcludedApplications(List.of("chrome.exe")));
        assertTrue(failing.isCaptureAllowed("text"));
    }

    @Test
    void enabledSensitiveRulesBlockMatchingContentWithoutForegroundLookup() {
        AtomicReference<Integer> resolverCalls = new AtomicReference<>(0);
        ClipboardPrivacyGate gate = new ClipboardPrivacyGate(() -> {
            resolverCalls.set(resolverCalls.get() + 1);
            return Optional.empty();
        });
        SensitiveContentPolicy policy = new SensitiveContentPolicy(
                SensitiveContentPolicy.RuleAction.SKIP,
                SensitiveContentPolicy.RuleAction.SKIP
        );
        gate.applyConfig(Config.defaults().withSensitiveContentPolicy(policy));

        assertFalse(gate.isCaptureAllowed("Card: 4111 1111 1111 1111"));
        assertFalse(gate.isCaptureAllowed("Your verification code is 482913"));
        assertEquals(0, resolverCalls.get().intValue());
        assertTrue(gate.isCaptureAllowed("Invoice 482913 was paid"));
    }

    @Test
    void applyingConfigChangesBothPoliciesWithoutRestart() {
        ClipboardPrivacyGate gate = new ClipboardPrivacyGate(() -> Optional.of(
                new ForegroundApplication(45, "notepad.exe", "Notes")
        ));

        gate.applyConfig(Config.defaults());
        assertTrue(gate.isCaptureAllowed("4111111111111111"));

        gate.applyConfig(
                Config.defaults().withExcludedApplications(List.of("notepad.exe"))
        );
        assertFalse(gate.isCaptureAllowed("ordinary text"));

        SensitiveContentPolicy cardPolicy = new SensitiveContentPolicy(
                SensitiveContentPolicy.RuleAction.SKIP,
                SensitiveContentPolicy.RuleAction.CAPTURE
        );
        gate.applyConfig(Config.defaults().withSensitiveContentPolicy(cardPolicy));
        assertFalse(gate.isCaptureAllowed("4111111111111111"));
        assertTrue(gate.isCaptureAllowed("ordinary text"));

        gate.applyConfig(Config.defaults());
        assertTrue(gate.isCaptureAllowed("4111111111111111"));
    }
}
