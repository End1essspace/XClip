/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.dao;

import io.xseries.xclip.data.model.ClipTag;
import io.xseries.xclip.data.model.TagSummary;
import io.xseries.xclip.domain.service.TagNamePolicy;
import io.xseries.xclip.domain.service.TagNamePolicy.NormalizedTagName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SQLite contract for user-defined tags and clip-tag assignments.
 *
 * All write methods are deterministic and safe to retry:
 * - tag identity is case-insensitive through name_norm;
 * - assigning an existing relation is a no-op;
 * - replacing a clip's tags is atomic;
 * - clip/tag deletion relies on ON DELETE CASCADE.
 */
public final class TagDao {

    public static final int MAX_TAG_NAME_LENGTH = TagNamePolicy.MAX_NAME_LENGTH;

    private final String jdbcUrl;
    private final ThreadLocal<Connection> tlConn = ThreadLocal.withInitial(this::openConnection);

    public TagDao(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    private Connection openConnection() {
        try {
            Connection c = DriverManager.getConnection(jdbcUrl);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA foreign_keys=ON;");
                st.execute("PRAGMA busy_timeout=3000;");
            }
            return c;
        } catch (Exception e) {
            throw new RuntimeException("Failed to open SQLite connection: " + jdbcUrl, e);
        }
    }

    private Connection conn() {
        try {
            Connection c = tlConn.get();
            if (c == null || c.isClosed()) {
                c = openConnection();
                tlConn.set(c);
            }
            return c;
        } catch (Exception e) {
            throw new RuntimeException("Failed to obtain SQLite connection", e);
        }
    }

    /**
     * Creates a tag or returns the existing case-insensitive match.
     *
     * Repeated calls do not change the existing display casing or created_at.
     */
    public ClipTag createOrGet(String rawName) {
        NormalizedTagName normalized = TagNamePolicy.normalize(rawName);
        Connection c = conn();
        boolean previousAutoCommit = beginTransaction(c, "tag create transaction setup failed");

        try {
            ClipTag tag = createOrGet(c, normalized);
            c.commit();
            return tag;
        } catch (Exception e) {
            rollbackQuietly(c);
            throw new RuntimeException("createOrGet tag failed", e);
        } finally {
            restoreAutoCommit(c, previousAutoCommit);
        }
    }

