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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SvgIconResourceTest {

    @Test
    void typedRegistryHasUniqueResourceNames() {
        Set<String> names = new HashSet<>();

        for (UiIcon icon : UiIcon.values()) {
            assertTrue(
                    names.add(icon.resourceName()),
                    () -> "Duplicate UI icon resource name: " + icon.resourceName()
            );
            assertEquals(
                    "/icons/ui/" + icon.resourceName() + ".svg",
                    icon.resourcePath()
            );
        }

        assertTrue(UiIcon.values().length >= 32, "Unexpectedly small Lucide registry");
    }

    @Test
    void everyTypedLucideResourceIsPackagedVectorOnlyAndRenderable() throws Exception {
        DocumentBuilderFactory factory = secureFactory();

        for (UiIcon icon : UiIcon.values()) {
            String resourcePath = icon.resourcePath();
            byte[] bytes;

            try (InputStream stream = SvgIconResourceTest.class.getResourceAsStream(resourcePath)) {
                assertNotNull(stream, () -> "Missing packaged UI icon: " + resourcePath);
                bytes = stream.readAllBytes();
            }

            assertTrue(bytes.length > 0, () -> "Empty packaged UI icon: " + resourcePath);

            String source = new String(bytes, StandardCharsets.UTF_8);
            String lower = source.toLowerCase(Locale.ROOT);
            assertFalse(lower.contains("<image"), () -> "Raster image embedded in " + resourcePath);
            assertFalse(lower.contains("<script"), () -> "Script embedded in " + resourcePath);
            assertFalse(lower.contains("<foreignobject"), () -> "foreignObject embedded in " + resourcePath);
            assertFalse(lower.contains("<!doctype"), () -> "DOCTYPE embedded in " + resourcePath);

            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            Element root = document.getDocumentElement();

            assertNotNull(root, () -> "Missing SVG root: " + resourcePath);
            assertTrue(
                    "svg".equalsIgnoreCase(root.getTagName()),
                    () -> "Unexpected root element in " + resourcePath
            );
            assertLucideViewBox(root, resourcePath);
            assertTrue(
                    hasSupportedShape(root),
                    () -> "No supported vector shape found in " + resourcePath
            );
        }
    }

    @Test
    void lucideAttributionIsPackagedWithTheApplication() throws Exception {
        String notice = readResource("/META-INF/THIRD-PARTY-NOTICES.txt");
        String license = readResource("/META-INF/licenses/LUCIDE-ISC.txt");

        assertTrue(notice.contains("Lucide Icons"));
        assertTrue(notice.contains("ISC"));
        assertTrue(license.contains("ISC License"));
        assertTrue(license.contains("Lucide Icons and Contributors"));
        assertTrue(license.contains("Permission to use, copy, modify"));
    }

    private DocumentBuilderFactory secureFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setExpandEntityReferences(false);
        try {
            factory.setXIncludeAware(false);
        } catch (UnsupportedOperationException ignored) {
        }
        return factory;
    }

    private void setFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception e) {
            throw new IllegalStateException("Required XML hardening is unavailable: " + feature, e);
        }
    }

    private void assertLucideViewBox(Element root, String resourcePath) {
        String[] values = root.getAttribute("viewBox").trim().split("[\\s,]+");
        assertEquals(4, values.length, () -> "Invalid viewBox in " + resourcePath);

        double[] parsed = Arrays.stream(values).mapToDouble(Double::parseDouble).toArray();
        assertEquals(0.0, parsed[0], () -> "Unexpected viewBox minX in " + resourcePath);
        assertEquals(0.0, parsed[1], () -> "Unexpected viewBox minY in " + resourcePath);
        assertEquals(24.0, parsed[2], () -> "Unexpected viewBox width in " + resourcePath);
        assertEquals(24.0, parsed[3], () -> "Unexpected viewBox height in " + resourcePath);
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

    private String readResource(String resourcePath) throws Exception {
        try (InputStream stream = SvgIconResourceTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(stream, () -> "Missing packaged notice: " + resourcePath);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
