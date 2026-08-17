/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Pure presentation model for the popup tag filter.
 *
 * An empty tag library is represented by one non-interactive "No tags" value.
 * Once tags exist, the first option becomes "All tags" and every following
 * option contains only the user-defined display name.
 */
public final class TagFilterModel {

    static final String NO_TAGS_LABEL = "No tags";
    static final String ALL_TAGS_LABEL = "All tags";

    private TagFilterModel() {}

    public static Snapshot build(List<ClipTag> tags, Long requestedTagId) {
        LinkedHashMap<Long, ClipTag> availableById = new LinkedHashMap<>();
        if (tags != null) {
            for (ClipTag tag : tags) {
                if (tag == null || tag.id() <= 0 || tag.name() == null || tag.name().isBlank()) {
                    continue;
                }
                availableById.putIfAbsent(tag.id(), tag);
            }
        }

        if (availableById.isEmpty()) {
            return new Snapshot(
                    List.of(new Option(null, NO_TAGS_LABEL)),
                    false,
                    null
            );
        }

        List<Option> options = new ArrayList<>(availableById.size() + 1);
        options.add(new Option(null, ALL_TAGS_LABEL));
        for (ClipTag tag : availableById.values()) {
            options.add(new Option(tag.id(), tag.name().trim()));
        }

        Long selectedTagId = requestedTagId != null
                && availableById.containsKey(requestedTagId)
                ? requestedTagId
                : null;
        return new Snapshot(options, true, selectedTagId);
    }

    public record Option(Long tagId, String label) {
        public Option {
            if (tagId != null && tagId <= 0) {
                throw new IllegalArgumentException("tagId must be positive");
            }
            label = Objects.requireNonNullElse(label, "").trim();
            if (label.isEmpty()) {
                throw new IllegalArgumentException("label is required");
            }
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record Snapshot(
            List<Option> options,
            boolean available,
            Long selectedTagId
    ) {
        public Snapshot {
            options = List.copyOf(Objects.requireNonNull(options, "options"));
            if (options.isEmpty()) {
                throw new IllegalArgumentException("options cannot be empty");
            }
            if (!available && selectedTagId != null) {
                throw new IllegalArgumentException(
                        "An unavailable tag filter cannot keep a selected tag"
                );
            }
        }

        public Option selectedOption() {
            return options.stream()
                    .filter(option -> Objects.equals(option.tagId(), selectedTagId))
                    .findFirst()
                    .orElse(options.get(0));
        }
    }
}
