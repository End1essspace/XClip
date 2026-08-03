/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipPrimaryAction;

import java.util.Optional;

/**
 * Resolves safe type-aware actions and prepares clipboard output for them.
 *
 * This service is deterministic and does not touch the OS, JavaFX, or the
 * system clipboard. External URL/path opening is delegated to the system layer.
 */
public final class ClipContentActionService {

    private static final Gson PRETTY_JSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private ClipContentActionService() {}

    public static ClipPrimaryAction primaryActionFor(ClipContentType type) {
        if (type == null) return ClipPrimaryAction.NONE;

        return switch (type) {
            case TEXT -> ClipPrimaryAction.NONE;
            case URL -> ClipPrimaryAction.OPEN_URL;
            case PATH -> ClipPrimaryAction.REVEAL_PATH;
            case JSON -> ClipPrimaryAction.COPY_FORMATTED_JSON;
            case CODE -> ClipPrimaryAction.COPY_CODE;
            case COMMAND -> ClipPrimaryAction.COPY_COMMAND;
        };
    }

    /**
     * Returns the text that should be written to the clipboard for a copy action.
     * External actions deliberately return an empty result.
     */
    public static Optional<String> clipboardTextFor(
            ClipPrimaryAction action,
            String originalContent
    ) {
        if (action == null || originalContent == null) return Optional.empty();

        return switch (action) {
            case COPY_FORMATTED_JSON -> formatJson(originalContent);
            case COPY_CODE, COPY_COMMAND -> Optional.of(originalContent);
            case NONE, OPEN_URL, REVEAL_PATH -> Optional.empty();
        };
    }

    /**
     * Pretty-prints only JSON objects and arrays. Invalid input and scalar JSON
     * values are rejected so the behavior matches the JSON classifier contract.
     */
    public static Optional<String> formatJson(String content) {
        if (content == null || content.isBlank()) return Optional.empty();

        try {
            JsonElement parsed = JsonParser.parseString(content.trim());
            if (!parsed.isJsonObject() && !parsed.isJsonArray()) {
                return Optional.empty();
            }
            return Optional.of(PRETTY_JSON.toJson(parsed));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
