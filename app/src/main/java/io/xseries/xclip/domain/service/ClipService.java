/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.service;

import io.xseries.xclip.config.Config;
import io.xseries.xclip.data.dao.ClipEntryDao;
import io.xseries.xclip.domain.duplicate.DuplicateBehaviorPolicy;
import io.xseries.xclip.domain.duplicate.DuplicateContentKeys;
import io.xseries.xclip.domain.duplicate.DuplicatePolicyEngine;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ClipService {

    private final ClipEntryDao dao;

    private volatile int retentionLimit;
    private volatile int minClipLength = 0;
    private volatile int maxClipChars = Config.DEFAULT_MAX_CLIP_CHARS;
    private volatile DuplicateBehaviorPolicy duplicatePolicy = DuplicateBehaviorPolicy.defaults();

    // Self-copy suppression (when popup copies an item).
    private static final long SELF_COPY_WINDOW_MS = 1500;
    private final AtomicReference<String> lastPushedDuplicateHash = new AtomicReference<>("");
    private final AtomicLong lastPushedAtMs = new AtomicLong(0);

    private final AtomicLong insertCounter = new AtomicLong(0);

    public ClipService(ClipEntryDao dao) {
        this.dao = dao;
        this.retentionLimit = clampRetention(800);
        this.minClipLength = clampMinLen(0);
    }

    /**
     * Applies capture and duplicate behavior atomically from one normalized config snapshot.
     */
    public void applyConfig(Config cfg) {
        if (cfg == null) return;

        DuplicateBehaviorPolicy nextPolicy = cfg.duplicateBehaviorPolicy();
        boolean policyChanged = !nextPolicy.equals(this.duplicatePolicy);

        this.retentionLimit = clampRetention(cfg.maxHistory());
        this.minClipLength = clampMinLen(cfg.minClipLength());
        this.maxClipChars = clampMaxClipChars(cfg.maxClipChars());
        this.duplicatePolicy = nextPolicy;

        if (policyChanged) {
            lastPushedDuplicateHash.set("");
            lastPushedAtMs.set(0);
        }
    }

    public void ingestText(String text) {
        ingestTextAt(text, System.currentTimeMillis());
    }

    void ingestTextAt(String text, long now) {
        if (text == null || now < 0) return;

        DuplicateBehaviorPolicy policy = duplicatePolicy;
        String captured = prepareCapturedContent(text, policy);
        if (captured == null) return;

        String contentNorm = normalize(captured);
        if (contentNorm.isEmpty()) return;

        DuplicateContentKeys keys = DuplicateContentKeys.from(captured);
        String selectedHash = keys.selectedHash(policy);

        if (isSelfCopy(selectedHash, now)) return;

        long cutoff = duplicateCutoff(policy, now);
        List<ClipEntryDao.DuplicateCandidate> candidates = dao.findDuplicateCandidates(
                keys.selectedKind(policy),
                selectedHash,
                cutoff
        );

        for (ClipEntryDao.DuplicateCandidate candidate : candidates) {
            DuplicatePolicyEngine.Decision decision = DuplicatePolicyEngine.evaluate(
                    policy,
                    new DuplicatePolicyEngine.ExistingClip(
                            candidate.content(),
                            candidate.pinned(),
                            candidate.lastCopiedAt()
                    ),
                    captured,
                    now
            );

            if (!decision.duplicate()) continue;

            if (dao.applyDuplicate(
                    candidate.id(),
                    captured,
                    contentNorm,
                    keys,
                    now,
                    decision
            )) {
                maintainRetention();
                return;
            }
        }

        dao.insertNew(captured, contentNorm, keys, now);
        maintainRetention();
    }

    /**
     * Called immediately before XClip writes text to the system clipboard.
     */
    public void markPushedByApp(String textThatWillBeSetToClipboard) {
        if (textThatWillBeSetToClipboard == null) return;

        DuplicateBehaviorPolicy policy = duplicatePolicy;
        String captured = prepareCapturedContent(textThatWillBeSetToClipboard, policy);
        if (captured == null) return;

        String hash = DuplicateContentKeys.from(captured).selectedHash(policy);
        lastPushedDuplicateHash.set(hash);
        lastPushedAtMs.set(System.currentTimeMillis());
    }

    private String prepareCapturedContent(
            String source,
            DuplicateBehaviorPolicy policy
    ) {
        if (source.isBlank()) return null;

        String meaningful = source.trim();
        int minLen = this.minClipLength;
        if (minLen > 0 && meaningful.length() < minLen) return null;

        boolean preserveCharacters = policy.exactContentMode()
                || policy.whitespaceMode() == DuplicateBehaviorPolicy.WhitespaceMode.PRESERVE;
        String captured = preserveCharacters ? source : meaningful;

        int cap = this.maxClipChars;
        if (cap > 0 && captured.length() > cap) {
            captured = captured.substring(0, cap);
        }
        return captured.isBlank() ? null : captured;
    }

    private boolean isSelfCopy(String hash, long now) {
        String pushed = lastPushedDuplicateHash.get();
        if (pushed == null || pushed.isEmpty() || !pushed.equals(hash)) return false;

        long dt = now - lastPushedAtMs.get();
        return dt >= 0 && dt <= SELF_COPY_WINDOW_MS;
    }

    private long duplicateCutoff(DuplicateBehaviorPolicy policy, long now) {
        long window = policy.duplicateWindowMillis();
        if (window == DuplicateBehaviorPolicy.UNLIMITED_WINDOW) return 0L;
        return window >= now ? 0L : now - window;
    }

    private void maintainRetention() {
        int limit = this.retentionLimit;
        if (insertCounter.incrementAndGet() % 10 == 0) {
            dao.pruneToLimit(limit);
        }
    }

    private String normalize(String s) {
        return DuplicateBehaviorPolicy.normalizeWhitespace(s);
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

    private static int clampMaxClipChars(int v) {
        if (v < 10_000) return 10_000;
        if (v > 5_000_000) return 5_000_000;
        return v;
    }
}
