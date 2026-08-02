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
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

import static io.xseries.xclip.ui.settings.SettingsPageSupport.pageScroll;
import static io.xseries.xclip.ui.settings.SettingsPageSupport.section;

public final class DataSettingsPage {

    private DataSettingsPage() {}

    public static ScrollPane create(
            Path dataDirectory,
            Runnable openDataFolder,
            Runnable clearAllData,
            Consumer<String> statusSink
    ) {
        Path directory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        Runnable openAction = Objects.requireNonNull(openDataFolder, "openDataFolder");
        Runnable clearAction = Objects.requireNonNull(clearAllData, "clearAllData");
        Consumer<String> status = Objects.requireNonNull(statusSink, "statusSink");

        TextField dataPath = new TextField(directory.toAbsolutePath().toString());
        dataPath.setEditable(false);
        dataPath.setFocusTraversable(true);
        dataPath.setAccessibleText("XClip data folder path");
        dataPath.setAccessibleHelp("Read-only path. Use Ctrl+C to copy selected text.");
        dataPath.setPrefColumnCount(28);
        dataPath.getStyleClass().add("data-path");

        Button copyPath = new Button("Copy path");
        copyPath.setAccessibleHelp("Copy the XClip data folder path to the clipboard.");
        copyPath.getStyleClass().add("btn-subtle");
        copyPath.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(dataPath.getText());
            Clipboard.getSystemClipboard().setContent(content);
            status.accept("Path copied");
        });

        HBox pathRow = new HBox(10, dataPath, copyPath);
        pathRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(dataPath, Priority.ALWAYS);

        Button openFolder = new Button("Open data folder");
        openFolder.setAccessibleHelp("Open the XClip data folder in File Explorer.");
        openFolder.getStyleClass().add("btn-subtle");
        openFolder.setOnAction(event -> openAction.run());

        HBox dataActions = new HBox(openFolder);
        dataActions.setAlignment(Pos.CENTER_RIGHT);

        VBox dataSection = section(
                "Local data",
                "All history and preferences stay in this user-owned folder.",
                pathRow,
                dataActions
        );

        Button clearData = new Button("Clear ALL data");
        clearData.setAccessibleHelp("Permanently delete clipboard history and configuration.");
        clearData.getStyleClass().add("button-danger");
        clearData.setOnAction(event -> clearAction.run());

        Label dangerTitle = new Label("Danger zone");
        dangerTitle.getStyleClass().add("danger-title");

        Label dangerHint = new Label("Clearing data deletes clipboard history and config.json.");
        dangerHint.getStyleClass().add("settings-section-description");

        VBox dangerBox = new VBox(8, dangerTitle, dangerHint, clearData);
        dangerBox.getStyleClass().addAll("settings-section", "danger-box");
        return pageScroll(dataSection, dangerBox);
    }
}
