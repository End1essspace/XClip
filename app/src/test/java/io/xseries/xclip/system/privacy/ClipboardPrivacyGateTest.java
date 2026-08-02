/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.privacy;

import io.xseries.xclip.config.Config;
import io.xseries.xclip.system.privacy.ForegroundApplicationResolver.ForegroundApplication;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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

        assertFalse(gate.isCaptureAllowed());

        foreground.set(Optional.of(new ForegroundApplication(
                43,
                "firefox.exe",
                "Browser"
        )));
        assertTrue(gate.isCaptureAllowed());
    }

    @Test
    void resolverFailureAndUnknownExecutableAreFailOpen() {
        ClipboardPrivacyGate missing = new ClipboardPrivacyGate(Optional::empty);
        missing.applyConfig(Config.defaults().withExcludedApplications(List.of("chrome.exe")));
        assertTrue(missing.isCaptureAllowed());

        ClipboardPrivacyGate unidentified = new ClipboardPrivacyGate(() -> Optional.of(
                new ForegroundApplication(44, null, "Unknown window")
        ));
        unidentified.applyConfig(
                Config.defaults().withExcludedApplications(List.of("chrome.exe"))
        );
        assertTrue(unidentified.isCaptureAllowed());

        Supplier<Optional<ForegroundApplication>> broken = () -> {
            throw new IllegalStateException("resolver unavailable");
        };
        ClipboardPrivacyGate failing = new ClipboardPrivacyGate(broken);
        failing.applyConfig(Config.defaults().withExcludedApplications(List.of("chrome.exe")));
        assertTrue(failing.isCaptureAllowed());
    }

    @Test
    void applyingConfigChangesPolicyWithoutRestart() {
        ClipboardPrivacyGate gate = new ClipboardPrivacyGate(() -> Optional.of(
                new ForegroundApplication(45, "notepad.exe", "Notes")
        ));

        gate.applyConfig(Config.defaults());
        assertTrue(gate.isCaptureAllowed());

        gate.applyConfig(
                Config.defaults().withExcludedApplications(List.of("notepad.exe"))
        );
        assertFalse(gate.isCaptureAllowed());

        gate.applyConfig(Config.defaults());
        assertTrue(gate.isCaptureAllowed());
    }
}
