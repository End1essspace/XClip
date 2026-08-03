
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.service;

import io.xseries.xclip.config.Config;
import io.xseries.xclip.data.dao.ClipEntryDao;
import io.xseries.xclip.data.db.Database;
import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClipServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultPolicyPreservesExistingRuntimeBehavior() throws Exception {
        Fixture fixture = fixture("default.db", DuplicateBehaviorPolicy.defaults());
        try {
            fixture.service.ingestTextAt("alpha", 1_000);
            fixture.service.ingestTextAt("beta", 2_000);
            fixture.service.ingestTextAt("alpha", 3_000);

            List<ClipEntry> rows = fixture.dao.listLatest(10);
            assertEquals(List.of("alpha", "beta"),
                    rows.stream().map(ClipEntry::content).toList());
            assertEquals(2, usageCount(fixture.db.jdbcUrl(), rows.get(0).id()));
        } finally {
            fixture.close();
        }
    }

    @Test
    void finiteWindowCanCreateAnotherEqualEntry() {
        DuplicateBehaviorPolicy policy = policy(
                DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                DuplicateBehaviorPolicy.WhitespaceMode.NORMALIZE,
                DuplicateBehaviorPolicy.CaseSensitivity.SENSITIVE,
                500,
                false
        );
        Fixture fixture = fixture("window.db", policy);
        try {
            fixture.service.ingestTextAt("alpha", 1_000);
            fixture.service.ingestTextAt("separator", 1_100);
            fixture.service.ingestTextAt("alpha", 1_501);

            assertEquals(3, fixture.dao.countAll());
            assertEquals(2, fixture.dao.listLatest(10).stream()
                    .filter(entry -> "alpha".equals(entry.content()))
                    .count());
        } finally {
            fixture.close();
        }
    }

    @Test
    void recentDuplicateCanPreserveItsExistingPosition() {
        DuplicateBehaviorPolicy policy = policy(
                DuplicateBehaviorPolicy.RecentDuplicatePosition.PRESERVE_EXISTING_POSITION,
                DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                DuplicateBehaviorPolicy.WhitespaceMode.NORMALIZE,
                DuplicateBehaviorPolicy.CaseSensitivity.SENSITIVE,
                0,
                false
        );
        Fixture fixture = fixture("preserve-position.db", policy);
        try {
            fixture.service.ingestTextAt("alpha", 1_000);
            fixture.service.ingestTextAt("beta", 2_000);
            fixture.service.ingestTextAt("alpha", 3_000);

            List<ClipEntry> rows = fixture.dao.listLatest(10);
            assertEquals(List.of("beta", "alpha"),
                    rows.stream().map(ClipEntry::content).toList());
            assertEquals(1_000L, rows.get(1).createdAt());
            assertEquals(2, usageCount(fixture.db.jdbcUrl(), rows.get(1).id()));
        } finally {
            fixture.close();
        }
    }

    @Test
    void normalizedCaseInsensitivePolicyUsesPersistedAlternateHash() {
        DuplicateBehaviorPolicy policy = policy(
                DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                DuplicateBehaviorPolicy.WhitespaceMode.NORMALIZE,
                DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE,
                0,
                false
        );
        Fixture fixture = fixture("insensitive.db", policy);
        try {
            fixture.service.ingestTextAt("Alpha  Value", 1_000);
            fixture.service.ingestTextAt("separator", 1_500);
            fixture.service.ingestTextAt("alpha\tvalue", 2_000);

            List<ClipEntry> rows = fixture.dao.listLatest(10);
            assertEquals(2, rows.size());
            assertEquals("alpha\tvalue", rows.get(0).content());
            assertEquals(2, usageCount(fixture.db.jdbcUrl(), rows.get(0).id()));
        } finally {
            fixture.close();
        }
    }

    @Test
    void policyChangeUsesAlreadyPersistedAlternateKeys() {
        Fixture fixture = fixture("policy-switch.db", DuplicateBehaviorPolicy.defaults());
        try {
            fixture.service.ingestTextAt("Alpha  Value", 1_000);
            fixture.service.ingestTextAt("separator", 1_500);

            DuplicateBehaviorPolicy insensitive = policy(
                    DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                    DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                    DuplicateBehaviorPolicy.WhitespaceMode.NORMALIZE,
                    DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE,
                    0,
                    false
            );
            fixture.service.applyConfig(
                    Config.defaults().withDuplicateBehaviorPolicy(insensitive)
            );
            fixture.service.ingestTextAt("alpha value", 2_000);

            assertEquals(2, fixture.dao.countAll());
            assertEquals("alpha value", fixture.dao.listLatest(10).get(0).content());
        } finally {
            fixture.close();
        }
    }

    @Test
    void preserveWhitespaceKeepsCharacterDistinctRows() {
        DuplicateBehaviorPolicy policy = policy(
                DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                DuplicateBehaviorPolicy.WhitespaceMode.PRESERVE,
                DuplicateBehaviorPolicy.CaseSensitivity.SENSITIVE,
                0,
                false
        );
        Fixture fixture = fixture("preserve-whitespace.db", policy);
        try {
            fixture.service.ingestTextAt("alpha", 1_000);
            fixture.service.ingestTextAt("separator", 1_500);
            fixture.service.ingestTextAt(" alpha ", 2_000);

            assertEquals(3, fixture.dao.countAll());
            assertEquals(" alpha ", fixture.dao.listLatest(10).get(0).content());
        } finally {
            fixture.close();
        }
    }

    @Test
    void exactContentModeKeepsCaseDistinct() {
        DuplicateBehaviorPolicy policy = policy(
                DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                DuplicateBehaviorPolicy.WhitespaceMode.NORMALIZE,
                DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE,
                0,
                true
        );
        Fixture fixture = fixture("exact.db", policy);
        try {
            fixture.service.ingestTextAt("Alpha", 1_000);
            fixture.service.ingestTextAt("separator", 1_500);
            fixture.service.ingestTextAt("alpha", 2_000);

            assertEquals(3, fixture.dao.countAll());
        } finally {
            fixture.close();
        }
    }

    @Test
    void pinnedDuplicateCanMoveToTopWithoutLosingMetadata() {
        DuplicateBehaviorPolicy policy = policy(
                DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                DuplicateBehaviorPolicy.PinnedDuplicatePosition.MOVE_PIN_TO_TOP,
                DuplicateBehaviorPolicy.WhitespaceMode.NORMALIZE,
                DuplicateBehaviorPolicy.CaseSensitivity.SENSITIVE,
                0,
                false
        );
        Fixture fixture = fixture("pinned.db", policy);
        try {
            fixture.service.ingestTextAt("first", 1_000);
            fixture.service.ingestTextAt("second", 2_000);
            long firstId = idFor(fixture.dao, "first");
            long secondId = idFor(fixture.dao, "second");

            fixture.dao.setFavorite(firstId, true);
            fixture.dao.setTitle(firstId, "First title");
            fixture.dao.setFavorite(secondId, true);
            assertEquals(List.of("second", "first"), pinnedContents(fixture.dao));

            fixture.service.ingestTextAt("first", 3_000);

            assertEquals(List.of("first", "second"), pinnedContents(fixture.dao));
            assertEquals("First title", fixture.dao.listLatest(10).get(0).title());
        } finally {
            fixture.close();
        }
    }

    private Fixture fixture(String name, DuplicateBehaviorPolicy policy) {
        Database db = new Database(tempDir.resolve(name));
        db.init();
        ClipEntryDao dao = new ClipEntryDao(db.jdbcUrl());
        ClipService service = new ClipService(dao);
        service.applyConfig(Config.defaults().withDuplicateBehaviorPolicy(policy));
        return new Fixture(db, dao, service);
    }

    private DuplicateBehaviorPolicy policy(
            DuplicateBehaviorPolicy.RecentDuplicatePosition recent,
            DuplicateBehaviorPolicy.PinnedDuplicatePosition pinned,
            DuplicateBehaviorPolicy.WhitespaceMode whitespace,
            DuplicateBehaviorPolicy.CaseSensitivity caseSensitivity,
            long window,
            boolean exact
    ) {
        return new DuplicateBehaviorPolicy(
                recent,
                pinned,
                whitespace,
                caseSensitivity,
                window,
                exact
        );
    }

    private int usageCount(String jdbcUrl, long id) {
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT use_count FROM clip_entries WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long idFor(ClipEntryDao dao, String content) {
        return dao.listLatest(100).stream()
                .filter(entry -> content.equals(entry.content()))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private List<String> pinnedContents(ClipEntryDao dao) {
        return dao.listLatest(100, true).stream()
                .map(ClipEntry::content)
                .toList();
    }

    private record Fixture(Database db, ClipEntryDao dao, ClipService service) {
        private void close() {
            dao.closeForCurrentThread();
            db.close();
        }
    }

    @Test
    void selectedOnlySelfCopyHashSuppressesEquivalentNormalizedContent() {
        DuplicateBehaviorPolicy policy = policy(
                DuplicateBehaviorPolicy.RecentDuplicatePosition.MOVE_TO_TOP,
                DuplicateBehaviorPolicy.PinnedDuplicatePosition.PRESERVE_PIN_POSITION,
                DuplicateBehaviorPolicy.WhitespaceMode.NORMALIZE,
                DuplicateBehaviorPolicy.CaseSensitivity.INSENSITIVE,
                0,
                false
        );
        Fixture fixture = fixture("self-copy.db", policy);
        try {
            fixture.service.markPushedByApp(" Alpha\tValue ");
            fixture.service.ingestTextAt("alpha value", System.currentTimeMillis());

            assertEquals(0, fixture.dao.countAll());
        } finally {
            fixture.close();
        }
    }

    @Test
    void loweringHistoryLimitPrunesImmediatelyBeforeDuplicateOnlyWorkloads() {
        Database db = new Database(tempDir.resolve("retention-limit.db"));
        db.init();
        ClipEntryDao dao = new ClipEntryDao(db.jdbcUrl());
        try {
            for (int index = 0; index < 110; index++) {
                String content = "entry-" + index;
                dao.insert(
                        content,
                        content,
                        "hash-" + index,
                        1_000L + index
                );
            }
            assertEquals(110, dao.countAll());

            ClipService service = new ClipService(dao);
            service.applyConfig(Config.defaults().withMaxHistory(100));

            assertEquals(100, dao.countAll());
        } finally {
            dao.closeForCurrentThread();
            db.close();
        }
    }

}
