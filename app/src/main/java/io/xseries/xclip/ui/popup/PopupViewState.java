/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.popup;

import io.xseries.xclip.domain.model.ClipContentType;
import io.xseries.xclip.domain.model.ClipViewScope;

/**
 * Immutable snapshot of popup filters.
 *
 * An immutable value prevents asynchronous reloads from observing a partially
 * updated scope/type pair while the user changes filters quickly.
 */
public record PopupViewState(
        ClipViewScope scope,
        ClipContentType contentType
) {
    public PopupViewState {
        scope = scope == null ? ClipViewScope.ALL : scope;
    }

    public static PopupViewState defaults() {
        return new PopupViewState(ClipViewScope.ALL, null);
    }

    public boolean filtersActive() {
        return scope != ClipViewScope.ALL || contentType != null;
    }
}
