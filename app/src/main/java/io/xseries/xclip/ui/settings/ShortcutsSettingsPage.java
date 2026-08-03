/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.settings;

import io.xseries.xclip.system.tray.HotkeyRegistrationStatus;
import io.xseries.xclip.ui.popup.QuickHelpContent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.xseries.xclip.ui.settings.SettingsPageSupport.infoRow;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.informationSection;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.pageScroll;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.section;

public final class ShortcutsSettingsPage {

    private ShortcutsSettingsPage() {}

    public static View create(HotkeyRegistrationStatus initialStatus) {
        Label statusValue = new Label();
        statusValue.getStyleClass().add("settings-status-value");
        statusValue.setAccessibleText("Global hotkey registration status");

        Label statusDetail = new Label();
        statusDetail.setWrapText(true);
        statusDetail.getStyleClass().add("settings-field-description");

        Label shortcut = new Label("Ctrl+Shift+V");
        shortcut.getStyleClass().add("settings-shortcut-keys");
        shortcut.setAccessibleText("Global hotkey Ctrl Shift V");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox statusRow = new HBox(14, shortcut, spacer, statusValue);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.getStyleClass().add("settings-info-row");

        Label fixedContract = new Label(
                "The global shortcut is fixed in v1.3.0. Rebinding is not available yet."
        );
        fixedContract.setWrapText(true);
        fixedContract.getStyleClass().add("settings-inline-note");

        VBox globalHotkey = section(
                "Global hotkey",
                "Open XClip and capture the previously active window as the Direct Paste target.",
                statusRow,
                statusDetail,
                fixedContract
        );

        List<Node> cards = new ArrayList<>();
        cards.add(globalHotkey);
        for (QuickHelpContent.Section shortcutSection : QuickHelpContent.sections()) {
            List<HBox> rows = shortcutSection.shortcuts().stream()
                    .map(item -> infoRow(item.description(), item.keys()))
                    .toList();
            cards.add(informationSection(
                    shortcutSection.title(),
                    "This is the same frozen shortcut contract shown by popup Quick Help.",
                    rows
            ));
        }

        View view = new View(
                pageScroll(cards.toArray(Node[]::new)),
                statusValue,
                statusDetail
        );
        view.updateHotkeyStatus(initialStatus);
        return view;
    }

    public record View(
            ScrollPane root,
            Label statusValue,
            Label statusDetail
    ) {
        public View {
            root = Objects.requireNonNull(root, "root");
            statusValue = Objects.requireNonNull(statusValue, "statusValue");
            statusDetail = Objects.requireNonNull(statusDetail, "statusDetail");
        }

        public void updateHotkeyStatus(HotkeyRegistrationStatus status) {
            HotkeyRegistrationStatus value = Objects.requireNonNullElse(
                    status,
                    HotkeyRegistrationStatus.FAILED
            );
            statusValue.setText(value.label());
            statusDetail.setText(value.detail());
            statusValue.getStyleClass().removeAll(List.of(
                    "status-active",
                    "status-problem",
                    "status-neutral"
            ));
            statusValue.getStyleClass().add(
                    value == HotkeyRegistrationStatus.ACTIVE
                            ? "status-active"
                            : value.problem() ? "status-problem" : "status-neutral"
            );
        }
    }
}
