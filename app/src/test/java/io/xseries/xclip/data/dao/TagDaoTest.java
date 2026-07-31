/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.dao;

import io.xseries.xclip.data.db.Database;
import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.data.model.ClipTag;
import io.xseries.xclip.data.model.TagSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void createsCanonicalTagsAndDeduplicatesByNormalizedName() {
        TestContext ctx = createContext("canonical.db");
        try {
            ClipTag first = ctx.tags.createOrGet("  Work   Items  ");
            ClipTag repeated = ctx.tags.createOrGet("work items");

            assertEquals(first.id(), repeated.id());
            assertEquals("Work Items", first.name());
            assertEquals("Work Items", repeated.name());
            assertEquals(List.of("Work Items"),
                    ctx.tags.listAll().stream().map(ClipTag::name).toList());

            assertThrows(IllegalArgumentException.class,
                    () -> ctx.tags.createOrGet("   \t\n  "));
            assertThrows(IllegalArgumentException.class,
                    () -> ctx.tags.createOrGet("x".repeat(TagDao.MAX_TAG_NAME_LENGTH + 1)));
        } finally {
            ctx.close();
        }
    }

    @Test
    void assignsAndReplacesClipTagsAtomically() {
        TestContext ctx = createContext("assignments.db");
        try {
            long clipId = insertClip(ctx.clips, "alpha", "hash-alpha", 1_000L);
            ClipTag work = ctx.tags.createOrGet("Work");
            ClipTag review = ctx.tags.createOrGet("Review");
            ClipTag later = ctx.tags.createOrGet("Later");

            assertTrue(ctx.tags.addTagToClip(clipId, work.id()));
            assertFalse(ctx.tags.addTagToClip(clipId, work.id()));
            assertEquals(List.of("Work"), tagNames(ctx.tags, clipId));

            ctx.tags.replaceTagsForClip(
                    clipId,
                    List.of(review.id(), later.id(), review.id())
            );
            assertEquals(List.of("Later", "Review"), tagNames(ctx.tags, clipId));

            assertThrows(IllegalArgumentException.class,
                    () -> ctx.tags.replaceTagsForClip(clipId, List.of(work.id(), 999_999L)));

            // The failed replacement must not modify the previous relation set.
            assertEquals(List.of("Later", "Review"), tagNames(ctx.tags, clipId));

            assertTrue(ctx.tags.removeTagFromClip(clipId, review.id()));
            assertFalse(ctx.tags.removeTagFromClip(clipId, review.id()));
            assertEquals(List.of("Later"), tagNames(ctx.tags, clipId));
        } finally {
            ctx.close();
        }
    }

    @Test
    void appliesMultiClipEditAndCreatesTagsInOneTransaction() {
        TestContext ctx = createContext("multi-edit.db");
        try {
            long firstClip = insertClip(ctx.clips, "first", "hash-first", 1_000L);
            long secondClip = insertClip(ctx.clips, "second", "hash-second", 2_000L);

            ClipTag work = ctx.tags.createOrGet("Work");
            ClipTag privateTag = ctx.tags.createOrGet("Private");
            ClipTag later = ctx.tags.createOrGet("Later");

            ctx.tags.addTagToClip(firstClip, work.id());
            ctx.tags.addTagToClip(firstClip, privateTag.id());
            ctx.tags.addTagToClip(secondClip, work.id());

            List<ClipTag> resolved = ctx.tags.applyEdit(
                    List.of(firstClip, secondClip, firstClip),
                    List.of(later.id()),
                    List.of(privateTag.id()),
                    List.of("  Project   Work  ", "project work")
            );

            assertEquals(List.of("Project Work"),
                    resolved.stream().map(ClipTag::name).toList());
            assertEquals(List.of("Later", "Project Work", "Work"),
                    tagNames(ctx.tags, firstClip));
            assertEquals(List.of("Later", "Project Work", "Work"),
                    tagNames(ctx.tags, secondClip));
            assertEquals(List.of("Later", "Private", "Project Work", "Work"),
                    ctx.tags.listAll().stream().map(ClipTag::name).toList());
        } finally {
            ctx.close();
        }
    }

    @Test
    void failedMultiClipEditRollsBackAllChangesAndNewTags() {
        TestContext ctx = createContext("multi-edit-rollback.db");
        try {
            long firstClip = insertClip(ctx.clips, "first", "hash-first", 1_000L);
            long secondClip = insertClip(ctx.clips, "second", "hash-second", 2_000L);
            ClipTag keep = ctx.tags.createOrGet("Keep");
            ctx.tags.addTagToClip(firstClip, keep.id());
            ctx.tags.addTagToClip(secondClip, keep.id());

            assertThrows(IllegalArgumentException.class, () -> ctx.tags.applyEdit(
                    List.of(firstClip, 999_999L, secondClip),
                    List.of(),
                    List.of(keep.id()),
                    List.of("Must Roll Back")
            ));

            assertEquals(List.of("Keep"), tagNames(ctx.tags, firstClip));
            assertEquals(List.of("Keep"), tagNames(ctx.tags, secondClip));
            assertEquals(List.of("Keep"),
                    ctx.tags.listAll().stream().map(ClipTag::name).toList());
        } finally {
            ctx.close();
        }
    }

    @Test
    void renamesTagsAndRejectsCaseInsensitiveCollisions() {
        TestContext ctx = createContext("rename.db");
        try {
            ClipTag work = ctx.tags.createOrGet("Work");
            ClipTag personal = ctx.tags.createOrGet("Personal");

            assertTrue(ctx.tags.renameTag(work.id(), "Project   Work"));
            assertEquals(List.of("Personal", "Project Work"),
                    ctx.tags.listAll().stream().map(ClipTag::name).toList());

            assertThrows(IllegalArgumentException.class,
                    () -> ctx.tags.renameTag(personal.id(), "project work"));

            assertTrue(ctx.tags.renameTag(work.id(), "PROJECT WORK"));
            assertEquals("PROJECT WORK", ctx.tags.listAll().stream()
                    .filter(tag -> tag.id() == work.id())
                    .findFirst()
                    .orElseThrow()
                    .name());
        } finally {
            ctx.close();
        }
    }

    @Test
    void deletionsCascadeAndTagQueryUsesPopupOrder() throws Exception {
        TestContext ctx = createContext("cascade.db");
        try {
            long olderId = insertClip(ctx.clips, "older", "hash-older", 1_000L);
            long newerId = insertClip(ctx.clips, "newer", "hash-newer", 2_000L);
            ctx.clips.setFavorite(olderId, true);

            ClipTag shared = ctx.tags.createOrGet("Shared");
            ClipTag temporary = ctx.tags.createOrGet("Temporary");

            ctx.tags.addTagToClip(olderId, shared.id());
            ctx.tags.addTagToClip(newerId, shared.id());
            ctx.tags.addTagToClip(newerId, temporary.id());

            // Pinned clip remains first even though it is older.
            assertEquals(List.of(olderId, newerId),
                    ctx.tags.listClipIdsForTag(shared.id(), 10));

            assertTrue(ctx.tags.deleteTag(temporary.id()));
            assertEquals(0, relationCount(ctx.db.jdbcUrl(), "tag_id", temporary.id()));

            ctx.clips.deleteById(newerId);
            assertEquals(0, relationCount(ctx.db.jdbcUrl(), "clip_id", newerId));
            assertEquals(List.of(olderId), ctx.tags.listClipIdsForTag(shared.id(), 10));
        } finally {
            ctx.close();
        }
    }

    @Test
    void listsTagsWithDeterministicUsageCountsIncludingUnusedTags() {
        TestContext ctx = createContext("tag-usage.db");
        try {
            long firstClip = insertClip(ctx.clips, "first", "hash-first", 1_000L);
            long secondClip = insertClip(ctx.clips, "second", "hash-second", 2_000L);

            ClipTag unused = ctx.tags.createOrGet("Unused");
            ClipTag shared = ctx.tags.createOrGet("Shared");
            ClipTag single = ctx.tags.createOrGet("Single");

            ctx.tags.addTagToClip(firstClip, shared.id());
            ctx.tags.addTagToClip(secondClip, shared.id());
            ctx.tags.addTagToClip(secondClip, single.id());

            List<TagSummary> summaries = ctx.tags.listAllWithUsage();
            assertEquals(List.of("Shared", "Single", "Unused"),
                    summaries.stream().map(TagSummary::name).toList());
            assertEquals(List.of(2, 1, 0),
                    summaries.stream().map(TagSummary::usageCount).toList());
            assertFalse(summaries.get(0).unused());
            assertTrue(summaries.get(2).unused());
            assertEquals(unused.id(), summaries.get(2).id());
        } finally {
            ctx.close();
        }
    }

    @Test
    void cleanupDeletesOnlyCurrentlyUnusedTags() {
        TestContext ctx = createContext("cleanup-unused.db");
        try {
            long clipId = insertClip(ctx.clips, "clip", "hash-clip", 1_000L);
            ClipTag assigned = ctx.tags.createOrGet("Assigned");
            ctx.tags.createOrGet("Unused A");
            ctx.tags.createOrGet("Unused B");
            ctx.tags.addTagToClip(clipId, assigned.id());

            assertEquals(2, ctx.tags.cleanupUnusedTags());
            assertEquals(List.of("Assigned"),
                    ctx.tags.listAllWithUsage().stream().map(TagSummary::name).toList());
            assertEquals(0, ctx.tags.cleanupUnusedTags());
            assertEquals(List.of("Assigned"), tagNames(ctx.tags, clipId));
        } finally {
            ctx.close();
        }
    }

    @Test
    void batchAssignmentLookupPreservesRequestedClipsAndTagOrder() {
        TestContext ctx = createContext("batch-tag-read.db");
        try {
            long firstClip = insertClip(ctx.clips, "first", "hash-first", 1_000L);
            long secondClip = insertClip(ctx.clips, "second", "hash-second", 2_000L);
            long untaggedClip = insertClip(ctx.clips, "untagged", "hash-untagged", 3_000L);

            ClipTag beta = ctx.tags.createOrGet("Beta");
            ClipTag alpha = ctx.tags.createOrGet("Alpha");
            ctx.tags.addTagToClip(firstClip, beta.id());
            ctx.tags.addTagToClip(firstClip, alpha.id());
            ctx.tags.addTagToClip(secondClip, beta.id());

            var assignments = ctx.tags.listForClips(
                    List.of(secondClip, firstClip, untaggedClip, secondClip)
            );

            assertEquals(
                    List.of("Beta"),
                    assignments.get(secondClip).stream().map(ClipTag::name).toList()
            );
            assertEquals(
                    List.of("Alpha", "Beta"),
                    assignments.get(firstClip).stream().map(ClipTag::name).toList()
            );
            assertTrue(assignments.get(untaggedClip).isEmpty());
            assertEquals(3, assignments.size());
        } finally {
            ctx.close();
        }
    }

    private TestContext createContext(String fileName) {
        Path dbPath = tempDir.resolve(fileName);
        Database db = new Database(dbPath);
        db.init();
        return new TestContext(
                db,
                new ClipEntryDao(db.jdbcUrl()),
                new TagDao(db.jdbcUrl())
        );
    }

    private long insertClip(
            ClipEntryDao clips,
            String content,
            String hash,
            long createdAt
    ) {
        clips.insert(content, content, hash, createdAt);
        return clips.listLatest(100).stream()
                .filter(entry -> content.equals(entry.content()))
                .map(ClipEntry::id)
                .findFirst()
                .orElseThrow();
    }

    private List<String> tagNames(TagDao tags, long clipId) {
        return tags.listForClip(clipId).stream().map(ClipTag::name).toList();
    }

    private int relationCount(String jdbcUrl, String column, long id) throws Exception {
        try (Connection c = DriverManager.getConnection(jdbcUrl);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM clip_tags WHERE " + column + " = " + id
             )) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private record TestContext(Database db, ClipEntryDao clips, TagDao tags) {
        void close() {
            tags.closeForCurrentThread();
            clips.closeForCurrentThread();
            db.close();
        }
    }
}




