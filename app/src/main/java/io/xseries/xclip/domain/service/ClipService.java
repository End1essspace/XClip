package io.xseries.xclip.domain.service;

import io.xseries.xclip.config.Config;
import io.xseries.xclip.data.dao.ClipEntryDao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ClipService {

    private final ClipEntryDao dao;

    // Config-driven, can change at runtime (Settings -> Apply)
    private volatile int retentionLimit;
    private volatile int minClipLength;

    // Fast in-memory duplicate filter for back-to-back clipboard reads
    private final AtomicReference<String> lastNorm = new AtomicReference<>("");

    // Self-copy suppression (when popup copies an item)
    private static final long SELF_COPY_WINDOW_MS = 1500;
    private final AtomicReference<String> lastPushedNorm = new AtomicReference<>("");
    private final AtomicLong lastPushedAtMs = new AtomicLong(0);

    private final AtomicLong insertCounter = new AtomicLong(0);
    private static final ThreadLocal<MessageDigest> SHA256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    public ClipService(ClipEntryDao dao) {
        this(dao, 800, 0);
    }

    public ClipService(ClipEntryDao dao, int retentionLimit) {
        this(dao, retentionLimit, 0);
    }

    public ClipService(ClipEntryDao dao, int retentionLimit, int minClipLength) {
        this.dao = dao;
        this.retentionLimit = clampRetention(retentionLimit);
        this.minClipLength = clampMinLen(minClipLength);
    }

    /**
     * Apply runtime configuration (used by Settings window).
     * Must be safe to call any time, from any thread.
     */
    public void applyConfig(Config cfg) {
        if (cfg == null) return;
        this.retentionLimit = clampRetention(cfg.maxHistory());
        this.minClipLength = clampMinLen(cfg.minClipLength());
    }

    public void ingestText(String text) {
        if (text == null) return;

        String trimmed = text.trim();
        if (trimmed.isEmpty()) return;

        int minLen = this.minClipLength;
        if (minLen > 0 && trimmed.length() < minLen) return;

        // hard cap to avoid memory spikes
        if (trimmed.length() > 50_000) return;

        // whitespace normalization
        String norm = normalize(trimmed);
        if (norm.isEmpty()) return;

        // ignore self-copy (from XClip UI)
        if (isSelfCopy(norm)) return;

        // in-memory duplicate filtering (fast path)
        String prev = lastNorm.get();
        if (norm.equals(prev)) return;
        lastNorm.set(norm);

        String hash = sha256Hex(norm);
        long now = System.currentTimeMillis();

        // DB-level duplicate protection is inside dao.insert(...) (by content_hash)
        dao.insert(trimmed, norm, hash, now);

        // retention maintenance
        int limit = this.retentionLimit;
        if (insertCounter.incrementAndGet() % 10 == 0) {
            dao.pruneToLimit(limit);
        }
    }

    /**
     * Called from PopupWindow just before setting clipboard text.
     * This ensures watcher will ignore that upcoming clipboard event.
     */
    public void markPushedByApp(String textThatWillBeSetToClipboard) {
        if (textThatWillBeSetToClipboard == null) return;

        String trimmed = textThatWillBeSetToClipboard.trim();
        if (trimmed.isEmpty()) return;

        String norm = normalize(trimmed);
        if (norm.isEmpty()) return;

        lastPushedNorm.set(norm);
        lastPushedAtMs.set(System.currentTimeMillis());

        // Also set lastNorm to prevent immediate duplicate insert if timing slips
        lastNorm.set(norm);
    }

    private boolean isSelfCopy(String norm) {
        String pushed = lastPushedNorm.get();
        if (pushed == null || pushed.isEmpty()) return false;
        if (!pushed.equals(norm)) return false;

        long dt = System.currentTimeMillis() - lastPushedAtMs.get();
        return dt >= 0 && dt <= SELF_COPY_WINDOW_MS;
    }

    /**
     * Normalize whitespace:
     * - trim edges
     * - collapse any whitespace run (space/tab/newline) into a single space
     * This avoids regex cost.
     */
    private String normalize(String s) {
        int n = s.length();
        StringBuilder sb = new StringBuilder(n);

        boolean inWs = false;
        for (int i = 0; i < n; i++) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) {
                inWs = true;
                continue;
            }
            if (inWs && sb.length() > 0) sb.append(' ');
            sb.append(ch);
            inWs = false;
        }
        return sb.toString().trim();
    }

    private String sha256Hex(String s) {
        try {
            MessageDigest md = SHA256.get();
            md.reset();
            byte[] bytes = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int clampRetention(int v) {
        if (v < 100) return 100;
        if (v > 50_000) return 50_000;
        return v;
    }

    private static int clampMinLen(int v) {
        if (v < 0) return 0;
        if (v > 10_000) return 10_000;
        return v;
    }
}
