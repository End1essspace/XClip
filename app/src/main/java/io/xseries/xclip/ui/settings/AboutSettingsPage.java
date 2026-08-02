/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static io.xseries.xclip.ui.settings.SettingsPageSupport.actionRow;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.infoRow;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.informationSection;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.pageScroll;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.section;

public final class AboutSettingsPage {

    private AboutSettingsPage() {}

    public static ScrollPane create(
            String version,
            Consumer<String> openLink,
            Runnable showThirdPartyNotices
    ) {
        Consumer<String> linkAction = Objects.requireNonNull(openLink, "openLink");
        Runnable noticesAction = Objects.requireNonNull(
                showThirdPartyNotices,
                "showThirdPartyNotices"
        );

        VBox product = informationSection(
                "About XClip",
                "Local-first Windows clipboard management.",
                List.of(
                        infoRow("Version", version),
                        infoRow("Author", AboutSettingsContent.AUTHOR),
                        infoRow("License", AboutSettingsContent.LICENSE),
                        infoRow("Data model", "Local SQLite + config.json"),
                        infoRow("UI contract", "v1.3.0 revision 15")
                )
        );

        Button repository = linkButton(
                "Open GitHub repository",
                AboutSettingsContent.REPOSITORY_URL,
                linkAction
        );
        Button telegram = linkButton(
                "Open Telegram",
                AboutSettingsContent.TELEGRAM_URL,
                linkAction
        );
        Button license = linkButton(
                "Read GPL v3",
                AboutSettingsContent.GPL_URL,
                linkAction
        );
        var links = actionRow(
                Pos.CENTER_LEFT,
                repository,
                telegram,
                license
        );

        VBox linksSection = section(
                "Project links",
                "External links open only after an explicit button press.",
                links
        );

        Button notices = new Button("Third-party notices");
        notices.getStyleClass().add("btn-subtle");
        notices.setAccessibleHelp(
                "Show bundled third-party attribution and license notices."
        );
        notices.setOnAction(event -> noticesAction.run());

        VBox licensing = section(
                "Open-source licensing",
                "XClip is distributed under GPL-3.0-only. Selected Lucide icons are bundled under the ISC license.",
                notices
        );

        Label privacy = new Label(
                "Clipboard history and preferences stay in the local XClip data directory. "
                        + "XClip does not upload clipboard contents. Sensitive-content checks run locally, "
                        + "and existing history is not automatically scanned by those rules."
        );
        privacy.setWrapText(true);
        privacy.getStyleClass().add("settings-privacy-statement");

        VBox privacySection = section(
                "Data and privacy",
                "The product is designed around explicit local data ownership.",
                privacy
        );

        return pageScroll(product, linksSection, licensing, privacySection);
    }

    private static Button linkButton(
            String label,
            String url,
            Consumer<String> action
    ) {
        Button button = new Button(label);
        button.getStyleClass().add("btn-subtle");
        button.setAccessibleHelp("Open " + url + " in the default browser.");
        button.setOnAction(event -> action.accept(url));
        return button;
    }
}