/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.validation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.xseries.xclip.data.dao.ClipEntryDao;
import io.xseries.xclip.data.dao.TagDao;
import io.xseries.xclip.data.db.Database;
import io.xseries.xclip.data.db.SqliteConnectionConfig;
import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.data.model.ClipTag;
import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;
import io.xseries.xclip.domain.duplicate.DuplicateContentKeys;
import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;
import io.xseries.xclip.domain.retention.HistoryRetentionPolicy;
import io.xseries.xclip.domain.search.SearchExecutionPlan;
import io.xseries.xclip.domain.search.SearchQueryParser;
import io.xseries.xclip.domain.search.SearchQueryExecutor;
import io.xseries.xclip.domain.service.ClipContentClassifier;
import io.xseries.xclip.domain.service.HistoryCleanupService;
import io.xseries.xclip.ui.popup.ClipPreviewPolicy;
import io.xseries.xclip.ui.popup.PopupPerformancePolicy;
import io.xseries.xclip.ui.popup.PopupReloadCache;
import io.xseries.xclip.ui.popup.PopupRow;
import io.xseries.xclip.ui.popup.PopupRows;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Explicit M7.3 release harness.
 *
 * The harness creates isolated temporary databases and never opens the user's
 * live XClip data directory. It exercises the production DAO/search/retention
 * paths, records latency/memory/database-size evidence, and fails when a frozen
 * release budget is exceeded.
 */
public final class LargeDataValidationMain {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DUPLICATE_CONTENT =
            "M7 shared duplicate candidate payload for indexed lookup";
    private static final int UNIQUE_SEARCH_INDEX = 42_000;
    private static final long MIB = 1024L * 1024L;

    private final Path reportDirectory;
    private final List<Metric> metrics = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private final List<DatasetEvidence> datasets = new ArrayList<>();

    private LargeDataValidationMain(Path reportDirectory) {
        this.reportDirectory = reportDirectory;
    }

    public static void main(String[] args) throws Exception {
        Path reportDirectory = args.length > 0
                ? Path.of(args[0]).toAbsolutePath().normalize()
                : Path.of("build", "reports", "m7-large-data")
                        .toAbsolutePath()
                        .normalize();
        new LargeDataValidationMain(reportDirectory).run();
    }

    private void run() throws Exception {
        Files.createDirectories(reportDirectory);
        clearPreviousEvidence();

        Instant startedAt = Instant.now();
        Path fixtureRoot = Files.createTempDirectory("xclip-m7-large-data-");
        HeapSampler heapSampler = new HeapSampler();
        FxQueueProbe fxProbe = null;
        Throwable unexpectedFailure = null;

        try {
            requireBoundedHeap();
            FxRuntime.start();
            fxProbe = new FxQueueProbe();
            fxProbe.start();
            heapSampler.start();

            Map<Integer, Path> databaseBySize = new LinkedHashMap<>();
            for (int size : LargeDataValidationPolicy.DATASET_SIZES) {
                Path databasePath = fixtureRoot.resolve("xclip-" + size + ".db");
                boolean fullMatrix = size == 50_000;
                long buildStarted = System.nanoTime();
                FixtureSummary fixture = LargeDataFixtureBuilder.build(
                        databasePath,
                        size,
                        fullMatrix
                );
                double buildMillis = elapsedMillis(buildStarted);

                budget(
                        "fixture-build",
                        String.valueOf(size),
                        "ms",
                        buildMillis,
                        LargeDataValidationPolicy.fixtureBudgetMillis(size)
                );
                exact("fixture-row-count", String.valueOf(size), fixture.clipCount(), size);
                budget(
                        "database-size",
                        String.valueOf(size),
                        "MiB",
                        bytesToMib(fixture.databaseBytes()),
                        LargeDataValidationPolicy.MAX_DATABASE_MIB
                );

                Latency startup = measureStartup(databasePath);
                budget(
                        "startup-p95",
                        String.valueOf(size),
                        "ms",
                        startup.p95Millis(),
                        LargeDataValidationPolicy.startupBudgetMillis(size)
                );
                datasets.add(new DatasetEvidence(
                        size,
                        fixture.databaseBytes(),
                        buildMillis,
                        startup.medianMillis(),
                        startup.p95Millis()
                ));
                databaseBySize.put(size, databasePath);
            }

            Path fullDatabase = databaseBySize.get(50_000);
            validateFullMatrix(fullDatabase);
            validateRetentionCleanup(fullDatabase, fixtureRoot);
            validateLargeClipPolicy();

            Thread.sleep(150L);
        } catch (Throwable failure) {
            unexpectedFailure = failure;
            failures.add("Unexpected harness failure: " + safeMessage(failure));
        } finally {
            heapSampler.close();
            if (fxProbe != null) {
                try {
                    fxProbe.close();
                    budget(
                            "javafx-queue-p95",
                            "50k mixed workload",
                            "ms",
                            fxProbe.p95Millis(),
                            LargeDataValidationPolicy.FX_QUEUE_P95_MAX_MILLIS
                    );
                    budget(
                            "javafx-max-stall",
                            "50k mixed workload",
                            "ms",
                            fxProbe.maxMillis(),
                            LargeDataValidationPolicy.FX_QUEUE_MAX_STALL_MILLIS
                    );
                    minimum(
                            "javafx-probe-samples",
                            "50k mixed workload",
                            fxProbe.sampleCount(),
                            20L
                    );
                } catch (Throwable probeFailure) {
                    failures.add("JavaFX responsiveness probe failed: "
                            + safeMessage(probeFailure));
                }
            }

            budget(
                    "peak-used-heap",
                    "complete matrix",
                    "MiB",
                    bytesToMib(heapSampler.peakUsedBytes()),
                    LargeDataValidationPolicy.MAX_USED_HEAP_MIB
            );

            try {
                writeEvidence(startedAt, Instant.now());
            } finally {
                FxRuntime.stop();
                deleteTreeQuietly(fixtureRoot);
            }
        }

        if (!failures.isEmpty()) {
            IllegalStateException gateFailure = new IllegalStateException(
                    "M7.3 large-data gate failed: " + String.join(" | ", failures)
            );
            if (unexpectedFailure != null) gateFailure.addSuppressed(unexpectedFailure);
            throw gateFailure;
        }

        System.out.println(
                "M7_LARGE_DATA_VALIDATION_OK: reports=" + reportDirectory
        );
    }

