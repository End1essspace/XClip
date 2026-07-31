/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
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
        String incoming = Objects.requireNonNull(incomingContent, "incomingContent");
        if (now < 0) throw new IllegalArgumentException("now cannot be negative");

        if (existing == null) {
            return Decision.CREATE_NEW_ENTRY;
        }

        if (!effectivePolicy.matches(existing.content(), incoming)) {
            return Decision.CREATE_NEW_ENTRY;
        }

        if (!effectivePolicy.withinDuplicateWindow(existing.lastCopiedAt(), now)) {
            return Decision.CREATE_NEW_ENTRY;
        }

        return switch (effectivePolicy.mutationFor(existing.pinned())) {
            case UPDATE_METADATA_MOVE_RECENT_TO_TOP ->
                    Decision.UPDATE_EXISTING_MOVE_RECENT_TO_TOP;
            case UPDATE_METADATA_PRESERVE_RECENT_POSITION ->
                    Decision.UPDATE_EXISTING_PRESERVE_RECENT_POSITION;
            case UPDATE_METADATA_PRESERVE_PIN_POSITION ->
                    Decision.UPDATE_EXISTING_PRESERVE_PIN_POSITION;
            case UPDATE_METADATA_MOVE_PIN_TO_TOP ->
                    Decision.UPDATE_EXISTING_MOVE_PIN_TO_TOP;
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
