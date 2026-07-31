/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui;

import io.xseries.xclip.config.Config;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;
import io.xseries.xclip.domain.service.TagNamePolicy;
import io.xseries.xclip.ui.components.UiIcon;
import io.xseries.xclip.ui.popup.ClipPreviewPolicy;
import io.xseries.xclip.ui.popup.PopupActionBar;
import io.xseries.xclip.ui.popup.PopupKeyBindings;
import io.xseries.xclip.ui.popup.PopupPerformancePolicy;
import io.xseries.xclip.ui.popup.PopupResponsivePolicy;
import io.xseries.xclip.ui.popup.TagChipPolicy;
import io.xseries.xclip.ui.popup.TagEditorModel;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UiContractFreezeTest {

    private static final String CONTRACT_RESOURCE = "/ui/ui-contract-v1.3.0.properties";

    @Test
    void frozenContractMatchesRuntimeConstantsAndEnums() throws Exception {
        Properties contract = loadContract();

        assertEquals("4", contract.getProperty("contract.version"));
        assertEquals("1.3.0", contract.getProperty("product.version"));
        assertEquals(Config.MIN_WINDOW_W, intValue(contract, "window.minWidth"));
        assertEquals(Config.MIN_WINDOW_H, intValue(contract, "window.minHeight"));
        assertEquals(
                PopupResponsivePolicy.COMPACT_MAX_WIDTH,
                doubleValue(contract, "popup.compactMaxWidth")
        );
        assertEquals(
                PopupResponsivePolicy.BALANCED_MAX_WIDTH,
                doubleValue(contract, "popup.balancedMaxWidth")
        );
        assertEquals(
                PopupResponsivePolicy.ROW_TIME_MIN_WIDTH,
                doubleValue(contract, "popup.rowTimeMinWidth")
        );
        assertEquals(
                ClipPreviewPolicy.MAX_EXPANDED_LINES,
                intValue(contract, "popup.previewMaxLines")
        );
        assertEquals(
                ClipPreviewPolicy.MAX_EXPANDED_CHARS,
                intValue(contract, "popup.previewMaxChars")
        );
        assertEquals(
                PopupPerformancePolicy.PREVIEW_CACHE_CAPACITY,
                intValue(contract, "popup.previewCacheCapacity")
        );
        assertEquals(
                PopupPerformancePolicy.CONTENT_TYPE_CACHE_CAPACITY,
                intValue(contract, "popup.contentTypeCacheCapacity")
        );
        assertEquals(
                PopupPerformancePolicy.TYPE_FILTER_SCAN_LIMIT,
                intValue(contract, "popup.typeFilterScanLimit")
        );
        assertEquals(
                PopupPerformancePolicy.SEARCH_DEBOUNCE_MS,
                longValue(contract, "popup.searchDebounceMs")
        );
        assertEquals(enumNames(ClipViewScope.values()), values(contract, "popup.scopes"));
        assertEquals(enumNames(ClipContentType.values()), values(contract, "popup.contentTypes"));
        assertEquals(enumNames(PopupActionBar.StatusTone.values()), values(contract, "popup.statusTones"));
        assertEquals(enumNames(UiDialogs.Tone.values()), values(contract, "dialog.tones"));
        assertEquals(
                TagNamePolicy.MAX_NAME_LENGTH,
                intValue(contract, "tags.maxNameLength")
        );
        assertEquals(
                enumNames(TagEditorModel.SelectionState.values()),
                values(contract, "tags.selectionStates")
        );
        assertEquals(
                "ATOMIC_MULTI_CLIP_EDIT",
                required(contract, "tags.assignmentMode")
        );
        assertEquals(
                List.of("ACTIONS_MENU", "ROW_CONTEXT_MENU", "MULTI_SELECTION"),
                values(contract, "tags.entryPoints")
        );
        assertEquals(
                TagChipPolicy.MAX_VISIBLE_CHIPS,
                intValue(contract, "tags.maxVisibleChips")
        );
        assertEquals("CHIPS_WITH_OVERFLOW", required(contract, "tags.rowMetadata"));
        assertEquals("TAG_ID", required(contract, "tags.filterMode"));
        assertEquals(
                List.of("CONTENT", "PINNED_TITLE", "TAG_NAME"),
                values(contract, "tags.searchFields")
        );
        assertEquals("ACTIONS_MENU", required(contract, "tags.managementEntryPoint"));
        assertEquals(
                List.of("LIST", "RENAME", "DELETE", "CLEANUP_UNUSED"),
                values(contract, "tags.managementActions")
        );
        assertEquals(
                "CLIP_ASSIGNMENT_COUNT",
                required(contract, "tags.managementUsageCount")
        );
        assertEquals(
                "CONFIRMED_CASCADE_ASSIGNMENTS",
                required(contract, "tags.deleteBehavior")
        );
        assertEquals(
                "CASE_INSENSITIVE_REJECT",
                required(contract, "tags.renameCollision")
        );
        assertEquals(UiStyles.popupResourcePaths(), values(contract, "popup.stylesheets"));
        assertEquals(UiStyles.settingsResourcePaths(), values(contract, "settings.stylesheets"));
        assertEquals(UiIcon.values().length, intValue(contract, "popup.iconCount"));
        assertEquals(shortcuts(), values(contract, "popup.shortcuts"));
    }

    @Test
    void contractResourceIsPackagedAndNonEmpty() throws Exception {
        try (InputStream stream = UiContractFreezeTest.class.getResourceAsStream(CONTRACT_RESOURCE)) {
            assertNotNull(stream, "Missing frozen UI contract resource");
            Properties contract = new Properties();
            contract.load(stream);
            assertEquals("1.3.0", contract.getProperty("product.version"));
        }
    }

    private static Properties loadContract() throws Exception {
        try (InputStream stream = UiContractFreezeTest.class.getResourceAsStream(CONTRACT_RESOURCE)) {
            assertNotNull(stream, "Missing frozen UI contract resource");
            Properties contract = new Properties();
            contract.load(stream);
            return contract;
        }
    }

    private static List<String> shortcuts() {
        return PopupKeyBindings.bindings().stream()
                .map(UiContractFreezeTest::shortcut)
                .toList();
    }

    private static String shortcut(PopupKeyBindings.Binding binding) {
        PopupKeyBindings.Stroke stroke = binding.stroke();
        StringJoiner keys = new StringJoiner("+");
        if (stroke.control()) keys.add("CTRL");
        if (stroke.shift()) keys.add("SHIFT");
        if (stroke.alt()) keys.add("ALT");
        if (stroke.meta()) keys.add("META");
        keys.add(stroke.code());

        return keys + ":" + binding.action().name() + ":"
                + (binding.allowedInTextInput() ? "TEXT" : "POPUP");
    }

    private static List<String> values(Properties contract, String key) {
        String raw = contract.getProperty(key);
        assertNotNull(raw, "Missing contract key: " + key);
        if (raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("\\|", -1)).toList();
    }

    private static List<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private static int intValue(Properties contract, String key) {
        return Integer.parseInt(required(contract, key));
    }

    private static long longValue(Properties contract, String key) {
        return Long.parseLong(required(contract, key));
    }

    private static double doubleValue(Properties contract, String key) {
        return Double.parseDouble(required(contract, key));
    }

    private static String required(Properties contract, String key) {
        String value = contract.getProperty(key);
        assertNotNull(value, "Missing contract key: " + key);
        return value;
    }
}




