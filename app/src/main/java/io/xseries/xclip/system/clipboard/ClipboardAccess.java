
/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.system.clipboard;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

public final class ClipboardAccess {

    public String getTextSafely() {
        try {
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            var contents = clipboard.getContents(null);
            if (contents == null) return null;
            if (!contents.isDataFlavorSupported(DataFlavor.stringFlavor)) return null;

            Object data = contents.getTransferData(DataFlavor.stringFlavor);
            return (data instanceof String s) ? s : null;

        } catch (IllegalStateException busy) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean setTextSafely(String text) {
        if (text == null) return false;

        try {
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
