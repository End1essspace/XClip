/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.data.model.ClipTag;
import io.xseries.xclip.domain.service.TagNamePolicy;
import io.xseries.xclip.domain.service.TagNamePolicy.NormalizedTagName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * JavaFX-free state model for the single-clip and multi-selection tag editor.
 *
 * Multi-selection semantics are deliberately non-destructive:
 * - ASSIGNED assigns the tag to every selected clip;
 * - UNASSIGNED removes it from every selected clip;
 * - MIXED preserves the existing per-clip differences.
 */
public final class TagEditorModel {

    public enum SelectionState {
        UNASSIGNED,
        ASSIGNED,
        MIXED
    }

    public enum AddResult {
        ADDED,
        SELECTED_EXISTING,
        ALREADY_ASSIGNED,
        ALREADY_PENDING
    }

    public record TagOption(
            long id,
            String name,
            SelectionState initialState,
            SelectionState currentState
    ) {}

    public record EditPlan(
            List<Long> assignTagIds,
            List<Long> removeTagIds,
            List<String> createAndAssignNames
    ) {
        public EditPlan {
            assignTagIds = List.copyOf(Objects.requireNonNull(assignTagIds, "assignTagIds"));
            removeTagIds = List.copyOf(Objects.requireNonNull(removeTagIds, "removeTagIds"));
            createAndAssignNames = List.copyOf(
                    Objects.requireNonNull(createAndAssignNames, "createAndAssignNames")
            );
        }

        public boolean isEmpty() {
            return assignTagIds.isEmpty()
                    && removeTagIds.isEmpty()
                    && createAndAssignNames.isEmpty();
        }
    }

    private static final Comparator<ClipTag> TAG_ORDER =
            Comparator.comparing(ClipTag::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingLong(ClipTag::id);

    private final List<Long> clipIds;
    private final LinkedHashMap<Long, MutableOption> options = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> pendingByIdentity = new LinkedHashMap<>();

    private TagEditorModel(List<Long> clipIds) {
        this.clipIds = clipIds;
    }

    public static TagEditorModel create(
            Collection<Long> rawClipIds,
            Collection<ClipTag> rawAllTags,
            Map<Long, ? extends Collection<ClipTag>> assignmentsByClip
    ) {
        List<Long> clipIds = uniquePositiveIds(rawClipIds);
        if (clipIds.isEmpty()) {
            throw new IllegalArgumentException("At least one clip is required");
        }

        Map<Long, ? extends Collection<ClipTag>> safeAssignments =
                assignmentsByClip == null ? Map.of() : assignmentsByClip;

        LinkedHashMap<Long, ClipTag> tagsById = new LinkedHashMap<>();
        if (rawAllTags != null) {
            rawAllTags.stream()
                    .filter(Objects::nonNull)
                    .sorted(TAG_ORDER)
                    .forEach(tag -> tagsById.putIfAbsent(tag.id(), tag));
        }
        for (Long clipId : clipIds) {
            Collection<ClipTag> assigned = safeAssignments.get(clipId);
            if (assigned == null) continue;
            assigned.stream()
                    .filter(Objects::nonNull)
                    .sorted(TAG_ORDER)
                    .forEach(tag -> tagsById.putIfAbsent(tag.id(), tag));
        }

        List<ClipTag> orderedTags = new ArrayList<>(tagsById.values());
        orderedTags.sort(TAG_ORDER);

        TagEditorModel model = new TagEditorModel(clipIds);
        for (ClipTag tag : orderedTags) {
            int assignedCount = 0;
            for (Long clipId : clipIds) {
                if (containsTag(safeAssignments.get(clipId), tag.id())) {
                    assignedCount++;
                }
            }

            SelectionState state = assignedCount == 0
                    ? SelectionState.UNASSIGNED
                    : assignedCount == clipIds.size()
                    ? SelectionState.ASSIGNED
                    : SelectionState.MIXED;

            model.options.put(
                    tag.id(),
                    new MutableOption(tag.id(), tag.name(), identity(tag.name()), state, state)
            );
        }
        return model;
    }

    public List<Long> clipIds() {
        return clipIds;
    }

    public int clipCount() {
        return clipIds.size();
    }

    public List<TagOption> options() {
        return options.values().stream()
                .map(MutableOption::snapshot)
                .toList();
    }

    public SelectionState state(long tagId) {
        return requireOption(tagId).currentState;
    }

    public void setState(long tagId, SelectionState state) {
        MutableOption option = requireOption(tagId);
        option.currentState = Objects.requireNonNull(state, "state");
    }

    public AddResult addPendingTag(String rawName) {
        NormalizedTagName normalized = TagNamePolicy.normalize(rawName);

        for (MutableOption option : options.values()) {
            if (!option.identity.equals(normalized.identity())) continue;

            if (option.currentState == SelectionState.ASSIGNED) {
                return AddResult.ALREADY_ASSIGNED;
            }
            option.currentState = SelectionState.ASSIGNED;
            return AddResult.SELECTED_EXISTING;
        }

        if (pendingByIdentity.containsKey(normalized.identity())) {
            return AddResult.ALREADY_PENDING;
        }

        pendingByIdentity.put(normalized.identity(), normalized.displayName());
        return AddResult.ADDED;
    }

    public boolean removePendingTag(String rawName) {
        if (rawName == null || rawName.isBlank()) return false;
        try {
            return pendingByIdentity.remove(TagNamePolicy.normalize(rawName).identity()) != null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public List<String> pendingTagNames() {
        return List.copyOf(pendingByIdentity.values());
    }

    public boolean hasChanges() {
        if (!pendingByIdentity.isEmpty()) return true;
        return options.values().stream()
                .anyMatch(option -> option.initialState != option.currentState);
    }

    public EditPlan plan() {
        List<Long> assign = new ArrayList<>();
        List<Long> remove = new ArrayList<>();

        for (MutableOption option : options.values()) {
            if (option.currentState == SelectionState.ASSIGNED
                    && option.initialState != SelectionState.ASSIGNED) {
                assign.add(option.id);
            } else if (option.currentState == SelectionState.UNASSIGNED
                    && option.initialState != SelectionState.UNASSIGNED) {
                remove.add(option.id);
            }
        }

        return new EditPlan(assign, remove, pendingTagNames());
    }

    private MutableOption requireOption(long tagId) {
        MutableOption option = options.get(tagId);
        if (option == null) {
            throw new IllegalArgumentException("Unknown tag id: " + tagId);
        }
        return option;
    }

    private static boolean containsTag(Collection<ClipTag> tags, long tagId) {
        if (tags == null || tags.isEmpty()) return false;
        for (ClipTag tag : tags) {
            if (tag != null && tag.id() == tagId) return true;
        }
        return false;
    }

    private static List<Long> uniquePositiveIds(Collection<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) return List.of();

        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : rawIds) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("clipIds must contain only positive ids");
            }
            unique.add(id);
        }
        return List.copyOf(unique);
    }

    private static String identity(String name) {
        return TagNamePolicy.normalize(name).identity();
    }

    private static final class MutableOption {
        private final long id;
        private final String name;
        private final String identity;
        private final SelectionState initialState;
        private SelectionState currentState;

        private MutableOption(
                long id,
                String name,
                String identity,
                SelectionState initialState,
                SelectionState currentState
        ) {
            this.id = id;
            this.name = name;
            this.identity = identity;
            this.initialState = initialState;
            this.currentState = currentState;
        }

        private TagOption snapshot() {
            return new TagOption(id, name, initialState, currentState);
        }
    }
}
