/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (End1essspace | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;

/**
 * Immutable snapshot of popup filters.
 *
 * An immutable value prevents asynchronous reloads from observing a partially
 * updated scope/type/tag combination while the user changes filters quickly.
 */
public record PopupViewState(
        ClipViewScope scope,
        ClipContentType contentType,
        Long tagId
) {
    public PopupViewState {
        scope = scope == null ? ClipViewScope.ALL : scope;
        if (tagId != null && tagId <= 0) {
            throw new IllegalArgumentException("tagId must be positive");
        }
    }

    public PopupViewState(ClipViewScope scope, ClipContentType contentType) {
        this(scope, contentType, null);
    }

    public static PopupViewState defaults() {
        return new PopupViewState(ClipViewScope.ALL, null, null);
    }

    public boolean filtersActive() {
        return scope != ClipViewScope.ALL || contentType != null || tagId != null;
    }
}
