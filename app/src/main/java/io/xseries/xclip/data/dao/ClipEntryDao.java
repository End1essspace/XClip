/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.data.dao;

import io.xseries.xclip.data.model.ClipEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class ClipEntryDao {
    private final String jdbcUrl;
    private final ThreadLocal<Connection> tlConn = ThreadLocal.withInitial(this::openConnection);

    public ClipEntryDao(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }
    private Connection openConnection() {
        try {
            Connection c = DriverManager.getConnection(jdbcUrl);
            try (Statement st = c.createStatement()) {
                // Per-connection pragmas (safe even if already set elsewhere)
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
     * Insert clip, but do NOT create duplicates by content_hash.
     * This protects against duplicates across restarts (lastNorm is in-memory only).
     */
    public void insert(String content, String contentNorm, String contentHash, long createdAt) {
        String sql = """
            INSERT INTO clip_entries(content, content_norm, content_hash, created_at)
            SELECT ?, ?, ?, ?
            WHERE NOT EXISTS (
              SELECT 1 FROM clip_entries WHERE content_hash = ?
            )
            """;
        Connection c = conn();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, content);
            ps.setString(2, contentNorm);
            ps.setString(3, contentHash);
            ps.setLong(4, createdAt);
            ps.setString(5, contentHash);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Insert failed", e);
        }
    }

    public List<ClipEntry> listLatest(int limit) {
        String sql = """
            SELECT id, content, is_favorite, created_at
            FROM clip_entries
            ORDER BY is_favorite DESC, created_at DESC
            LIMIT ?
            """;
        Connection c = conn();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                return map(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("listLatest failed", e);
        }
    }

    public List<ClipEntry> search(String q, int limit) {
        String sql = """
            SELECT id, content, is_favorite, created_at
            FROM clip_entries
            WHERE content LIKE ? ESCAPE '\\'
            ORDER BY is_favorite DESC, created_at DESC
            LIMIT ?
            """;
        String like = "%" + escapeLike(q) + "%";
        Connection c = conn();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                return map(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("search failed", e);
        }
    }

    public void deleteById(long id) {
        String sql = "DELETE FROM clip_entries WHERE id = ?";
        Connection c = conn();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("delete failed", e);
        }
    }

    /**
     * Used by PopupWindow "Clear" button.
     * Keeps favorites intact.
     */
    public void deleteAllNonFavorites() {
        String sql = "DELETE FROM clip_entries WHERE is_favorite = 0";
        Connection c = conn();
        try (Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        } catch (Exception e) {
            throw new RuntimeException("deleteAllNonFavorites failed", e);
        }
    }

    public void setFavorite(long id, boolean favorite) {
        String sql = "UPDATE clip_entries SET is_favorite = ? WHERE id = ?";
        Connection c = conn();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, favorite ? 1 : 0);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("favorite update failed", e);
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
                ORDER BY created_at DESC
                LIMIT ?
              )
            """;
        Connection c = conn();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("pruneToLimit failed", e);
        }
    }

    private List<ClipEntry> map(ResultSet rs) throws SQLException {
        List<ClipEntry> list = new ArrayList<>();
        while (rs.next()) {
            long id = rs.getLong("id");
            String content = rs.getString("content");
            boolean fav = rs.getInt("is_favorite") != 0;
            long createdAt = rs.getLong("created_at");
            list.add(new ClipEntry(id, content, fav, createdAt));
        }
        return list;
    }

    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
}
