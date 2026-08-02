/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import io.xseries.xclip.domain.privacy.SensitiveContentPolicy;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import static io.xseries.xclip.ui.settings.SettingsPageSupport.actionRow;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.addSettingRow;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.pageScroll;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.section;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.settingsGrid;

public final class PrivacySettingsPage {

    private PrivacySettingsPage() {}

    public record Controls(
            TextArea excludedApplications,
            Button clearExcludedApplications,
            ComboBox<SensitiveContentPolicy.RuleAction> paymentCardAction,
            ComboBox<SensitiveContentPolicy.RuleAction> oneTimeCodeAction,
            Button resetSensitiveRules
    ) {}

    public static ScrollPane create(Controls controls) {
        GridPane privacyGrid = settingsGrid();
        addSettingRow(
                privacyGrid,
                0,
                "Excluded applications",
                "XClip skips clipboard changes while a listed executable owns the foreground window. Matching uses the executable name only and is case-insensitive.",
                controls.excludedApplications()
        );

        Label fallbackHint = new Label(
                "Resolver failures are fail-open: unidentified applications remain capturable instead of silently losing clipboard data."
        );
        fallbackHint.setWrapText(true);
        fallbackHint.getStyleClass().add("settings-privacy-hint");

        var privacyActions = actionRow(
                Pos.CENTER_RIGHT,
                controls.clearExcludedApplications()
        );

        VBox privacySection = section(
                "Excluded applications",
                "Process-based capture exclusions are stored locally in config.json.",
                privacyGrid,
                fallbackHint,
                privacyActions
        );
        privacySection.getStyleClass().add("privacy-settings-section");

        GridPane sensitiveGrid = settingsGrid();
        int row = 0;
        row = addSettingRow(
                sensitiveGrid,
                row,
                "Payment card numbers",
                "A match requires 13–19 digits, a valid Luhn checksum, and safe token boundaries.",
                controls.paymentCardAction()
        );
        addSettingRow(
                sensitiveGrid,
                row,
                "One-time codes",
                "Only 4–8 digit values near explicit OTP or verification wording are matched.",
                controls.oneTimeCodeAction()
        );

        Label detectionHint = new Label(
                "Detection runs locally. Standalone short numbers are not treated as OTP. Rules apply only to new clipboard changes; existing history is never scanned or deleted."
        );
        detectionHint.setWrapText(true);
        detectionHint.getStyleClass().add("settings-sensitive-hint");

        var sensitiveActions = actionRow(
                Pos.CENTER_RIGHT,
                controls.resetSensitiveRules()
        );

        VBox sensitiveSection = section(
                "Sensitive content",
                "Explicit opt-in rules can skip selected sensitive text before it reaches clipboard history.",
                sensitiveGrid,
                detectionHint,
                sensitiveActions
        );
        sensitiveSection.getStyleClass().add("sensitive-settings-section");

        return pageScroll(privacySection, sensitiveSection);
    }
}