    private ClipTag createOrGet(Connection c, NormalizedTagName normalized) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO tags(name, name_norm, created_at)
                VALUES (?, ?, ?)
                ON CONFLICT(name_norm) DO NOTHING
                """)) {
            ps.setString(1, normalized.displayName());
            ps.setString(2, normalized.identity());
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }

        ClipTag tag = findByNormalizedName(c, normalized.identity());
        if (tag == null) {
            throw new SQLException("Tag insert completed but row could not be loaded");
        }
        return tag;
    }

    public List<ClipTag> listAll() {
        String sql = """
                SELECT id, name, created_at
                FROM tags
                ORDER BY name COLLATE NOCASE ASC, id ASC
                """;

        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return mapTags(rs);
        } catch (Exception e) {
            throw new RuntimeException("listAll tags failed", e);
        }
    }

    /**
     * Lists all tags together with their current clip-assignment count.
     *
     * The LEFT JOIN keeps unused tags visible for explicit cleanup.
     */
    public List<TagSummary> listAllWithUsage() {
        String sql = """
                SELECT t.id, t.name, t.created_at, COUNT(ct.clip_id) AS usage_count
                FROM tags AS t
                LEFT JOIN clip_tags AS ct ON ct.tag_id = t.id
                GROUP BY t.id, t.name, t.created_at
                ORDER BY t.name COLLATE NOCASE ASC, t.id ASC
                """;

        List<TagSummary> tags = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tags.add(new TagSummary(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getLong("created_at"),
                        rs.getInt("usage_count")
                ));
            }
            return List.copyOf(tags);
        } catch (Exception e) {
            throw new RuntimeException("listAllWithUsage failed", e);
        }
    }

    public List<ClipTag> listForClip(long clipId) {
        requirePositiveId(clipId, "clipId");

        String sql = """
                SELECT t.id, t.name, t.created_at
                FROM tags AS t
                JOIN clip_tags AS ct ON ct.tag_id = t.id
                WHERE ct.clip_id = ?
                ORDER BY t.name COLLATE NOCASE ASC, t.id ASC
                """;

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setLong(1, clipId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapTags(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("listForClip failed", e);
        }
    }

    /**
     * Loads assignments for a bounded set of clips without issuing one query
     * per virtualized row. Input order is preserved and every returned tag list
     * is sorted by the same deterministic order as listForClip.
     */
    public Map<Long, List<ClipTag>> listForClips(List<Long> clipIds) {
        List<Long> uniqueClipIds = uniquePositiveIds(clipIds, "clipIds");
        if (uniqueClipIds.isEmpty()) return Map.of();

        LinkedHashMap<Long, List<ClipTag>> result = new LinkedHashMap<>();
        for (Long clipId : uniqueClipIds) {
            result.put(clipId, new ArrayList<>());
        }

        final int batchSize = 500;
        Connection c = conn();

        for (int offset = 0; offset < uniqueClipIds.size(); offset += batchSize) {
            int end = Math.min(uniqueClipIds.size(), offset + batchSize);
            List<Long> batch = uniqueClipIds.subList(offset, end);

            StringBuilder sql = new StringBuilder("""
                    SELECT ct.clip_id, t.id, t.name, t.created_at
                    FROM clip_tags AS ct
                    JOIN tags AS t ON t.id = ct.tag_id
                    WHERE ct.clip_id IN (
                    """);
            for (int index = 0; index < batch.size(); index++) {
                if (index > 0) sql.append(", ");
                sql.append("?");
            }
            sql.append("""
                    )
                    ORDER BY ct.clip_id ASC,
                             t.name COLLATE NOCASE ASC,
                             t.id ASC
                    """);

            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int index = 0; index < batch.size(); index++) {
                    ps.setLong(index + 1, batch.get(index));
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long clipId = rs.getLong("clip_id");
                        List<ClipTag> tags = result.get(clipId);
                        if (tags != null) tags.add(mapTag(rs));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("listForClips failed", e);
            }
        }

        LinkedHashMap<Long, List<ClipTag>> immutable = new LinkedHashMap<>();
        for (Map.Entry<Long, List<ClipTag>> entry : result.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(immutable);
    }

    /**
     * Adds one relation. Returns false only when the relation already existed.
     * Unknown clip/tag ids are rejected instead of silently creating partial state.
     */
    public boolean addTagToClip(long clipId, long tagId) {
        requirePositiveId(clipId, "clipId");
        requirePositiveId(tagId, "tagId");

        Connection c = conn();
        boolean previousAutoCommit = beginTransaction(c, "tag assignment transaction setup failed");

        try {
            requireClipExists(c, clipId);
            requireTagExists(c, tagId);

            boolean inserted;
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO clip_tags(clip_id, tag_id, assigned_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(clip_id, tag_id) DO NOTHING
                    """)) {
                ps.setLong(1, clipId);
                ps.setLong(2, tagId);
                ps.setLong(3, System.currentTimeMillis());
                inserted = ps.executeUpdate() > 0;
            }

            c.commit();
            return inserted;
        } catch (RuntimeException | SQLException e) {
            rollbackQuietly(c);
            throw propagate("addTagToClip failed", e);
        } finally {
            restoreAutoCommit(c, previousAutoCommit);
        }
    }

    public boolean removeTagFromClip(long clipId, long tagId) {
        requirePositiveId(clipId, "clipId");
        requirePositiveId(tagId, "tagId");

        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM clip_tags WHERE clip_id = ? AND tag_id = ?")) {
            ps.setLong(1, clipId);
            ps.setLong(2, tagId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("removeTagFromClip failed", e);
        }
    }

    /**
     * Atomically replaces all assignments for one clip.
     *
     * Duplicate ids are collapsed while preserving caller order. Validation is
     * completed before existing relations are removed, so invalid input cannot
     * leave the clip with a partially updated tag set.
     */
    public void replaceTagsForClip(long clipId, List<Long> tagIds) {
        requirePositiveId(clipId, "clipId");
        List<Long> uniqueTagIds = uniquePositiveIds(tagIds, "tagIds");

        Connection c = conn();
        boolean previousAutoCommit = beginTransaction(c, "replace tags transaction setup failed");

        try {
            requireClipExists(c, clipId);
            for (Long tagId : uniqueTagIds) {
                requireTagExists(c, tagId);
            }

            try (PreparedStatement delete = c.prepareStatement(
                    "DELETE FROM clip_tags WHERE clip_id = ?")) {
                delete.setLong(1, clipId);
                delete.executeUpdate();
            }

            if (!uniqueTagIds.isEmpty()) {
                long assignedAt = System.currentTimeMillis();
                try (PreparedStatement insert = c.prepareStatement("""
                        INSERT INTO clip_tags(clip_id, tag_id, assigned_at)
                        VALUES (?, ?, ?)
                        """)) {
                    for (Long tagId : uniqueTagIds) {
                        insert.setLong(1, clipId);
                        insert.setLong(2, tagId);
                        insert.setLong(3, assignedAt);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }

            c.commit();
        } catch (RuntimeException | SQLException e) {
            rollbackQuietly(c);
            throw propagate("replaceTagsForClip failed", e);
        } finally {
            restoreAutoCommit(c, previousAutoCommit);
        }
    }

    /**
     * Atomically applies one tag-editor save across every selected clip.
     *
     * Existing mixed assignments can be preserved by omitting a tag from both
     * assignTagIds and removeTagIds. New names are created (or resolved to an
     * existing case-insensitive match) and assigned to every selected clip in
     * the same transaction, so Cancel or a failed save cannot leave orphaned
     * tags or partially updated selections.
     */
    public List<ClipTag> applyEdit(
            List<Long> clipIds,
            List<Long> assignTagIds,
            List<Long> removeTagIds,
            List<String> createAndAssignNames
    ) {
        List<Long> uniqueClipIds = uniquePositiveIds(clipIds, "clipIds");
        if (uniqueClipIds.isEmpty()) {
            throw new IllegalArgumentException("clipIds cannot be empty");
        }

        List<Long> uniqueAssignIds = uniquePositiveIds(assignTagIds, "assignTagIds");
        List<Long> uniqueRemoveIds = uniquePositiveIds(removeTagIds, "removeTagIds");
        List<NormalizedTagName> normalizedNewNames = normalizeUniqueNames(createAndAssignNames);

        Connection c = conn();
        boolean previousAutoCommit = beginTransaction(c, "tag edit transaction setup failed");

        try {
            for (Long clipId : uniqueClipIds) {
                requireClipExists(c, clipId);
            }
            for (Long tagId : uniqueAssignIds) {
                requireTagExists(c, tagId);
            }
            for (Long tagId : uniqueRemoveIds) {
                requireTagExists(c, tagId);
            }

            LinkedHashSet<Long> effectiveAssignIds = new LinkedHashSet<>(uniqueAssignIds);
            List<ClipTag> resolvedNewTags = new ArrayList<>();
            for (NormalizedTagName normalized : normalizedNewNames) {
                ClipTag tag = createOrGet(c, normalized);
                effectiveAssignIds.add(tag.id());
                resolvedNewTags.add(tag);
            }

            if (!uniqueRemoveIds.isEmpty()) {
                try (PreparedStatement delete = c.prepareStatement(
                        "DELETE FROM clip_tags WHERE clip_id = ? AND tag_id = ?")) {
                    for (Long clipId : uniqueClipIds) {
                        for (Long tagId : uniqueRemoveIds) {
                            delete.setLong(1, clipId);
                            delete.setLong(2, tagId);
                            delete.addBatch();
                        }
                    }
                    delete.executeBatch();
                }
            }

            if (!effectiveAssignIds.isEmpty()) {
                long assignedAt = System.currentTimeMillis();
                try (PreparedStatement insert = c.prepareStatement("""
                        INSERT INTO clip_tags(clip_id, tag_id, assigned_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT(clip_id, tag_id) DO NOTHING
                        """)) {
                    for (Long clipId : uniqueClipIds) {
                        for (Long tagId : effectiveAssignIds) {
                            insert.setLong(1, clipId);
                            insert.setLong(2, tagId);
                            insert.setLong(3, assignedAt);
                            insert.addBatch();
                        }
                    }
                    insert.executeBatch();
                }
            }

            c.commit();
            return List.copyOf(resolvedNewTags);
        } catch (RuntimeException | SQLException e) {
            rollbackQuietly(c);
            throw propagate("apply tag edit failed", e);
        } finally {
            restoreAutoCommit(c, previousAutoCommit);
        }
    }

    /**
     * Renames one tag. Case-insensitive collisions are rejected.
     * Returns false when the id does not exist.
     */
    public boolean renameTag(long tagId, String rawName) {
        requirePositiveId(tagId, "tagId");
        NormalizedTagName normalized = TagNamePolicy.normalize(rawName);

        try (PreparedStatement ps = conn().prepareStatement("""
                UPDATE tags
                SET name = ?, name_norm = ?
                WHERE id = ?
                """)) {
            ps.setString(1, normalized.displayName());
            ps.setString(2, normalized.identity());
            ps.setLong(3, tagId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (isConstraintViolation(e)) {
                throw new IllegalArgumentException("A tag with this name already exists", e);
            }
            throw new RuntimeException("renameTag failed", e);
        }
    }

    public boolean deleteTag(long tagId) {
        requirePositiveId(tagId, "tagId");

        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM tags WHERE id = ?")) {
            ps.setLong(1, tagId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("deleteTag failed", e);
        }
    }

    /**
     * Deletes only tags that have no clip assignments.
     *
     * The NOT EXISTS predicate is evaluated in the same SQLite statement, so a
     * tag that becomes assigned before execution is not removed as unused.
     */
    public int cleanupUnusedTags() {
        String sql = """
                DELETE FROM tags
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM clip_tags AS ct
                    WHERE ct.tag_id = tags.id
                )
                """;

        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("cleanupUnusedTags failed", e);
        }
    }

    /**
     * Returns matching clip ids in the same deterministic order used by the popup.
     */
    public List<Long> listClipIdsForTag(long tagId, int limit) {
        requirePositiveId(tagId, "tagId");
        int safeLimit = Math.max(1, limit);

        String sql = """
                SELECT ce.id
                FROM clip_entries AS ce
                JOIN clip_tags AS ct ON ct.clip_id = ce.id
                WHERE ct.tag_id = ?
                ORDER BY ce.is_favorite DESC,
                         CASE
                             WHEN ce.is_favorite = 1 THEN COALESCE(ce.pin_order, 2147483647)
                             ELSE 2147483647
                         END ASC,
                         ce.last_copied_at DESC,
                         ce.id DESC
                LIMIT ?
                """;

        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setLong(1, tagId);
            ps.setInt(2, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
            return List.copyOf(ids);
        } catch (Exception e) {
            throw new RuntimeException("listClipIdsForTag failed", e);
        }
    }

    public void closeForCurrentThread() {
        try {
            Connection c = tlConn.get();
            if (c != null) c.close();
        } catch (Exception ignored) {
        } finally {
            tlConn.remove();
        }
    }

    private ClipTag findByNormalizedName(Connection c, String normalizedName) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT id, name, created_at
                FROM tags
                WHERE name_norm = ?
                """)) {
            ps.setString(1, normalizedName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapTag(rs) : null;
            }
        }
    }

    private List<ClipTag> mapTags(ResultSet rs) throws SQLException {
        List<ClipTag> tags = new ArrayList<>();
        while (rs.next()) tags.add(mapTag(rs));
        return List.copyOf(tags);
    }

    private ClipTag mapTag(ResultSet rs) throws SQLException {
        return new ClipTag(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getLong("created_at")
        );
    }

    private void requireClipExists(Connection c, long clipId) throws SQLException {
        if (!exists(c, "clip_entries", clipId)) {
            throw new IllegalArgumentException("Unknown clip id: " + clipId);
        }
    }

    private void requireTagExists(Connection c, long tagId) throws SQLException {
        if (!exists(c, "tags", tagId)) {
            throw new IllegalArgumentException("Unknown tag id: " + tagId);
        }
    }

    private boolean exists(Connection c, String table, long id) throws SQLException {
        String sql = "SELECT 1 FROM " + table + " WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<Long> uniquePositiveIds(List<Long> ids, String field) {
        if (ids == null || ids.isEmpty()) return List.of();

        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException(field + " must contain only positive ids");
            }
            unique.add(id);
        }
        return List.copyOf(unique);
    }

    private List<NormalizedTagName> normalizeUniqueNames(List<String> rawNames) {
        if (rawNames == null || rawNames.isEmpty()) return List.of();

        java.util.LinkedHashMap<String, NormalizedTagName> unique = new java.util.LinkedHashMap<>();
        for (String rawName : rawNames) {
            NormalizedTagName normalized = TagNamePolicy.normalize(rawName);
            unique.putIfAbsent(normalized.identity(), normalized);
        }
        return List.copyOf(unique.values());
    }

    private void requirePositiveId(long id, String field) {
        if (id <= 0) throw new IllegalArgumentException(field + " must be positive");
    }

    private boolean beginTransaction(Connection c, String message) {
        try {
            boolean previousAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            return previousAutoCommit;
        } catch (SQLException e) {
            throw new RuntimeException(message, e);
        }
    }

    private RuntimeException propagate(String message, Exception e) {
        if (e instanceof RuntimeException runtime) return runtime;
        return new RuntimeException(message, e);
    }

    private boolean isConstraintViolation(SQLException e) {
        return e.getErrorCode() == 19
                || (e.getMessage() != null
                && e.getMessage().toLowerCase(Locale.ROOT).contains("constraint"));
    }

    private void rollbackQuietly(Connection c) {
        try {
            c.rollback();
        } catch (Exception ignored) {
        }
    }

    private void restoreAutoCommit(Connection c, boolean autoCommit) {
        try {
            c.setAutoCommit(autoCommit);
        } catch (Exception ignored) {
        }
    }

}
