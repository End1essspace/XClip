/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.dao;

import io.xseries.xclip.data.db.Database;
import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.data.model.ClipTag;
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
