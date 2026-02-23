/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip;

public final class AppVersion {

    public static final String VERSION;

    static {
        String v = AppVersion.class.getPackage().getImplementationVersion();
        VERSION = (v != null) ? v : "DEV";
    }

    private AppVersion() {}
}