    private void validateFullMatrix(Path databasePath) throws Exception {
        String jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        try (ClipEntryDao dao = new ClipEntryDao(jdbcUrl);
             TagDao tagDao = new TagDao(jdbcUrl)) {

            exact("full-row-count", "50k", dao.countAll(), 50_000L);

            List<ClipEntry> pinned = dao.listLatest(
                    LargeDataValidationPolicy.MANY_PINNED_COUNT,
                    true
            );
            exact(
                    "many-pinned-count",
                    "50k",
                    pinned.size(),
                    LargeDataValidationPolicy.MANY_PINNED_COUNT
            );
            assertDensePinOrder(pinned);

            List<ClipTag> allTags = tagDao.listAll();
            exact(
                    "many-tags-count",
                    "50k",
                    allTags.size(),
                    LargeDataValidationPolicy.MANY_TAGS_COUNT
            );

            validatePopupPipeline(dao, tagDao);
            validateSearchLatency(dao, tagDao);
            validateDuplicateCandidates(dao);
        }
    }

    private void validatePopupPipeline(
            ClipEntryDao dao,
            TagDao tagDao
    ) throws Exception {
        for (int warmup = 0; warmup < 3; warmup++) {
            loadPopup(dao, tagDao, new PopupReloadCache(
                    PopupPerformancePolicy.TAG_ASSIGNMENT_CACHE_CAPACITY
            ), "", null);
        }

        List<Double> samples = new ArrayList<>();
        PopupSnapshot last = null;
        for (int sample = 0; sample < LargeDataValidationPolicy.MEASUREMENT_SAMPLES; sample++) {
            PopupReloadCache coldCache = new PopupReloadCache(
                    PopupPerformancePolicy.TAG_ASSIGNMENT_CACHE_CAPACITY
            );
            long started = System.nanoTime();
            last = loadPopup(dao, tagDao, coldCache, "", null);
            samples.add(elapsedMillis(started));
        }

        Latency latency = Latency.from(samples);
        budget(
                "popup-pipeline-p95",
                "50k cold cache",
                "ms",
                latency.p95Millis(),
                LargeDataValidationPolicy.POPUP_PIPELINE_P95_MAX_MILLIS
        );
        Objects.requireNonNull(last, "popup snapshot");

        Latency fxMaterialization = FxRuntime.measureInitialMaterialization(
                last.rows(),
                LargeDataValidationPolicy.MEASUREMENT_SAMPLES
        );
        budget(
                "popup-fx-materialization-p95",
                "200 prepared rows",
                "ms",
                fxMaterialization.p95Millis(),
                LargeDataValidationPolicy.POPUP_FX_MATERIALIZATION_P95_MAX_MILLIS
        );
        budget(
                "popup-open-composite-p95",
                "cold data + JavaFX materialization",
                "ms",
                latency.p95Millis() + fxMaterialization.p95Millis(),
                LargeDataValidationPolicy.POPUP_OPEN_COMPOSITE_P95_MAX_MILLIS
        );
        exact(
                "popup-visible-clips",
                "50k",
                last.entries().size(),
                LargeDataValidationPolicy.POPUP_RESULT_LIMIT
        );
        exact(
                "popup-row-clip-count",
                "50k",
                PopupRows.countClips(last.rows()),
                LargeDataValidationPolicy.POPUP_RESULT_LIMIT
        );
        exact("popup-total-clips", "50k", last.totalClipCount(), 50_000L);
        exact(
                "popup-tag-library",
                "50k",
                last.availableTags().size(),
                LargeDataValidationPolicy.MANY_TAGS_COUNT
        );

        List<Long> expectedOrder = clipIds(last.rows());
        List<Double> rowSamples = new ArrayList<>();
        for (int sample = 0; sample < 100; sample++) {
            long started = System.nanoTime();
            List<PopupRow> rows = PopupRows.build(last.entries(), last.tagsByClipId());
            rowSamples.add(elapsedMillis(started));
            if (!clipIds(rows).equals(expectedOrder)) {
                failures.add("Popup row ordering changed during repeated row builds");
                break;
            }
        }
        budget(
                "row-build-p95",
                "200 visible clips",
                "ms",
                Latency.from(rowSamples).p95Millis(),
                LargeDataValidationPolicy.ROW_BUILD_P95_MAX_MILLIS
        );

        double scrollMillis = FxRuntime.validateListScroll(last.rows());
        budget(
                "virtualized-scroll-sequence",
                "popup rows",
                "ms",
                scrollMillis,
                2_000L
        );
    }

