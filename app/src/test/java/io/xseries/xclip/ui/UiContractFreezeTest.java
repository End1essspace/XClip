
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui;

import io.xseries.xclip.config.Config;
import io.xseries.xclip.data.db.Database;
import io.xseries.xclip.data.db.DatabaseMaintenanceService;
import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.privacy.ExcludedApplicationPolicy;
import io.xseries.xclip.domain.privacy.SensitiveContentPolicy;
import io.xseries.xclip.domain.retention.HistoryRetentionPolicy;
import io.xseries.xclip.domain.model.ClipViewScope;
import io.xseries.xclip.domain.service.HistoryCleanupService;
import io.xseries.xclip.domain.service.TagNamePolicy;
import io.xseries.xclip.ui.components.UiIcon;
import io.xseries.xclip.ui.popup.ClipPreviewPolicy;
import io.xseries.xclip.ui.popup.PopupActionBar;
import io.xseries.xclip.ui.popup.PopupKeyBindings;
import io.xseries.xclip.ui.popup.PopupPerformancePolicy;
import io.xseries.xclip.ui.popup.PopupResponsivePolicy;
import io.xseries.xclip.ui.popup.SearchUiModel;
import io.xseries.xclip.ui.popup.TagChipPolicy;
import io.xseries.xclip.ui.popup.TagEditorModel;
import io.xseries.xclip.ui.settings.DuplicateSettingsModel;
import io.xseries.xclip.ui.settings.SettingsPage;
import io.xseries.xclip.ui.settings.SettingsResponsivePolicy;
import io.xseries.xclip.validation.LargeDataValidationPolicy;
import io.xseries.xclip.system.SingleInstanceGuard;
import io.xseries.xclip.system.lifecycle.WindowsLifecycleCoordinator;
import io.xseries.xclip.system.lifecycle.WindowsLifecycleStateMachine;
import io.xseries.xclip.system.tray.HotkeyRegistrationStatus;
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

        assertEquals("18", contract.getProperty("contract.version"));
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
        assertEquals(
                List.of("TYPE", "IS", "TAG"),
                values(contract, "search.operators")
        );
        assertEquals("AND", required(contract, "search.toolbarCombination"));
        assertEquals("OR", required(contract, "search.positiveTypeCombination"));
        assertEquals("AND", required(contract, "search.positiveTagCombination"));
        assertEquals("EXCLUDE", required(contract, "search.negativeCombination"));
        assertEquals("TEXT", required(contract, "search.invalidFallback"));
        assertEquals(
                List.of("CONTENT", "PINNED_TITLE", "TAG_NAME"),
                values(contract, "search.textFields")
        );
        assertEquals(
                PopupPerformancePolicy.TYPE_FILTER_SCAN_LIMIT,
                intValue(contract, "search.derivedScanLimit")
        );
        assertEquals("DAO_STABLE", required(contract, "search.ordering"));
        assertEquals("GENERATION", required(contract, "search.staleResultGate"));
        assertEquals("INLINE_ASSIST", required(contract, "search.ui"));
        assertEquals(
                "FOCUS_OR_ACTIVE_QUERY",
                required(contract, "search.syntaxHint")
        );
        assertEquals(
                "CONTEXTUAL_TOKEN_REPLACE",
                required(contract, "search.suggestions")
        );
        assertEquals(
                SearchUiModel.MAX_VISIBLE_CHIPS,
                intValue(contract, "search.maxVisibleChips")
        );
        assertEquals(
                SearchUiModel.MAX_SUGGESTIONS,
                intValue(contract, "search.maxSuggestions")
        );
        assertEquals(
                "INLINE_NON_BLOCKING",
                required(contract, "search.errorDisplay")
        );
        assertEquals(
                "TEXT_REMAINDER_ONLY",
                required(contract, "search.highlight")
        );
        assertEquals(
                "DEFERRED_OPTIONAL",
                required(contract, "search.savedQueries")
        );
        assertEquals(
                "DEDICATED",
                required(contract, "duplicate.settingsSection")
        );
        assertEquals(
                enumNames(DuplicateBehaviorPolicy.RecentDuplicatePosition.values()),
                values(contract, "duplicate.recentPositions")
        );
        assertEquals(
                enumNames(DuplicateBehaviorPolicy.PinnedDuplicatePosition.values()),
                values(contract, "duplicate.pinnedPositions")
        );
        assertEquals(
                enumNames(DuplicateBehaviorPolicy.WhitespaceMode.values()),
                values(contract, "duplicate.whitespaceModes")
        );
        assertEquals(
                enumNames(DuplicateBehaviorPolicy.CaseSensitivity.values()),
                values(contract, "duplicate.caseModes")
        );
        assertEquals(
                enumNames(DuplicateSettingsModel.WindowPreset.values()),
                values(contract, "duplicate.windowPresets")
        );
        assertEquals(
                "MILLISECONDS",
                required(contract, "duplicate.customWindowUnit")
        );
        assertEquals(
                List.of("WHITESPACE", "CASE"),
                values(contract, "duplicate.exactOverrides")
        );
        assertEquals(
                "DUPLICATE_DEFAULTS_ONLY",
                required(contract, "duplicate.resetScope")
        );
        assertEquals("DEDICATED", required(contract, "privacy.settingsSection"));
        assertEquals(
                "EXECUTABLE_BASENAME",
                required(contract, "privacy.exclusionIdentity")
        );
        assertEquals("CASE_INSENSITIVE", required(contract, "privacy.matching"));
        assertEquals(
                "FOREGROUND_AT_CAPTURE_DETECTION",
                required(contract, "privacy.captureDecision")
        );
        assertEquals("FAIL_OPEN", required(contract, "privacy.resolverFailure"));
        assertEquals(
                ExcludedApplicationPolicy.MAX_APPLICATIONS,
                intValue(contract, "privacy.maxExcludedApplications")
        );
        assertEquals(
                ExcludedApplicationPolicy.MAX_EXECUTABLE_NAME_LENGTH,
                intValue(contract, "privacy.maxExecutableNameLength")
        );
        assertEquals(
                "EMPTY",
                required(contract, "privacy.defaultExcludedApplications")
        );
        assertEquals(
                "DEDICATED",
                required(contract, "privacy.sensitiveSettingsSection")
        );
        assertEquals(
                enumNames(SensitiveContentPolicy.RuleAction.values()),
                values(contract, "privacy.sensitiveActions")
        );
        assertEquals(
                enumNames(SensitiveContentPolicy.SensitiveKind.values()),
                values(contract, "privacy.sensitiveKinds")
        );
        assertEquals(
                "LUHN_13_19_BOUNDARY",
                required(contract, "privacy.paymentCardDetection")
        );
        assertEquals(
                "CONTEXTUAL_4_8_DIGITS",
                required(contract, "privacy.oneTimeCodeDetection")
        );
        assertEquals("CAPTURE", required(contract, "privacy.defaultSensitiveActions"));
        assertEquals("FAIL_OPEN", required(contract, "privacy.sensitiveFailure"));
        assertEquals("NONE", required(contract, "privacy.sensitiveHistoryMutation"));
        assertEquals("DEDICATED", required(contract, "history.settingsSection"));
        assertEquals("RECENT_ONLY", required(contract, "history.autoDeleteScope"));
        assertEquals("PRESERVE_ALWAYS", required(contract, "history.pinnedBehavior"));
        assertEquals("DAYS", required(contract, "history.ageUnit"));
        assertEquals(
                HistoryRetentionPolicy.MAX_MAX_AGE_DAYS,
                intValue(contract, "history.maxAgeDays")
        );
        assertEquals(
                enumNames(ClipContentType.values()),
                values(contract, "history.typeOverrides")
        );
        assertEquals(
                HistoryRetentionPolicy.TYPE_RULE_DISABLED,
                intValue(contract, "history.typeRuleDisabled")
        );
        assertEquals("SHORTEST_AGE_WINS", required(contract, "history.ruleCombination"));
        assertEquals(
                "RECENT_ONLY_EXPLICIT",
                required(contract, "history.clearOnExit")
        );
        assertEquals(
                enumNames(HistoryCleanupService.CleanupTrigger.values()),
                values(contract, "history.cleanupTriggers")
        );
        assertEquals(
                HistoryCleanupService.PERIODIC_INTERVAL_HOURS,
                longValue(contract, "history.periodicIntervalHours")
        );
        assertEquals(
                "RUNTIME_LAST_RESULT",
                required(contract, "history.cleanupStatus")
        );
        assertEquals("NONE", required(contract, "history.schemaMutation"));
        assertEquals(UiStyles.popupResourcePaths(), values(contract, "popup.stylesheets"));
        assertEquals(UiStyles.settingsResourcePaths(), values(contract, "settings.stylesheets"));
        assertEquals(
                enumNames(SettingsPage.values()),
                values(contract, "settings.pages")
        );
        assertEquals("GENERAL", required(contract, "settings.defaultPage"));
        assertEquals("LEFT_SIDEBAR", required(contract, "settings.navigation"));
        assertEquals(
                "CUSTOM_UNDECORATED",
                required(contract, "settings.windowChrome")
        );
        assertEquals("INDEPENDENT", required(contract, "settings.pageScroll"));
        assertEquals(
                List.of("BASELINE", "CURRENT", "VALIDATION"),
                values(contract, "settings.draftState")
        );
        assertEquals(
                "DIRTY_AND_VALID_ONLY",
                required(contract, "settings.applyState")
        );
        assertEquals(
                "RESTORE_BASELINE",
                required(contract, "settings.cancelBehavior")
        );
        assertEquals(
                List.of("FIELD_ERROR", "PAGE_ERROR", "FIRST_ERROR_FOCUS"),
                values(contract, "settings.validation")
        );
        assertEquals("SECTION_ONLY", required(contract, "settings.resetScope"));
        assertEquals(
                "QUICK_HELP_CONTENT",
                required(contract, "settings.shortcutsSource")
        );
        assertEquals("CTRL_SHIFT_V", required(contract, "settings.globalHotkey"));
        assertEquals(
                enumNames(HotkeyRegistrationStatus.values()),
                values(contract, "settings.hotkeyStates")
        );
        assertEquals(
                List.of("DATA_DIRECTORY", "DATABASE", "CONFIGURATION"),
                values(contract, "settings.dataPaths")
        );
        assertEquals(
                List.of(
                        "OPEN_FOLDER",
                        "COPY_PATH",
                        "REFRESH_STATUS",
                        "INTEGRITY_CHECK",
                        "RUN_RETENTION",
                        "CHECKPOINT_WAL",
                        "OPTIMIZE_DATABASE",
                        "CREATE_BACKUP",
                        "RESTORE_BACKUP",
                        "CLEAR_RECENT",
                        "CLEAR_ALL"
                ),
                values(contract, "settings.dataActions")
        );
        assertEquals(
                "ASYNC_EXCLUSIVE",
                required(contract, "settings.dataOperations")
        );
        assertEquals(
                "VERSIONED_ARCHIVE_VALIDATED_ATOMIC_REPLACE",
                required(contract, "settings.backupRestore")
        );
        assertEquals(
                Database.CURRENT_SCHEMA_VERSION,
                intValue(contract, "database.schemaVersion")
        );
        assertEquals(
                "PRAGMA_INTEGRITY_CHECK",
                required(contract, "database.integrityCheck")
        );
        assertEquals(
                "TRUNCATE_EXPLICIT",
                required(contract, "database.checkpoint")
        );
        assertEquals(
                "EXPLICIT_OFF_UI_THREAD",
                required(contract, "database.vacuum")
        );
        assertEquals(
                DatabaseMaintenanceService.BACKUP_FORMAT_VERSION,
                intValue(contract, "database.backupFormat")
        );
        assertEquals(
                List.of("MANIFEST", "DATABASE", "CONFIGURATION"),
                values(contract, "database.backupEntries")
        );
        assertEquals(
                "VALIDATE_THEN_REPLACE_AND_EXIT",
                required(contract, "database.restore")
        );
        assertEquals(
                "TRANSACTIONAL_ROLLBACK_RETRY",
                required(contract, "database.migration")
        );
        assertEquals(
                "REJECT_BEFORE_MUTATION",
                required(contract, "database.forwardVersion")
        );
        assertEquals(
                "M7_DATABASE_GATE",
                required(contract, "database.regressionGate")
        );
        assertEquals(
                LargeDataValidationPolicy.DATASET_SIZES.stream()
                        .map(String::valueOf)
                        .toList(),
                values(contract, "performance.datasets")
        );
        assertEquals(
                LargeDataValidationPolicy.LARGE_CLIP_CHARACTERS,
                intValue(contract, "performance.largeClipCharacters")
        );
        assertEquals(
                LargeDataValidationPolicy.MANY_PINNED_COUNT,
                intValue(contract, "performance.manyPinnedCount")
        );
        assertEquals(
                LargeDataValidationPolicy.MANY_TAGS_COUNT,
                intValue(contract, "performance.manyTagsCount")
        );
        assertEquals(
                LargeDataValidationPolicy.DUPLICATE_CANDIDATE_COUNT,
                intValue(contract, "performance.duplicateCandidateCount")
        );
        assertEquals(
                LargeDataValidationPolicy.RETENTION_ELIGIBLE_COUNT,
                intValue(contract, "performance.retentionEligibleCount")
        );
        assertEquals(
                LargeDataValidationPolicy.RAPID_SEARCH_CHURN_ITERATIONS,
                intValue(contract, "performance.searchChurnIterations")
        );
        assertEquals(
                LargeDataValidationPolicy.POPUP_RESULT_LIMIT,
                intValue(contract, "performance.popupResultLimit")
        );
        assertEquals(
                LargeDataValidationPolicy.MAX_HEAP_MIB,
                longValue(contract, "performance.maxHeapMiB")
        );
        assertEquals(
                LargeDataValidationPolicy.MAX_USED_HEAP_MIB,
                longValue(contract, "performance.maxUsedHeapMiB")
        );
        assertEquals(
                LargeDataValidationPolicy.MAX_DATABASE_MIB,
                longValue(contract, "performance.maxDatabaseMiB")
        );
        assertEquals(
                List.of(
                        LargeDataValidationPolicy.STARTUP_1K_P95_MAX_MILLIS,
                        LargeDataValidationPolicy.STARTUP_10K_P95_MAX_MILLIS,
                        LargeDataValidationPolicy.STARTUP_50K_P95_MAX_MILLIS
                ).stream().map(String::valueOf).toList(),
                values(contract, "performance.startupP95Millis")
        );
        assertEquals(
                LargeDataValidationPolicy.POPUP_PIPELINE_P95_MAX_MILLIS,
                longValue(contract, "performance.popupPipelineP95Millis")
        );
        assertEquals(
                LargeDataValidationPolicy.POPUP_FX_MATERIALIZATION_P95_MAX_MILLIS,
                longValue(contract, "performance.popupFxMaterializationP95Millis")
        );
        assertEquals(
                LargeDataValidationPolicy.POPUP_OPEN_COMPOSITE_P95_MAX_MILLIS,
                longValue(contract, "performance.popupOpenCompositeP95Millis")
        );
        assertEquals(
                LargeDataValidationPolicy.SEARCH_P95_MAX_MILLIS,
                longValue(contract, "performance.searchP95Millis")
        );
        assertEquals(
                LargeDataValidationPolicy.TAG_SEARCH_P95_MAX_MILLIS,
                longValue(contract, "performance.tagSearchP95Millis")
        );
        assertEquals(
                LargeDataValidationPolicy.TYPE_FILTER_P95_MAX_MILLIS,
                longValue(contract, "performance.typeFilterP95Millis")
        );
        assertEquals(
                LargeDataValidationPolicy.DUPLICATE_LOOKUP_P95_MAX_MILLIS,
                longValue(contract, "performance.duplicateLookupP95Millis")
        );
        assertEquals(
                LargeDataValidationPolicy.ROW_BUILD_P95_MAX_MILLIS,
                longValue(contract, "performance.rowBuildP95Millis")
        );
        assertEquals(
                LargeDataValidationPolicy.LARGE_CLIP_POLICY_MAX_MILLIS,
                longValue(contract, "performance.largeClipPolicyMillis")
        );
        assertEquals(
                LargeDataValidationPolicy.RETENTION_CLEANUP_MAX_MILLIS,
                longValue(contract, "performance.retentionCleanupMillis")
        );
        assertEquals(
                LargeDataValidationPolicy.SEARCH_CHURN_TOTAL_MAX_MILLIS,
                longValue(contract, "performance.searchChurnTotalMillis")
        );
        assertEquals(
                LargeDataValidationPolicy.FX_QUEUE_P95_MAX_MILLIS,
                longValue(contract, "performance.fxQueueP95Millis")
        );
        assertEquals(
                LargeDataValidationPolicy.FX_QUEUE_MAX_STALL_MILLIS,
                longValue(contract, "performance.fxMaxStallMillis")
        );
        assertEquals(
                List.of(
                        "SUMMARY_JSON",
                        "METRICS_CSV",
                        "ENVIRONMENT_PROPERTIES"
                ),
                values(contract, "performance.evidence")
        );
        assertEquals(
                "M7_LARGE_DATA_GATE",
                required(contract, "performance.regressionGate")
        );
        assertEquals(
                WindowsLifecycleCoordinator.HEARTBEAT_INTERVAL_SECONDS,
                longValue(contract, "lifecycle.heartbeatSeconds")
        );
        assertEquals(
                WindowsLifecycleStateMachine.DEFAULT_RESUME_GAP_MILLIS,
                longValue(contract, "lifecycle.resumeGapMillis")
        );
        assertEquals(
                SingleInstanceGuard.DEFAULT_PORT,
                intValue(contract, "lifecycle.singleInstancePort")
        );
        assertEquals(
                "VISIBLE_ERROR_AND_ABORT",
                required(contract, "lifecycle.portConflict")
        );
        assertEquals(
                HistoryCleanupService.EXIT_CLEANUP_TIMEOUT_MILLIS,
                longValue(contract, "lifecycle.exitCleanupTimeoutMillis")
        );
        assertEquals(
                "SWITCHABLE_INPUT_DESKTOP",
                required(contract, "lifecycle.sessionProbe")
        );
        assertEquals(
                "SHELL_PROCESS_ID",
                required(contract, "lifecycle.explorerProbe")
        );
        assertEquals(
                List.of("BOUNDS", "SCALE", "DPI"),
                values(contract, "lifecycle.displayProbe")
        );
        assertEquals(
                "RESTART_WITH_CLIPBOARD_SNAPSHOT",
                required(contract, "lifecycle.watcherResume")
        );
        assertEquals(
                List.of(
                        "LOCK",
                        "UNLOCK",
                        "RESUME",
                        "DISPLAY_CHANGE",
                        "EXPLORER_RESTART"
                ),
                values(contract, "lifecycle.directPasteBoundary")
        );
        assertEquals(
                "IDEMPOTENT_REINSTALL",
                required(contract, "lifecycle.trayRecovery")
        );
        assertEquals(
                "RESTART_UNLESS_CONFLICT",
                required(contract, "lifecycle.hotkeyRecovery")
        );
        assertEquals(
                "LOOPBACK_ACKNOWLEDGED",
                required(contract, "lifecycle.singleInstance")
        );
        assertEquals(
                "REPAIR_STALE_CURRENT_LAUNCHER",
                required(contract, "lifecycle.autostart")
        );
        assertEquals(
                "1322455b-12c4-4363-b896-12cd27ac3e3d",
                required(contract, "lifecycle.msiUpgradeUuid")
        );
        assertEquals(
                "OUTSIDE_INSTALL_DIRECTORY",
                required(contract, "lifecycle.userData")
        );
        assertEquals(
                List.of(
                        "CLEAN_START",
                        "AUTOSTART",
                        "START_MINIMIZED",
                        "TRAY",
                        "SECONDARY_LAUNCH",
                        "EXPLORER_RESTART",
                        "SLEEP_RESUME",
                        "LOCK_UNLOCK",
                        "DISPLAY_TOPOLOGY",
                        "MONITOR_DISCONNECT",
                        "DPI_CHANGE",
                        "LOGOFF",
                        "SHUTDOWN",
                        "HOTKEY_CONFLICT",
                        "STALE_AUTOSTART",
                        "MSI_UPGRADE",
                        "UNINSTALL",
                        "REINSTALL"
                ),
                values(contract, "lifecycle.validationMatrix")
        );
        assertEquals(
                "M8_WINDOWS_LIFECYCLE_GATE",
                required(contract, "lifecycle.regressionGate")
        );
        assertEquals(
                List.of(
                        "VERSION",
                        "AUTHOR",
                        "LICENSE",
                        "THIRD_PARTY_NOTICES",
                        "PROJECT_LINKS",
                        "LOCAL_DATA_STATEMENT"
                ),
                values(contract, "settings.aboutContent")
        );
        assertEquals(
                enumNames(SettingsResponsivePolicy.LayoutMode.values()),
                values(contract, "settings.responsiveModes")
        );
        assertEquals(
                (int) SettingsResponsivePolicy.COMPACT_MAX_WIDTH,
                intValue(contract, "settings.compactMaxWidth")
        );
        assertEquals(
                (int) SettingsResponsivePolicy.WIDE_MIN_WIDTH,
                intValue(contract, "settings.wideMinWidth")
        );
        assertEquals(
                (int) SettingsResponsivePolicy.MIN_WIDTH
                        + "x"
                        + (int) SettingsResponsivePolicy.MIN_HEIGHT,
                required(contract, "settings.minimumWindow")
        );
        assertEquals(
                "VISUAL_BOUNDS_AWARE",
                required(contract, "settings.initialSizing")
        );
        assertEquals(
                "TWO_COLUMN_TO_STACKED",
                required(contract, "settings.gridLayout")
        );
        assertEquals("WRAPPING", required(contract, "settings.actionLayout"));
        assertEquals(
                List.of(
                        "NAMED_NAVIGATION",
                        "NAMED_PAGE_SCROLL",
                        "KEYBOARD_VALIDATION_ACTION",
                        "VISIBLE_FOCUS"
                ),
                values(contract, "settings.accessibility")
        );
        assertEquals(
                "SELECTED_NAVIGATION",
                required(contract, "settings.initialFocus")
        );
        assertEquals(
                List.of("MOUSE", "ENTER", "SPACE"),
                values(contract, "settings.validationActivation")
        );
        assertEquals(
                "M6_SETTINGS_GATE",
                required(contract, "settings.regressionGate")
        );
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
