/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.dao;

import io.xseries.xclip.data.model.ClipEntry;
import io.xseries.xclip.domain.duplicate.DuplicateContentKeys;
import io.xseries.xclip.domain.duplicate.DuplicatePolicyEngine;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ClipEntryDao {
    private final DaoConnectionContext connections;

    public ClipEntryDao(String jdbcUrl) {
        this.connections = new DaoConnectionContext(jdbcUrl);
    }

    /**
     * Backwards-compatible default-policy insert used by existing callers/tests.
     *
     * v6 no longer relies on a UNIQUE hash constraint because finite duplicate
     * windows can intentionally create multiple rows with equal content.
     */
    public void insert(String content, String contentNorm, String contentHash, long createdAt) {
        DuplicateContentKeys calculated = DuplicateContentKeys.from(content);
        DuplicateContentKeys keys = new DuplicateContentKeys(
                calculated.exactHash(),
                calculated.exactCaseInsensitiveHash(),
                contentHash,
                calculated.normalizedCaseInsensitiveHash()
        );

        List<DuplicateCandidate> candidates = findDuplicateCandidates(
                DuplicateContentKeys.KeyKind.NORMALIZED,
                contentHash,
                0L
        );
        if (!candidates.isEmpty()) {
            boolean updated = applyDuplicate(
                    candidates.get(0).id(),
                    content,
                    contentNorm,
                    keys,
                    createdAt,
                    DuplicatePolicyEngine.Decision.UPDATE_EXISTING_MOVE_RECENT_TO_TOP
            );
            if (updated) return;
        }

        insertNew(content, contentNorm, keys, createdAt);
    }

    public List<DuplicateCandidate> findDuplicateCandidates(
            DuplicateContentKeys.KeyKind keyKind,
            String hash,
            long cutoffInclusive
    ) {
        if (keyKind == null) throw new IllegalArgumentException("keyKind is required");
        if (hash == null || hash.isBlank()) throw new IllegalArgumentException("hash is required");
        if (cutoffInclusive < 0) throw new IllegalArgumentException("cutoffInclusive cannot be negative");

        String column = switch (keyKind) {
            case EXACT -> "content_exact_hash";
            case EXACT_CASE_INSENSITIVE -> "content_exact_ci_hash";
            case NORMALIZED -> "content_hash";
            case NORMALIZED_CASE_INSENSITIVE -> "content_norm_ci_hash";
        };

        String sql = """
                SELECT id, content, is_favorite, last_copied_at
                FROM clip_entries
                WHERE %s = ?
                  AND (? = 0 OR last_copied_at >= ?)
                ORDER BY last_copied_at DESC, id DESC
                """.formatted(column);

        List<DuplicateCandidate> candidates = new ArrayList<>();
        try (PreparedStatement ps = connections.connection().prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setLong(2, cutoffInclusive);
            ps.setLong(3, cutoffInclusive);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    candidates.add(new DuplicateCandidate(
                            rs.getLong("id"),
                            rs.getString("content"),
                            rs.getInt("is_favorite") != 0,
                            rs.getLong("last_copied_at")
                    ));
                }
            }
            return List.copyOf(candidates);
        } catch (Exception e) {
            throw new RuntimeException("findDuplicateCandidates failed", e);
        }
    }

    public void insertNew(
            String content,
            String contentNorm,
            DuplicateContentKeys keys,
            long createdAt
    ) {
        String sql = """
                INSERT INTO clip_entries(
                    content,
                    content_norm,
                    content_hash,
                    content_exact_hash,
                    content_exact_ci_hash,
                    content_norm_ci_hash,
                    created_at,
                    last_copied_at,
                    use_count
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                """;

        try (PreparedStatement ps = connections.connection().prepareStatement(sql)) {
            ps.setString(1, content);
            ps.setString(2, contentNorm);
            ps.setString(3, keys.normalizedHash());
            ps.setString(4, keys.exactHash());
            ps.setString(5, keys.exactCaseInsensitiveHash());
            ps.setString(6, keys.normalizedCaseInsensitiveHash());
            ps.setLong(7, createdAt);
            ps.setLong(8, createdAt);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("insertNew failed", e);
        }
    }

    public boolean applyDuplicate(
            long id,
            String content,
            String contentNorm,
            DuplicateContentKeys keys,
            long copiedAt,
            DuplicatePolicyEngine.Decision decision
    ) {
        if (id <= 0) throw new IllegalArgumentException("id must be positive");
        if (decision == null || !decision.duplicate()) {
            throw new IllegalArgumentException("duplicate decision is required");
        }

        Connection c = connections.connection();
        boolean previousAutoCommit = connections.beginTransaction(
                c,
                "duplicate transaction setup failed"
        );

        try {
            int updated;
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE clip_entries
                    SET content = ?,
                        content_norm = ?,
                        content_hash = ?,
                        content_exact_hash = ?,
                        content_exact_ci_hash = ?,
                        content_norm_ci_hash = ?,
                        last_copied_at = CASE WHEN ? = 1 THEN ? ELSE last_copied_at END,
                        use_count = use_count + 1
                    WHERE id = ?
                    """)) {
                ps.setString(1, content);
                ps.setString(2, contentNorm);
                ps.setString(3, keys.normalizedHash());
                ps.setString(4, keys.exactHash());
                ps.setString(5, keys.exactCaseInsensitiveHash());
                ps.setString(6, keys.normalizedCaseInsensitiveHash());
                ps.setInt(7, decision.updateLastCopiedAt() ? 1 : 0);
                ps.setLong(8, copiedAt);
                ps.setLong(9, id);
                updated = ps.executeUpdate();
            }

            if (updated == 0) {
                c.rollback();
                return false;
            }

            if (decision.movePinnedToTop() && isFavorite(c, id)) {
                List<Long> pinnedIds = loadPinnedIds(c);
                if (pinnedIds.remove(id)) {
                    pinnedIds.add(0, id);
                    persistPinnedOrder(c, pinnedIds);
                }
            }

            c.commit();
            return true;
        } catch (Exception e) {
            connections.rollbackQuietly(c);
            throw new RuntimeException("applyDuplicate failed", e);
        } finally {
            connections.restoreAutoCommit(c, previousAutoCommit);
        }
    }

    public record DuplicateCandidate(
            long id,
            String content,
            boolean pinned,
            long lastCopiedAt
    ) {}

    public List<ClipEntry> listLatest(int limit) {
        return listLatest(limit, null);
    }

    /**
     * Lists clips with an optional pinned-state restriction.
     *
     * favoriteFilter meanings:
     * - null  -> all clips;
     * - true  -> pinned clips only;
     * - false -> recent clips only.
     */
    public List<ClipEntry> listLatest(int limit, Boolean favoriteFilter) {
        String sql = """
            SELECT id, content, title, is_favorite, pin_order,
                   last_copied_at AS created_at
            FROM clip_entries
            WHERE (? IS NULL OR is_favorite = ?)
            ORDER BY is_favorite DESC,
                     CASE
                         WHEN is_favorite = 1 THEN COALESCE(pin_order, 2147483647)
                         ELSE 2147483647
                     END ASC,
                     last_copied_at DESC,
                     id DESC
            LIMIT ?
            """;
        Connection c = connections.connection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bindOptionalFavorite(ps, 1, favoriteFilter);
            ps.setInt(3, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                return map(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("listLatest failed", e);
        }
    }

    public List<ClipEntry> search(String q, int limit) {
        return search(q, limit, null);
    }

    /**
     * Unified popup query supporting scope, tag filtering, and tag-name search.
     *
     * Search matches clip content, pinned titles, and assigned tag names.
     * A selected tag is applied independently, so text search can still match
     * any assigned tag while the result remains restricted to that tag.
     */
    public List<ClipEntry> queryLatest(
            String q,
            int limit,
            Boolean favoriteFilter,
            Long tagId
    ) {
        return queryLatest(
                q,
                limit,
                favoriteFilter,
                tagId,
                List.of(),
                List.of()
        );
    }

    /**
     * Unified bounded query for popup text, toolbar filters, and advanced tag operators.
     *
     * Required tag identities use AND semantics. Excluded identities use NOT EXISTS.
     * Identity values are exact case-insensitive tag identities stored in tags.name_norm.
     */
    public List<ClipEntry> queryLatest(
            String q,
            int limit,
            Boolean favoriteFilter,
            Long tagId,
            List<String> requiredTagIdentities,
            List<String> excludedTagIdentities
    ) {
        if (tagId != null && tagId <= 0) {
            throw new IllegalArgumentException("tagId must be positive");
        }

        List<String> requiredTags = normalizedTagIdentities(
                requiredTagIdentities,
                "requiredTagIdentities"
        );
        List<String> excludedTags = normalizedTagIdentities(
                excludedTagIdentities,
                "excludedTagIdentities"
        );

        String normalizedQuery = q == null ? "" : q.trim();
        String like = "%" + escapeLike(normalizedQuery) + "%";

        StringBuilder sql = new StringBuilder("""
            SELECT ce.id, ce.content, ce.title, ce.is_favorite, ce.pin_order,
                   ce.last_copied_at AS created_at
            FROM clip_entries AS ce
            WHERE (? IS NULL OR ce.is_favorite = ?)
              AND (
                    ? IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM clip_tags AS selected_ct
                        WHERE selected_ct.clip_id = ce.id
                          AND selected_ct.tag_id = ?
                    )
                  )
              AND (
                    ? = ''
                    OR ce.content LIKE ? ESCAPE '\\'
                    OR (
                        ce.is_favorite = 1
                        AND COALESCE(ce.title, '') LIKE ? ESCAPE '\\'
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM clip_tags AS search_ct
                        JOIN tags AS search_tag ON search_tag.id = search_ct.tag_id
                        WHERE search_ct.clip_id = ce.id
                          AND search_tag.name LIKE ? ESCAPE '\\'
                    )
                  )
            """);

        for (int index = 0; index < requiredTags.size(); index++) {
            sql.append("""
                  AND EXISTS (
                        SELECT 1
                        FROM clip_tags AS required_ct
                        JOIN tags AS required_tag ON required_tag.id = required_ct.tag_id
                        WHERE required_ct.clip_id = ce.id
                          AND required_tag.name_norm = ?
                  )
                """);
        }
        for (int index = 0; index < excludedTags.size(); index++) {
            sql.append("""
                  AND NOT EXISTS (
                        SELECT 1
                        FROM clip_tags AS excluded_ct
                        JOIN tags AS excluded_tag ON excluded_tag.id = excluded_ct.tag_id
                        WHERE excluded_ct.clip_id = ce.id
                          AND excluded_tag.name_norm = ?
                  )
                """);
        }

        sql.append("""
            ORDER BY ce.is_favorite DESC,
                     CASE
                         WHEN ce.is_favorite = 1 THEN COALESCE(ce.pin_order, 2147483647)
                         ELSE 2147483647
                     END ASC,
                     ce.last_copied_at DESC,
                     ce.id DESC
            LIMIT ?
            """);

        Connection c = connections.connection();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int parameter = 1;
            bindOptionalFavorite(ps, parameter, favoriteFilter);
            parameter += 2;

            if (tagId == null) {
                ps.setNull(parameter, Types.INTEGER);
                ps.setNull(parameter + 1, Types.INTEGER);
            } else {
                ps.setLong(parameter, tagId);
                ps.setLong(parameter + 1, tagId);
            }
            parameter += 2;

            ps.setString(parameter++, normalizedQuery);
            ps.setString(parameter++, like);
            ps.setString(parameter++, like);
            ps.setString(parameter++, like);

            for (String identity : requiredTags) {
                ps.setString(parameter++, identity);
            }
            for (String identity : excludedTags) {
                ps.setString(parameter++, identity);
            }

            ps.setInt(parameter, Math.max(1, limit));

            try (ResultSet rs = ps.executeQuery()) {
                return map(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("queryLatest failed", e);
        }
    }

    /**
     * Searches content and pinned titles with an optional pinned-state restriction.
     */
    public List<ClipEntry> search(String q, int limit, Boolean favoriteFilter) {
        String sql = """
            SELECT id, content, title, is_favorite, pin_order,
                   last_copied_at AS created_at
            FROM clip_entries
            WHERE (
                    content LIKE ? ESCAPE '\\'
                    OR (is_favorite = 1 AND COALESCE(title, '') LIKE ? ESCAPE '\\')
                  )
              AND (? IS NULL OR is_favorite = ?)
            ORDER BY is_favorite DESC,
                     CASE
                         WHEN is_favorite = 1 THEN COALESCE(pin_order, 2147483647)
                         ELSE 2147483647
                     END ASC,
                     last_copied_at DESC,
                     id DESC
            LIMIT ?
            """;
        String like = "%" + escapeLike(q == null ? "" : q) + "%";
        Connection c = connections.connection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            bindOptionalFavorite(ps, 3, favoriteFilter);
            ps.setInt(5, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                return map(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("search failed", e);
        }
    }

    /**
     * Returns the total number of persisted clips, independent of popup search
     * and scope/type filters.
     */
    public int countAll() {
        try (PreparedStatement ps = connections.connection().prepareStatement(
                "SELECT COUNT(*) FROM clip_entries");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            throw new RuntimeException("countAll failed", e);
        }
    }

    public void deleteById(long id) {
        Connection c = connections.connection();
        boolean previousAutoCommit = connections.beginTransaction(
                c,
                "delete transaction setup failed"
        );

        try {
            boolean wasFavorite = isFavorite(c, id);
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM clip_entries WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }

            if (wasFavorite) {
                persistPinnedOrder(c, loadPinnedIds(c));
            }

            c.commit();
        } catch (Exception e) {
            connections.rollbackQuietly(c);
            throw new RuntimeException("delete failed", e);
        } finally {
            connections.restoreAutoCommit(c, previousAutoCommit);
        }
    }

    /**
     * Used by PopupWindow "Clear" button.
     * Keeps favorites intact.
     */
    public int deleteAllNonFavorites() {
        String sql = "DELETE FROM clip_entries WHERE is_favorite = 0";
        Connection c = connections.connection();
        try (Statement st = c.createStatement()) {
            return st.executeUpdate(sql);
        } catch (Exception e) {
            throw new RuntimeException("deleteAllNonFavorites failed", e);
        }
    }

    /**
     * Pins or unpins a clip while maintaining a dense, deterministic pin order.
     *
     * Newly pinned clips are placed at the top. Unpinning clears pin_order but
     * intentionally keeps optional metadata such as the custom title.
     */
    public void setFavorite(long id, boolean favorite) {
        Connection c = connections.connection();
        boolean previousAutoCommit = connections.beginTransaction(
                c,
                "favorite transaction setup failed"
        );

        try {
            boolean currentlyFavorite = isFavorite(c, id);

            if (favorite) {
                if (!currentlyFavorite) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE clip_entries SET is_favorite = 1 WHERE id = ?")) {
                        ps.setLong(1, id);
                        ps.executeUpdate();
                    }

                    List<Long> pinnedIds = loadPinnedIds(c);
                    if (pinnedIds.remove(id)) {
                        pinnedIds.add(0, id);
                        persistPinnedOrder(c, pinnedIds);
                    }
                }
            } else if (currentlyFavorite) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE clip_entries SET is_favorite = 0, pin_order = NULL WHERE id = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                persistPinnedOrder(c, loadPinnedIds(c));
            }

            c.commit();
        } catch (Exception e) {
            connections.rollbackQuietly(c);
            throw new RuntimeException("favorite update failed", e);
        } finally {
            connections.restoreAutoCommit(c, previousAutoCommit);
        }
    }

    /**
     * Stores an optional user-facing title for a clip.
     *
     * Blank titles are persisted as NULL. The clipboard content itself is never changed.
     */
    public void setTitle(long id, String title) {
        String normalized = title == null ? null : title.trim();
        if (normalized != null && normalized.isEmpty()) {
            normalized = null;
        }

        String sql = "UPDATE clip_entries SET title = ? WHERE id = ? AND is_favorite = 1";
        Connection c = connections.connection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (normalized == null) {
                ps.setNull(1, Types.VARCHAR);
            } else {
                ps.setString(1, normalized);
            }
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("title update failed", e);
        }
    }

    public boolean movePinnedUp(long id) {
        return movePinned(id, PinMove.UP);
    }

    public boolean movePinnedDown(long id) {
        return movePinned(id, PinMove.DOWN);
    }

    public boolean movePinnedToTop(long id) {
        return movePinned(id, PinMove.TOP);
    }

    public boolean movePinnedToBottom(long id) {
        return movePinned(id, PinMove.BOTTOM);
    }

    private boolean movePinned(long id, PinMove move) {
        Connection c = connections.connection();
        boolean previousAutoCommit = connections.beginTransaction(
                c,
                "pin reorder transaction setup failed"
        );

        try {
            List<Long> pinnedIds = loadPinnedIds(c);
            int from = pinnedIds.indexOf(id);
            if (from < 0 || pinnedIds.size() < 2) {
                c.commit();
                return false;
            }

            int to = switch (move) {
                case UP -> Math.max(0, from - 1);
                case DOWN -> Math.min(pinnedIds.size() - 1, from + 1);
                case TOP -> 0;
                case BOTTOM -> pinnedIds.size() - 1;
            };

            if (to == from) {
                c.commit();
                return false;
            }

            Long moved = pinnedIds.remove(from);
            pinnedIds.add(to, moved);
            persistPinnedOrder(c, pinnedIds);
            c.commit();
            return true;
        } catch (Exception e) {
            connections.rollbackQuietly(c);
            throw new RuntimeException("pin reorder failed", e);
        } finally {
            connections.restoreAutoCommit(c, previousAutoCommit);
        }
    }

    private boolean isFavorite(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT is_favorite FROM clip_entries WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("is_favorite") != 0;
            }
        }
    }

    private List<Long> loadPinnedIds(Connection c) throws SQLException {
        String sql = """
                SELECT id
                FROM clip_entries
                WHERE is_favorite = 1
                ORDER BY COALESCE(pin_order, 2147483647) ASC,
                         last_copied_at DESC,
                         id DESC
                """;

        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getLong("id"));
            }
        }
        return ids;
    }

    private void persistPinnedOrder(Connection c, List<Long> pinnedIds) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE clip_entries SET pin_order = ? WHERE id = ? AND is_favorite = 1")) {
            for (int i = 0; i < pinnedIds.size(); i++) {
                ps.setInt(1, i);
                ps.setLong(2, pinnedIds.get(i));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void pruneToLimit(int limit) {
        // Keep ALL favorites; prune only non-favorites.
        String sql = """
            DELETE FROM clip_entries
            WHERE is_favorite = 0
              AND id NOT IN (
                SELECT id FROM clip_entries
                WHERE is_favorite = 0
                ORDER BY last_copied_at DESC, id DESC
                LIMIT ?
              )
            """;
        Connection c = connections.connection();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("pruneToLimit failed", e);
        }
    }

    /**
     * Loads only unpinned rows old enough to match at least one retention rule.
     * Exact per-type decisions are made in the domain service because content
     * type is derived metadata and is intentionally not stored in SQLite.
     */
    public List<RetentionCandidate> listRetentionCandidates(long cutoffExclusive) {
        if (cutoffExclusive < 0) {
            throw new IllegalArgumentException("cutoffExclusive cannot be negative");
        }

        String sql = """
                SELECT id, content, last_copied_at
                FROM clip_entries
                WHERE is_favorite = 0
                  AND last_copied_at < ?
                ORDER BY id ASC
                """;

        List<RetentionCandidate> candidates = new ArrayList<>();
        try (PreparedStatement ps = connections.connection().prepareStatement(sql)) {
            ps.setLong(1, cutoffExclusive);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    candidates.add(new RetentionCandidate(
                            rs.getLong("id"),
                            rs.getString("content"),
                            rs.getLong("last_copied_at")
                    ));
                }
            }
            return List.copyOf(candidates);
        } catch (Exception e) {
            throw new RuntimeException("listRetentionCandidates failed", e);
        }
    }

    public record RetentionCandidate(long id, String content, long lastCopiedAt) {}

    private List<ClipEntry> map(ResultSet rs) throws SQLException {
        List<ClipEntry> list = new ArrayList<>();
        while (rs.next()) {
            long id = rs.getLong("id");
            String content = rs.getString("content");
            String title = rs.getString("title");
            boolean fav = rs.getInt("is_favorite") != 0;
            int rawPinOrder = rs.getInt("pin_order");
            Integer pinOrder = rs.wasNull() ? null : rawPinOrder;
            long createdAt = rs.getLong("created_at");
            list.add(new ClipEntry(id, content, title, fav, pinOrder, createdAt));
        }
        return list;
    }

    private void bindOptionalFavorite(PreparedStatement ps, int firstIndex, Boolean favoriteFilter)
            throws SQLException {
        if (favoriteFilter == null) {
            ps.setNull(firstIndex, Types.INTEGER);
            ps.setNull(firstIndex + 1, Types.INTEGER);
        } else {
            int value = favoriteFilter ? 1 : 0;
            ps.setInt(firstIndex, value);
            ps.setInt(firstIndex + 1, value);
        }
    }

    private List<String> normalizedTagIdentities(List<String> identities, String field) {
        if (identities == null || identities.isEmpty()) return List.of();

        Set<String> normalized = new LinkedHashSet<>();
        for (String identity : identities) {
            String value = identity == null
                    ? ""
                    : identity.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) {
                throw new IllegalArgumentException(field + " cannot contain blank values");
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public void closeForCurrentThread() {
        connections.closeForCurrentThread();
    }

    public int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;

        List<Long> validIds = ids.stream()
                .filter(java.util.Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (validIds.isEmpty()) return 0;

        final int batchSize = 500;
        Connection c = connections.connection();
        boolean previousAutoCommit = connections.beginTransaction(
                c,
                "deleteByIds transaction setup failed"
        );

        try {
            int deleted = 0;
            for (int offset = 0; offset < validIds.size(); offset += batchSize) {
                int end = Math.min(validIds.size(), offset + batchSize);
                List<Long> batch = validIds.subList(offset, end);

                StringBuilder sql = new StringBuilder(
                        "DELETE FROM clip_entries WHERE id IN ("
                );
                for (int i = 0; i < batch.size(); i++) {
                    if (i > 0) sql.append(", ");
                    sql.append("?");
                }
                sql.append(")");

                try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                    for (int i = 0; i < batch.size(); i++) {
                        ps.setLong(i + 1, batch.get(i));
                    }
                    deleted += ps.executeUpdate();
                }
            }
            c.commit();
            return deleted;
        } catch (Exception e) {
            connections.rollbackQuietly(c);
            throw new RuntimeException("deleteByIds failed", e);
        } finally {
            connections.restoreAutoCommit(c, previousAutoCommit);
        }
    }


    private enum PinMove {
        UP,
        DOWN,
        TOP,
        BOTTOM
    }
}