    private void validateSearchLatency(
            ClipEntryDao dao,
            TagDao tagDao
    ) {
        PopupReloadCache cache = new PopupReloadCache(
                PopupPerformancePolicy.TAG_ASSIGNMENT_CACHE_CAPACITY
        );
        loadPopup(dao, tagDao, cache, "", null);

        PopupSnapshot uniqueResult = loadPopup(
                dao,
                tagDao,
                cache,
                "needle-" + UNIQUE_SEARCH_INDEX,
                null
        );
        exact(
                "unique-search-result-count",
                "needle-" + UNIQUE_SEARCH_INDEX,
                uniqueResult.entries().size(),
                1L
        );
        if (uniqueResult.entries().isEmpty()
                || !uniqueResult.entries().get(0).content()
                .contains("needle-" + UNIQUE_SEARCH_INDEX)) {
            failures.add("Unique-token search did not return the expected clip");
        }

        Latency textSearch = measurePopupQuery(
                dao,
                tagDao,
                cache,
                "needle-" + UNIQUE_SEARCH_INDEX,
                null
        );
        budget(
                "search-p95",
                "unique content token",
                "ms",
                textSearch.p95Millis(),
                LargeDataValidationPolicy.SEARCH_P95_MAX_MILLIS
        );

        PopupSnapshot tagResult = loadPopup(
                dao,
                tagDao,
                cache,
                "tag:tag-017",
                null
        );
        minimum(
                "tag-search-result-count",
                "tag:tag-017",
                tagResult.entries().size(),
                1L
        );
        for (ClipEntry entry : tagResult.entries()) {
            boolean assigned = tagResult.tagsByClipId()
                    .getOrDefault(entry.id(), List.of())
                    .stream()
                    .anyMatch(tag -> "tag-017".equals(tag.name()));
            if (!assigned) {
                failures.add("Tag search returned a clip without tag-017: " + entry.id());
                break;
            }
        }

        Latency tagSearch = measurePopupQuery(
                dao,
                tagDao,
                cache,
                "tag:tag-017",
                null
        );
        budget(
                "tag-search-p95",
                "256-tag library",
                "ms",
                tagSearch.p95Millis(),
                LargeDataValidationPolicy.TAG_SEARCH_P95_MAX_MILLIS
        );

        PopupSnapshot typeResult = loadPopup(
                dao,
                tagDao,
                cache,
                "",
                ClipContentType.URL
        );
        minimum(
                "type-filter-result-count",
                "URL",
                typeResult.entries().size(),
                1L
        );
        for (ClipEntry entry : typeResult.entries()) {
            if (ClipContentClassifier.classify(entry.content()) != ClipContentType.URL) {
                failures.add("URL filter returned non-URL clip: " + entry.id());
                break;
            }
        }

        Latency typeSearch = measurePopupQuery(
                dao,
                tagDao,
                cache,
                "",
                ClipContentType.URL
        );
        budget(
                "type-filter-p95",
                "bounded derived scan",
                "ms",
                typeSearch.p95Millis(),
                LargeDataValidationPolicy.TYPE_FILTER_P95_MAX_MILLIS
        );

        String[] churnQueries = {
                "needle-42000",
                "tag:tag-017",
                "type:url",
                "is:pinned",
                "-type:text tag:tag-031",
                "needle-30001",
                "type:json tag:tag-007",
                ""
        };
        long churnStarted = System.nanoTime();
        for (int index = 0;
             index < LargeDataValidationPolicy.RAPID_SEARCH_CHURN_ITERATIONS;
             index++) {
            String query = churnQueries[index % churnQueries.length];
            loadPopup(dao, tagDao, cache, query, null);
        }
        double churnMillis = elapsedMillis(churnStarted);
        budget(
                "rapid-search-churn",
                LargeDataValidationPolicy.RAPID_SEARCH_CHURN_ITERATIONS + " requests",
                "ms",
                churnMillis,
                LargeDataValidationPolicy.SEARCH_CHURN_TOTAL_MAX_MILLIS
        );
    }

    private Latency measurePopupQuery(
            ClipEntryDao dao,
            TagDao tagDao,
            PopupReloadCache cache,
            String query,
            ClipContentType toolbarType
    ) {
        for (int warmup = 0; warmup < 3; warmup++) {
            loadPopup(dao, tagDao, cache, query, toolbarType);
        }

        List<Double> samples = new ArrayList<>();
        int maximumResultCount = 0;
        for (int sample = 0; sample < LargeDataValidationPolicy.MEASUREMENT_SAMPLES; sample++) {
            long started = System.nanoTime();
            PopupSnapshot result = loadPopup(dao, tagDao, cache, query, toolbarType);
            samples.add(elapsedMillis(started));
            maximumResultCount = Math.max(
                    maximumResultCount,
                    result.entries().size()
            );
        }
        maximum(
                "query-result-bound",
                query.isBlank() ? "toolbar type filter" : query,
                maximumResultCount,
                LargeDataValidationPolicy.POPUP_RESULT_LIMIT
        );
        return Latency.from(samples);
    }

