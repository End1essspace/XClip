/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.service;

import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipPrimaryAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ClipContentActionServiceTest {

    @Test
    void resolvesOneSafePrimaryActionPerSupportedType() {
        assertEquals(ClipPrimaryAction.NONE,
                ClipContentActionService.primaryActionFor(ClipContentType.TEXT));
        assertEquals(ClipPrimaryAction.OPEN_URL,
                ClipContentActionService.primaryActionFor(ClipContentType.URL));
        assertEquals(ClipPrimaryAction.REVEAL_PATH,
                ClipContentActionService.primaryActionFor(ClipContentType.PATH));
        assertEquals(ClipPrimaryAction.COPY_FORMATTED_JSON,
                ClipContentActionService.primaryActionFor(ClipContentType.JSON));
        assertEquals(ClipPrimaryAction.COPY_CODE,
                ClipContentActionService.primaryActionFor(ClipContentType.CODE));
        assertEquals(ClipPrimaryAction.COPY_COMMAND,
                ClipContentActionService.primaryActionFor(ClipContentType.COMMAND));
    }

    @Test
    void prettyPrintsJsonObjectWithoutChangingValues() {
        String source = "{\"name\":\"XClip\",\"enabled\":true,\"items\":[1,2]}";

        String formatted = ClipContentActionService.formatJson(source).orElseThrow();

        assertTrue(formatted.contains("\n"));
        assertTrue(formatted.contains("  \"name\": \"XClip\""));
        assertTrue(formatted.contains("  \"enabled\": true"));
        assertTrue(formatted.contains("    1"));
    }

    @Test
    void rejectsMalformedAndScalarJson() {
        assertTrue(ClipContentActionService.formatJson("{broken}").isEmpty());
        assertTrue(ClipContentActionService.formatJson("42").isEmpty());
        assertTrue(ClipContentActionService.formatJson("null").isEmpty());
    }

    @Test
    void copyActionsPreserveOriginalCodeAndCommand() {
        String code = "public final class Test {}";
        String command = "git status --short";

        assertEquals(code, ClipContentActionService.clipboardTextFor(
                ClipPrimaryAction.COPY_CODE, code).orElseThrow());
        assertEquals(command, ClipContentActionService.clipboardTextFor(
                ClipPrimaryAction.COPY_COMMAND, command).orElseThrow());
    }

    @Test
    void externalActionsNeverProduceClipboardText() {
        assertTrue(ClipContentActionService.clipboardTextFor(
                ClipPrimaryAction.OPEN_URL, "https://example.com").isEmpty());
        assertTrue(ClipContentActionService.clipboardTextFor(
                ClipPrimaryAction.REVEAL_PATH, "C:\\Temp").isEmpty());
    }
}
