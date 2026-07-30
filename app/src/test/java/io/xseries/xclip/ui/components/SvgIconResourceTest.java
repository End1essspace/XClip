/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.components;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SvgIconResourceTest {

    private static final List<String> REQUIRED_ICONS = List.of(
            "braces",
            "check-check",
            "check",
            "chevron-down",
            "circle-question-mark",
            "clipboard-paste",
            "code-xml",
            "copy",
            "ellipsis-vertical",
            "ellipsis",
            "external-link",
            "folder-open",
            "funnel",
            "list",
            "minus",
            "pause",
            "pencil",
            "pin-off",
            "pin",
            "play",
            "plus",
            "rotate-ccw-clock",
            "rotate-ccw",
            "search",
            "settings",
            "square",
            "tag",
            "tags",
            "terminal",
            "trash-2",
            "x",
            "zap"
    );

    @Test
    void requiredLucideResourcesArePresentAndWellFormed() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);

        for (String iconName : REQUIRED_ICONS) {
            String resourcePath = "/icons/ui/" + iconName + ".svg";

            try (InputStream stream = SvgIconResourceTest.class.getResourceAsStream(resourcePath)) {
                assertNotNull(stream, () -> "Missing UI icon: " + resourcePath);

                Document document = factory.newDocumentBuilder().parse(stream);
                Element root = document.getDocumentElement();

                assertNotNull(root, () -> "Missing SVG root: " + resourcePath);
                assertTrue(
                        "svg".equalsIgnoreCase(root.getTagName()),
                        () -> "Unexpected root element in " + resourcePath
                );
                assertTrue(
                        hasSupportedShape(root),
                        () -> "No supported Lucide shape found in " + resourcePath
                );
            }
        }
    }

    private boolean hasSupportedShape(Element root) {
        return root.getElementsByTagName("path").getLength() > 0
                || root.getElementsByTagName("line").getLength() > 0
                || root.getElementsByTagName("circle").getLength() > 0
                || root.getElementsByTagName("ellipse").getLength() > 0
                || root.getElementsByTagName("rect").getLength() > 0
                || root.getElementsByTagName("polyline").getLength() > 0
                || root.getElementsByTagName("polygon").getLength() > 0;
    }
}