    private PopupSnapshot loadPopup(
            ClipEntryDao dao,
            TagDao tagDao,
            PopupReloadCache cache,
            String rawQuery,
            ClipContentType toolbarType
    ) {
        SearchExecutionPlan plan = SearchExecutionPlan.combine(
                SearchQueryParser.parse(rawQuery),
                ClipViewScope.ALL,
                toolbarType,
                null
        );
        int candidateLimit = PopupPerformancePolicy.candidateLimit(
                LargeDataValidationPolicy.POPUP_RESULT_LIMIT,
                plan.derivedTypeFilteringActive()
        );

        int totalClipCount = cache.totalClipCount(dao::countAll);
        List<ClipEntry> candidates = plan.unsatisfiable()
                ? List.of()
                : dao.queryLatest(
                        plan.text(),
                        candidateLimit,
                        plan.scope().favoriteFilter(),
                        plan.toolbarTagId(),
                        plan.requiredTagIdentities(),
                        plan.excludedTagIdentities()
                );
        List<ClipEntry> entries = SearchQueryExecutor.apply(
                candidates,
                plan,
                LargeDataValidationPolicy.POPUP_RESULT_LIMIT,
                entry -> ClipContentClassifier.classify(entry.content())
        );

        List<Long> ids = entries.stream().map(ClipEntry::id).toList();
        Map<Long, List<ClipTag>> tagsByClip = cache.tagAssignments(
                ids,
                tagDao::listForClips
        );
        List<ClipTag> availableTags = cache.availableTags(tagDao::listAll);
        List<PopupRow> rows = PopupRows.build(entries, tagsByClip);

        return new PopupSnapshot(
                totalClipCount,
                entries,
                tagsByClip,
                availableTags,
                rows
        );
    }

    private void validateDuplicateCandidates(ClipEntryDao dao) {
        DuplicateContentKeys keys = DuplicateContentKeys.from(DUPLICATE_CONTENT);
        for (int warmup = 0; warmup < 3; warmup++) {
            dao.findDuplicateCandidates(
                    DuplicateContentKeys.KeyKind.NORMALIZED,
                    keys.normalizedHash(),
                    0L
            );
        }

        List<Double> samples = new ArrayList<>();
        int candidateCount = 0;
        for (int sample = 0; sample < LargeDataValidationPolicy.MEASUREMENT_SAMPLES; sample++) {
            long started = System.nanoTime();
            List<ClipEntryDao.DuplicateCandidate> candidates =
                    dao.findDuplicateCandidates(
                            DuplicateContentKeys.KeyKind.NORMALIZED,
                            keys.normalizedHash(),
                            0L
                    );
            samples.add(elapsedMillis(started));
            candidateCount = candidates.size();
        }

        exact(
                "duplicate-candidate-count",
                "50k",
                candidateCount,
                LargeDataValidationPolicy.DUPLICATE_CANDIDATE_COUNT
        );
        budget(
                "duplicate-lookup-p95",
                LargeDataValidationPolicy.DUPLICATE_CANDIDATE_COUNT + " candidates",
                "ms",
                Latency.from(samples).p95Millis(),
                LargeDataValidationPolicy.DUPLICATE_LOOKUP_P95_MAX_MILLIS
        );
    }

    private void validateRetentionCleanup(
            Path sourceDatabase,
            Path fixtureRoot
    ) throws Exception {
        Path cleanupDatabase = fixtureRoot.resolve("xclip-retention.db");
        checkpointDatabase(sourceDatabase);
        Files.copy(
                sourceDatabase,
                cleanupDatabase,
                StandardCopyOption.REPLACE_EXISTING
        );

        String jdbcUrl = "jdbc:sqlite:" + cleanupDatabase.toAbsolutePath();
        try (ClipEntryDao dao = new ClipEntryDao(jdbcUrl);
             HistoryCleanupService service = new HistoryCleanupService(dao)) {
            service.applyPolicy(new HistoryRetentionPolicy(
                    true,
                    30,
                    Map.of(),
                    false
            ));

            long started = System.nanoTime();
            HistoryCleanupService.CleanupStatus result = service.runCleanupNow(
                    HistoryCleanupService.CleanupTrigger.MANUAL
            );
            double cleanupMillis = elapsedMillis(started);

            if (result.outcome() != HistoryCleanupService.CleanupOutcome.SUCCESS) {
                failures.add("Retention cleanup outcome was " + result.outcome()
                        + ": " + result.detail());
            }
            exact(
                    "retention-deleted-count",
                    "50k copy",
                    result.deletedCount(),
                    LargeDataValidationPolicy.RETENTION_ELIGIBLE_COUNT
            );
            exact(
                    "retention-remaining-count",
                    "50k copy",
                    dao.countAll(),
                    50_000L - LargeDataValidationPolicy.RETENTION_ELIGIBLE_COUNT
            );
            budget(
                    "retention-cleanup",
                    LargeDataValidationPolicy.RETENTION_ELIGIBLE_COUNT + " deletions",
                    "ms",
                    cleanupMillis,
                    LargeDataValidationPolicy.RETENTION_CLEANUP_MAX_MILLIS
            );
        }
    }

