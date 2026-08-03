/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.domain.duplicate;

import java.util.Objects;

/**
 * Pure decision engine for one incoming clipboard value.
 *
 * The engine does not read or mutate persistence. It evaluates an already
 * selected existing candidate and returns the exact mutation intent.
 */
public final class DuplicatePolicyEngine {

    private DuplicatePolicyEngine() {}

    public static Decision evaluate(
            DuplicateBehaviorPolicy policy,
            ExistingClip existing,
            String incomingContent,
            long now
    ) {
        DuplicateBehaviorPolicy effectivePolicy =
                Objects.requireNonNull(policy, "policy");
        if (existing == null) {
            if (now < 0) throw new IllegalArgumentException("now cannot be negative");
            Objects.requireNonNull(incomingContent, "incomingContent");
            return Decision.CREATE_NEW_ENTRY;
        }

        return evaluateCanonical(
                effectivePolicy,
                existing.content(),
                existing.pinned(),
                existing.lastCopiedAt(),
                effectivePolicy.canonicalKey(
                        Objects.requireNonNull(incomingContent, "incomingContent")
                ),
                now
        );
    }

    /**
     * Evaluates a candidate against an incoming canonical key prepared once for
     * the complete lookup result.
     *
     * The public evaluate(...) contract remains unchanged. The ingest hot path
     * uses this overload to avoid allocating an ExistingClip record and
     * re-normalizing the same incoming text for every candidate.
     */
    public static Decision evaluateCanonical(
            DuplicateBehaviorPolicy policy,
            String existingContent,
            boolean existingPinned,
            long existingLastCopiedAt,
            String incomingCanonicalKey,
            long now
    ) {
        DuplicateBehaviorPolicy effectivePolicy =
                Objects.requireNonNull(policy, "policy");
        String existing = Objects.requireNonNull(existingContent, "existingContent");
        String incomingKey = Objects.requireNonNull(
                incomingCanonicalKey,
                "incomingCanonicalKey"
        );

        if (existingLastCopiedAt < 0 || now < 0) {
            throw new IllegalArgumentException("timestamps cannot be negative");
        }

        if (!effectivePolicy.canonicalKey(existing).equals(incomingKey)) {
            return Decision.CREATE_NEW_ENTRY;
        }

        if (!effectivePolicy.withinDuplicateWindow(existingLastCopiedAt, now)) {
            return Decision.CREATE_NEW_ENTRY;
        }

        if (existingPinned) {
            return switch (effectivePolicy.pinnedDuplicatePosition()) {
                case PRESERVE_PIN_POSITION ->
                        Decision.UPDATE_EXISTING_PRESERVE_PIN_POSITION;
                case MOVE_PIN_TO_TOP ->
                        Decision.UPDATE_EXISTING_MOVE_PIN_TO_TOP;
            };
        }

        return switch (effectivePolicy.recentDuplicatePosition()) {
            case MOVE_TO_TOP ->
                    Decision.UPDATE_EXISTING_MOVE_RECENT_TO_TOP;
            case PRESERVE_EXISTING_POSITION ->
                    Decision.UPDATE_EXISTING_PRESERVE_RECENT_POSITION;
        };
    }

    public record ExistingClip(
            String content,
            boolean pinned,
            long lastCopiedAt
    ) {
        public ExistingClip {
            content = Objects.requireNonNull(content, "content");
            if (lastCopiedAt < 0) {
                throw new IllegalArgumentException("lastCopiedAt cannot be negative");
            }
        }
    }

    public enum Decision {
        CREATE_NEW_ENTRY(false, false, false),
        UPDATE_EXISTING_MOVE_RECENT_TO_TOP(true, true, false),
        UPDATE_EXISTING_PRESERVE_RECENT_POSITION(true, false, false),
        UPDATE_EXISTING_PRESERVE_PIN_POSITION(true, true, false),
        UPDATE_EXISTING_MOVE_PIN_TO_TOP(true, true, true);

        private final boolean duplicate;
        private final boolean updateLastCopiedAt;
        private final boolean movePinnedToTop;

        Decision(
                boolean duplicate,
                boolean updateLastCopiedAt,
                boolean movePinnedToTop
        ) {
            this.duplicate = duplicate;
            this.updateLastCopiedAt = updateLastCopiedAt;
            this.movePinnedToTop = movePinnedToTop;
        }

        public boolean duplicate() {
            return duplicate;
        }

        public boolean updateLastCopiedAt() {
            return updateLastCopiedAt;
        }

        public boolean movePinnedToTop() {
            return movePinnedToTop;
        }
    }
}