    private void checkpointDatabase(Path databasePath) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath()
        )) {
            SqliteConnectionConfig.configureWorkingConnection(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE);");
            }
        }
    }

    private void validateLargeClipPolicy() {
        String content = "x".repeat(
                LargeDataValidationPolicy.LARGE_CLIP_CHARACTERS
        );
        long started = System.nanoTime();
        PopupPerformancePolicy.ContentFingerprint fingerprint =
                PopupPerformancePolicy.fingerprint(content);
        ClipContentType type = ClipContentClassifier.classify(content);
        String preview = ClipPreviewPolicy.expandedPreview(content);
        double millis = elapsedMillis(started);

        exact(
                "large-clip-fingerprint-length",
                "500k clip",
                fingerprint.length(),
                LargeDataValidationPolicy.LARGE_CLIP_CHARACTERS
        );
        maximum(
                "large-clip-preview-bound",
                "500k clip",
                preview.length(),
                ClipPreviewPolicy.MAX_EXPANDED_CHARS + 1L
        );
        if (type != ClipContentType.TEXT) {
            failures.add("500,000-character plain clip classified as " + type);
        }
        budget(
                "large-clip-policy",
                "fingerprint + classify + preview",
                "ms",
                millis,
                LargeDataValidationPolicy.LARGE_CLIP_POLICY_MAX_MILLIS
        );
    }

    private Latency measureStartup(Path databasePath) {
        for (int warmup = 0; warmup < 2; warmup++) {
            Database database = new Database(databasePath);
            database.init();
            database.close();
        }

        List<Double> samples = new ArrayList<>();
        for (int sample = 0; sample < LargeDataValidationPolicy.MEASUREMENT_SAMPLES; sample++) {
            long started = System.nanoTime();
            Database database = new Database(databasePath);
            database.init();
            database.close();
            samples.add(elapsedMillis(started));
        }
        return Latency.from(samples);
    }

    private void assertDensePinOrder(List<ClipEntry> pinned) {
        for (int index = 0; index < pinned.size(); index++) {
            Integer pinOrder = pinned.get(index).pinOrder();
            if (pinOrder == null || pinOrder != index) {
                failures.add(
                        "Pinned order is not dense at index " + index + ": " + pinOrder
                );
                return;
            }
        }
    }

    private List<Long> clipIds(List<PopupRow> rows) {
        List<Long> ids = new ArrayList<>();
        for (PopupRow row : rows) {
            if (row instanceof PopupRow.ClipRow clipRow) {
                ids.add(clipRow.entry().id());
            }
        }
        return List.copyOf(ids);
    }

    private void requireBoundedHeap() {
        long configuredMaxMib = Runtime.getRuntime().maxMemory() / MIB;
        if (configuredMaxMib > LargeDataValidationPolicy.MAX_HEAP_MIB + 32L) {
            failures.add(
                    "Large-data JVM must be bounded near "
                            + LargeDataValidationPolicy.MAX_HEAP_MIB
                            + " MiB, actual max is " + configuredMaxMib + " MiB"
            );
        }
    }

    private void budget(
            String metric,
            String scenario,
            String unit,
            double value,
            double maximum
    ) {
        boolean passed = Double.isFinite(value) && value <= maximum;
        metrics.add(new Metric(
                metric,
                scenario,
                unit,
                value,
                maximum,
                "<=",
                passed
        ));
        if (!passed) {
            failures.add(metric + " for " + scenario + " was "
                    + format(value) + " " + unit + ", budget "
                    + format(maximum) + " " + unit);
        }
    }

    private void exact(
            String metric,
            String scenario,
            long value,
            long expected
    ) {
        boolean passed = value == expected;
        metrics.add(new Metric(
                metric,
                scenario,
                "count",
                value,
                expected,
                "==",
                passed
        ));
        if (!passed) {
            failures.add(metric + " for " + scenario + " was "
                    + value + ", expected " + expected);
        }
    }

    private void maximum(
            String metric,
            String scenario,
            long value,
            long maximum
    ) {
        budget(metric, scenario, "count", value, maximum);
    }

    private void minimum(
            String metric,
            String scenario,
            long value,
            long minimum
    ) {
        boolean passed = value >= minimum;
        metrics.add(new Metric(
                metric,
                scenario,
                "count",
                value,
                minimum,
                ">=",
                passed
        ));
        if (!passed) {
            failures.add(metric + " for " + scenario + " was "
                    + value + ", minimum " + minimum);
        }
    }

    private void writeEvidence(
            Instant startedAt,
            Instant completedAt
    ) throws IOException {
        Files.createDirectories(reportDirectory);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("milestone", "M7.3");
        summary.put("status", failures.isEmpty() ? "PASS" : "FAIL");
        summary.put("startedAt", startedAt.toString());
        summary.put("completedAt", completedAt.toString());
        summary.put("durationMillis", completedAt.toEpochMilli() - startedAt.toEpochMilli());
        summary.put("environment", environment());
        summary.put("datasets", datasets);
        summary.put("metrics", metrics);
        summary.put("failures", failures);

        try (BufferedWriter writer = Files.newBufferedWriter(
                reportDirectory.resolve("summary.json"),
                StandardCharsets.UTF_8
        )) {
            GSON.toJson(summary, writer);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
                reportDirectory.resolve("metrics.csv"),
                StandardCharsets.UTF_8
        )) {
            writer.write("metric,scenario,unit,value,budget,comparison,status");
            writer.newLine();
            for (Metric metric : metrics) {
                writer.write(csv(metric.metric()));
                writer.write(',');
                writer.write(csv(metric.scenario()));
                writer.write(',');
                writer.write(csv(metric.unit()));
                writer.write(',');
                writer.write(format(metric.value()));
                writer.write(',');
                writer.write(format(metric.budget()));
                writer.write(',');
                writer.write(csv(metric.comparison()));
                writer.write(',');
                writer.write(metric.passed() ? "PASS" : "FAIL");
                writer.newLine();
            }
        }

        Properties environment = new Properties();
        for (Map.Entry<String, String> entry : environment().entrySet()) {
            environment.setProperty(entry.getKey(), entry.getValue());
        }
        try (var output = Files.newOutputStream(
                reportDirectory.resolve("environment.properties")
        )) {
            environment.store(output, "XClip M7.3 validation environment");
        }

        Path marker = reportDirectory.resolve(failures.isEmpty() ? "PASS.txt" : "FAIL.txt");
        Files.writeString(
                marker,
                failures.isEmpty()
                        ? "M7.3 large-data validation passed.\n"
                        : String.join(System.lineSeparator(), failures)
                                + System.lineSeparator(),
                StandardCharsets.UTF_8
        );
    }

    private Map<String, String> environment() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("os.name", System.getProperty("os.name", "unknown"));
        values.put("os.version", System.getProperty("os.version", "unknown"));
        values.put("os.arch", System.getProperty("os.arch", "unknown"));
        values.put("java.version", System.getProperty("java.version", "unknown"));
        values.put("java.vendor", System.getProperty("java.vendor", "unknown"));
        values.put("availableProcessors", String.valueOf(runtime.availableProcessors()));
        values.put("maxHeapBytes", String.valueOf(runtime.maxMemory()));
        values.put("maxHeapMiB", String.valueOf(runtime.maxMemory() / MIB));
        return values;
    }

    private void clearPreviousEvidence() throws IOException {
        if (!Files.exists(reportDirectory)) return;
        try (var stream = Files.list(reportDirectory)) {
            for (Path child : stream.toList()) {
                deleteTreeQuietly(child);
            }
        }
    }

    private static String csv(String value) {
        String safe = Objects.requireNonNullElse(value, "");
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static double elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000.0;
    }

    private static double bytesToMib(long bytes) {
        return bytes / (double) MIB;
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "unknown error";
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }

    private static void deleteTreeQuietly(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private record PopupSnapshot(
            int totalClipCount,
            List<ClipEntry> entries,
            Map<Long, List<ClipTag>> tagsByClipId,
            List<ClipTag> availableTags,
            List<PopupRow> rows
    ) {}

    private record Metric(
            String metric,
            String scenario,
            String unit,
            double value,
            double budget,
            String comparison,
            boolean passed
    ) {}

    private record DatasetEvidence(
            int clipCount,
            long databaseBytes,
            double fixtureBuildMillis,
            double startupMedianMillis,
            double startupP95Millis
    ) {}

    private record FixtureSummary(
            int clipCount,
            long databaseBytes
    ) {}

    private record Latency(
            double medianMillis,
            double p95Millis,
            double maxMillis
    ) {
        private static Latency from(List<Double> rawSamples) {
            if (rawSamples == null || rawSamples.isEmpty()) {
                return new Latency(Double.NaN, Double.NaN, Double.NaN);
            }
            List<Double> samples = rawSamples.stream()
                    .sorted()
                    .toList();
            return new Latency(
                    percentile(samples, 0.50),
                    percentile(samples, 0.95),
                    samples.get(samples.size() - 1)
            );
        }

        private static double percentile(List<Double> samples, double fraction) {
            int index = (int) Math.ceil(samples.size() * fraction) - 1;
            index = Math.max(0, Math.min(samples.size() - 1, index));
            return samples.get(index);
        }
    }

    private static final class LargeDataFixtureBuilder {

        private static final int BATCH_SIZE = 500;
        private static final long DAY_MILLIS = HistoryRetentionPolicy.MILLIS_PER_DAY;

        private LargeDataFixtureBuilder() {}

        private static FixtureSummary build(
                Path databasePath,
                int clipCount,
                boolean fullMatrix
        ) throws Exception {
            Files.deleteIfExists(databasePath);
            Database database = new Database(databasePath);
            database.init();
            database.close();

            String jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
            try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
                SqliteConnectionConfig.configureWorkingConnection(connection);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA synchronous=OFF;");
                    statement.execute("PRAGMA temp_store=MEMORY;");
                }
                connection.setAutoCommit(false);
                try {
                    insertClips(connection, clipCount, fullMatrix);
                    if (fullMatrix) {
                        insertTags(connection, clipCount);
                    }
                    connection.commit();
                } catch (Throwable failure) {
                    connection.rollback();
                    throw failure;
                } finally {
                    connection.setAutoCommit(true);
                }

                try (Statement statement = connection.createStatement()) {
                    statement.execute("ANALYZE;");
                    statement.execute("PRAGMA wal_checkpoint(TRUNCATE);");
                }
            }

            int actualCount;
            try (Connection connection = DriverManager.getConnection(jdbcUrl);
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT COUNT(*) FROM clip_entries"
                 )) {
                actualCount = result.next() ? result.getInt(1) : 0;
            }
            return new FixtureSummary(actualCount, Files.size(databasePath));
        }

        private static void insertClips(
                Connection connection,
                int clipCount,
                boolean fullMatrix
        ) throws Exception {
            String sql = """
                    INSERT INTO clip_entries(
                        content,
                        content_norm,
                        content_hash,
                        content_exact_hash,
                        content_exact_ci_hash,
                        content_norm_ci_hash,
                        title,
                        is_favorite,
                        pin_order,
                        created_at,
                        last_copied_at,
                        use_count
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;

            long now = System.currentTimeMillis();
            DuplicateContentKeys duplicateKeys = DuplicateContentKeys.from(
                    DUPLICATE_CONTENT
            );
            String largeContent = fullMatrix
                    ? "x".repeat(LargeDataValidationPolicy.LARGE_CLIP_CHARACTERS)
                    : null;

            try (PreparedStatement insert = connection.prepareStatement(sql)) {
                for (int index = 0; index < clipCount; index++) {
                    boolean duplicate = fullMatrix
                            && index >= clipCount
                            - LargeDataValidationPolicy.DUPLICATE_CANDIDATE_COUNT;
                    boolean large = fullMatrix && index == 30_000;
                    String content = duplicate
                            ? DUPLICATE_CONTENT
                            : large
                            ? largeContent
                            : contentFor(index);
                    String normalized = duplicate
                            ? DUPLICATE_CONTENT
                            : DuplicateBehaviorPolicy.normalizeWhitespace(content);
                    DuplicateContentKeys keys = duplicate
                            ? duplicateKeys
                            : DuplicateContentKeys.from(content, normalized);

                    boolean pinned = fullMatrix
                            && index < LargeDataValidationPolicy.MANY_PINNED_COUNT;
                    boolean oldForRetention = fullMatrix
                            && index >= LargeDataValidationPolicy.MANY_PINNED_COUNT
                            && index < LargeDataValidationPolicy.MANY_PINNED_COUNT
                            + LargeDataValidationPolicy.RETENTION_ELIGIBLE_COUNT;
                    long copiedAt = oldForRetention
                            ? now - 60L * DAY_MILLIS - index
                            : now - index * 1_000L;

                    insert.setString(1, content);
                    insert.setString(2, normalized);
                    insert.setString(3, keys.normalizedHash());
                    insert.setString(4, keys.exactHash());
                    insert.setString(5, keys.exactCaseInsensitiveHash());
                    insert.setString(6, keys.normalizedCaseInsensitiveHash());
                    if (pinned) {
                        insert.setString(7, "Pinned " + String.format(
                                Locale.ROOT,
                                "%04d",
                                index
                        ));
                    } else {
                        insert.setNull(7, java.sql.Types.VARCHAR);
                    }
                    insert.setInt(8, pinned ? 1 : 0);
                    if (pinned) insert.setInt(9, index);
                    else insert.setNull(9, java.sql.Types.INTEGER);
                    insert.setLong(10, copiedAt);
                    insert.setLong(11, copiedAt);
                    insert.setInt(12, duplicate ? 2 : 1);
                    insert.addBatch();

                    if ((index + 1) % BATCH_SIZE == 0) {
                        insert.executeBatch();
                    }
                }
                if (clipCount % BATCH_SIZE != 0) insert.executeBatch();
            }
        }

        private static void insertTags(
                Connection connection,
                int clipCount
        ) throws Exception {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO tags(name, name_norm, created_at)
                    VALUES (?, ?, ?)
                    """)) {
                long createdAt = System.currentTimeMillis();
                for (int index = 0;
                     index < LargeDataValidationPolicy.MANY_TAGS_COUNT;
                     index++) {
                    String name = String.format(Locale.ROOT, "tag-%03d", index);
                    insert.setString(1, name);
                    insert.setString(2, name);
                    insert.setLong(3, createdAt + index);
                    insert.addBatch();
                }
                insert.executeBatch();
            }

            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT OR IGNORE INTO clip_tags(clip_id, tag_id, assigned_at)
                    VALUES (?, ?, ?)
                    """)) {
                long assignedAt = System.currentTimeMillis();
                int pending = 0;
                for (int clipId = 1; clipId <= clipCount; clipId++) {
                    boolean denseTaggedRegion = clipId <= 10_000;
                    boolean sparseTaggedRegion = clipId > 10_000 && clipId % 10 == 0;
                    if (!denseTaggedRegion && !sparseTaggedRegion) continue;

                    int first = (clipId % LargeDataValidationPolicy.MANY_TAGS_COUNT) + 1;
                    addAssignment(insert, clipId, first, assignedAt);
                    pending++;

                    if (denseTaggedRegion) {
                        int second = ((clipId * 7)
                                % LargeDataValidationPolicy.MANY_TAGS_COUNT) + 1;
                        addAssignment(insert, clipId, second, assignedAt + 1L);
                        pending++;
                    }

                    if (pending >= BATCH_SIZE) {
                        insert.executeBatch();
                        pending = 0;
                    }
                }
                if (pending > 0) insert.executeBatch();
            }
        }

        private static void addAssignment(
                PreparedStatement insert,
                long clipId,
                long tagId,
                long assignedAt
        ) throws Exception {
            insert.setLong(1, clipId);
            insert.setLong(2, tagId);
            insert.setLong(3, assignedAt);
            insert.addBatch();
        }

        private static String contentFor(int index) {
            String token = "needle-" + index;
            return switch (index % 6) {
                case 0 -> "Plain clipboard note " + token + " with deterministic text";
                case 1 -> "https://example.com/items/" + index + "?q=" + token;
                case 2 -> "C:\\XClip\\fixtures\\" + token + ".txt";
                case 3 -> "{\"id\":" + index + ",\"token\":\"" + token + "\"}";
                case 4 -> "git show " + token;
                default -> "public int value" + index + "() { return " + index
                        + "; } // " + token;
            };
        }
    }

    private static final class HeapSampler implements AutoCloseable {

        private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        private final ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "xclip-m7-heap-sampler");
                    thread.setDaemon(true);
                    return thread;
                });
        private final AtomicLong peakUsed = new AtomicLong();

        private void start() {
            sample();
            executor.scheduleAtFixedRate(this::sample, 0L, 20L, TimeUnit.MILLISECONDS);
        }

        private void sample() {
            long used = memory.getHeapMemoryUsage().getUsed();
            peakUsed.accumulateAndGet(used, Math::max);
        }

        private long peakUsedBytes() {
            sample();
            return peakUsed.get();
        }

        @Override
        public void close() {
            executor.shutdownNow();
            try {
                executor.awaitTermination(2L, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            sample();
        }
    }

    private static final class FxQueueProbe implements AutoCloseable {

        private final ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "xclip-m7-fx-probe");
                    thread.setDaemon(true);
                    return thread;
                });
        private final AtomicBoolean pending = new AtomicBoolean(false);
        private final List<Double> delays = java.util.Collections.synchronizedList(
                new ArrayList<>()
        );

        private void start() {
            scheduler.scheduleAtFixedRate(this::postProbe, 0L, 25L, TimeUnit.MILLISECONDS);
        }

        private void postProbe() {
            if (!pending.compareAndSet(false, true)) return;
            long submitted = System.nanoTime();
            Platform.runLater(() -> {
                delays.add((System.nanoTime() - submitted) / 1_000_000.0);
                pending.set(false);
            });
        }

        private int sampleCount() {
            synchronized (delays) {
                return delays.size();
            }
        }

        private double p95Millis() {
            synchronized (delays) {
                return Latency.from(List.copyOf(delays)).p95Millis();
            }
        }

        private double maxMillis() {
            synchronized (delays) {
                return Latency.from(List.copyOf(delays)).maxMillis();
            }
        }

        @Override
        public void close() throws Exception {
            scheduler.shutdownNow();
            scheduler.awaitTermination(2L, TimeUnit.SECONDS);
            FxRuntime.runAndWait(() -> null);
        }
    }

    private static final class FxRuntime {

        private static final AtomicBoolean STARTED = new AtomicBoolean(false);

        private FxRuntime() {}

        private static void start() throws Exception {
            if (!STARTED.compareAndSet(false, true)) return;
            CountDownLatch ready = new CountDownLatch(1);
            Platform.startup(ready::countDown);
            if (!ready.await(15L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("JavaFX toolkit did not start");
            }
            Platform.setImplicitExit(false);
        }

        private static <T> T runAndWait(Callable<T> action) throws Exception {
            if (Platform.isFxApplicationThread()) return action.call();
            FutureTask<T> task = new FutureTask<>(action);
            Platform.runLater(task);
            return task.get(15L, TimeUnit.SECONDS);
        }

        private static Latency measureInitialMaterialization(
                List<PopupRow> rows,
                int sampleCount
        ) throws Exception {
            for (int warmup = 0; warmup < 3; warmup++) {
                materialize(rows);
            }
            List<Double> samples = new ArrayList<>();
            for (int sample = 0; sample < sampleCount; sample++) {
                samples.add(materialize(rows));
            }
            return Latency.from(samples);
        }

        private static double materialize(List<PopupRow> rows) throws Exception {
            return runAndWait(() -> {
                long started = System.nanoTime();
                ListView<PopupRow> list = new ListView<>(
                        FXCollections.observableArrayList(rows)
                );
                StackPane root = new StackPane(list);
                Scene scene = new Scene(root, 520.0, 420.0);
                root.applyCss();
                root.layout();
                if (scene.getRoot() != root || list.getItems().size() != rows.size()) {
                    throw new IllegalStateException("JavaFX popup row materialization is inconsistent");
                }
                return elapsedMillis(started);
            });
        }

        private static double validateListScroll(List<PopupRow> rows) throws Exception {
            return runAndWait(() -> {
                ListView<PopupRow> list = new ListView<>(
                        FXCollections.observableArrayList(rows)
                );
                StackPane root = new StackPane(list);
                Scene scene = new Scene(root, 520.0, 420.0);
                root.applyCss();
                root.layout();

                long started = System.nanoTime();
                int size = Math.max(1, rows.size());
                for (int index = 0; index < 400; index++) {
                    int target = (index * 37) % size;
                    list.scrollTo(target);
                    list.getSelectionModel().select(target);
                    if (index % 20 == 0) root.layout();
                }
                if (scene.getRoot() != root || list.getItems().size() != rows.size()) {
                    throw new IllegalStateException("JavaFX scroll surface lost rows");
                }
                return elapsedMillis(started);
            });
        }

        private static void stop() {
            if (!STARTED.compareAndSet(true, false)) return;
            try {
                Platform.exit();
            } catch (Throwable ignored) {
            }
        }
    }
}
