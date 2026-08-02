

/*
 * XClip — Windows Clipboard Manager
 * Copyright (C) 2026 Rafael Xudoynazarov (XCON | RX)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.xseries.xclip.ui.components;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight JavaFX renderer for the original Lucide SVG resources bundled
 * under {@code /icons/ui/}.
 *
 * Lucide icons are parsed once into immutable shape specifications. Every UI
 * use receives fresh JavaFX nodes, so icons can be safely reused by virtualized
 * cells and styled through the {@code -x-icon-color} looked-up CSS color.
 */
public final class SvgIcon extends StackPane {

    private static final Map<UiIcon, IconDefinition> CACHE = new ConcurrentHashMap<>();

    private final UiIcon icon;
    private final double iconSize;

    private SvgIcon(UiIcon icon, double iconSize, String... extraStyleClasses) {
        this.icon = Objects.requireNonNull(icon, "icon");
        this.iconSize = normalizeSize(iconSize);

        getStyleClass().add("svg-icon");
        if (extraStyleClasses != null) {
            for (String styleClass : extraStyleClasses) {
                if (styleClass != null && !styleClass.isBlank()) {
                    getStyleClass().add(styleClass);
                }
            }
        }

        setMinSize(this.iconSize, this.iconSize);
        setPrefSize(this.iconSize, this.iconSize);
        setMaxSize(this.iconSize, this.iconSize);
        setMouseTransparent(true);
        setFocusTraversable(false);
        setPickOnBounds(false);

        IconDefinition definition = CACHE.computeIfAbsent(
                this.icon,
                SvgIcon::loadDefinition
        );
        getChildren().setAll(buildGraphic(definition, this.iconSize));
    }

    public static SvgIcon of(UiIcon icon, double iconSize, String... extraStyleClasses) {
        return new SvgIcon(icon, iconSize, extraStyleClasses);
    }

    private static Node buildGraphic(IconDefinition definition, double requestedSize) {
        List<Node> nodes = new ArrayList<>(definition.shapes().size());
        for (ShapeSpec spec : definition.shapes()) {
            Shape shape = createShape(spec, definition.defaults());
            if (shape != null) nodes.add(shape);
        }

        if (nodes.isEmpty()) {
            throw new IllegalStateException("SVG icon has no renderable vector shapes");
        }

        Group graphic = new Group(nodes);
        double scale = requestedSize / Math.max(definition.viewWidth(), definition.viewHeight());
        graphic.setScaleX(scale);
        graphic.setScaleY(scale);
        graphic.setMouseTransparent(true);
        return graphic;
    }

    private static Shape createShape(ShapeSpec spec, Map<String, String> defaults) {
        Map<String, String> attrs = merged(defaults, spec.attributes());
        Shape shape = switch (spec.tag()) {
            case "path" -> createPath(attrs);
            case "line" -> new Line(
                    number(attrs, "x1", 0),
                    number(attrs, "y1", 0),
                    number(attrs, "x2", 0),
                    number(attrs, "y2", 0)
            );
            case "circle" -> new Circle(
                    number(attrs, "cx", 0),
                    number(attrs, "cy", 0),
                    number(attrs, "r", 0)
            );
            case "ellipse" -> new Ellipse(
                    number(attrs, "cx", 0),
                    number(attrs, "cy", 0),
                    number(attrs, "rx", 0),
                    number(attrs, "ry", 0)
            );
            case "rect" -> createRectangle(attrs);
            case "polyline" -> createPolyline(attrs);
            case "polygon" -> createPolygon(attrs);
            default -> null;
        };

        if (shape == null) return null;

        String fill = attrs.getOrDefault("fill", "none");
        boolean filled = !fill.isBlank() && !"none".equalsIgnoreCase(fill);

        if (filled) {
            shape.setFill(Color.WHITE);
            shape.setStroke(Color.TRANSPARENT);
            shape.getStyleClass().add("svg-icon-fill");
        } else {
            shape.setFill(Color.TRANSPARENT);
            shape.setStroke(Color.WHITE);
            shape.setStrokeWidth(number(attrs, "stroke-width", 2.0));
            shape.setStrokeLineCap(parseLineCap(attrs.get("stroke-linecap")));
            shape.setStrokeLineJoin(parseLineJoin(attrs.get("stroke-linejoin")));
            shape.getStyleClass().add("svg-icon-stroke");
        }

        shape.setMouseTransparent(true);
        return shape;
    }

    private static SVGPath createPath(Map<String, String> attrs) {
        String data = attrs.get("d");
        if (data == null || data.isBlank()) return null;

        SVGPath path = new SVGPath();
        path.setContent(data);
        return path;
    }

    private static Rectangle createRectangle(Map<String, String> attrs) {
        Rectangle rectangle = new Rectangle(
                number(attrs, "x", 0),
                number(attrs, "y", 0),
                number(attrs, "width", 0),
                number(attrs, "height", 0)
        );

        double rx = number(attrs, "rx", 0);
        double ry = number(attrs, "ry", rx);
        if (rx > 0) rectangle.setArcWidth(rx * 2.0);
        if (ry > 0) rectangle.setArcHeight(ry * 2.0);
        return rectangle;
    }

    private static Polyline createPolyline(Map<String, String> attrs) {
        Polyline polyline = new Polyline();
        polyline.getPoints().setAll(parsePoints(attrs.get("points")));
        return polyline;
    }

    private static Polygon createPolygon(Map<String, String> attrs) {
        Polygon polygon = new Polygon();
        polygon.getPoints().setAll(parsePoints(attrs.get("points")));
        return polygon;
    }

    private static List<Double> parsePoints(String raw) {
        if (raw == null || raw.isBlank()) return List.of();

        String[] tokens = raw.trim().split("[\\s,]+");
        List<Double> values = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            if (token.isBlank()) continue;
            try {
                values.add(Double.parseDouble(token));
            } catch (NumberFormatException ignored) {
                return List.of();
            }
        }
        return values;
    }

    private static IconDefinition loadDefinition(UiIcon icon) {
        String resourcePath = icon.resourcePath();

        try (InputStream stream = SvgIcon.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing packaged UI icon: " + resourcePath);
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            disableExternalXmlFeatures(factory);

            Document document = factory.newDocumentBuilder().parse(stream);
            Element root = document.getDocumentElement();
            if (root == null || !"svg".equalsIgnoreCase(root.getTagName())) {
                throw new IllegalStateException("Invalid SVG root: " + resourcePath);
            }

            ViewBox viewBox = parseViewBox(root.getAttribute("viewBox"), resourcePath);
            Map<String, String> defaults = attributes(root);
            List<ShapeSpec> shapes = new ArrayList<>();
            collectShapeSpecs(root.getChildNodes(), shapes);
            if (shapes.isEmpty()) {
                throw new IllegalStateException("SVG icon has no supported shapes: " + resourcePath);
            }

            return new IconDefinition(
                    viewBox.width(),
                    viewBox.height(),
                    Map.copyOf(defaults),
                    List.copyOf(shapes)
            );
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load packaged UI icon: " + resourcePath, e);
        }
    }

    private static void collectShapeSpecs(NodeList nodes, List<ShapeSpec> out) {
        for (int i = 0; i < nodes.getLength(); i++) {
            org.w3c.dom.Node node = nodes.item(i);
            if (!(node instanceof Element element)) continue;

            String tag = element.getTagName().toLowerCase(Locale.ROOT);
            if ("g".equals(tag) || "svg".equals(tag)) {
                collectShapeSpecs(element.getChildNodes(), out);
                continue;
            }

            if (switch (tag) {
                case "path", "line", "circle", "ellipse", "rect", "polyline", "polygon" -> true;
                default -> false;
            }) {
                out.add(new ShapeSpec(tag, Map.copyOf(attributes(element))));
            }
        }
    }

    private static Map<String, String> attributes(Element element) {
        Map<String, String> attrs = new LinkedHashMap<>();
        var named = element.getAttributes();
        for (int i = 0; i < named.getLength(); i++) {
            org.w3c.dom.Node item = named.item(i);
            attrs.put(item.getNodeName(), item.getNodeValue());
        }
        return attrs;
    }

    private static Map<String, String> merged(
            Map<String, String> defaults,
            Map<String, String> overrides
    ) {
        if (defaults.isEmpty()) return overrides;
        if (overrides.isEmpty()) return defaults;

        Map<String, String> result = new LinkedHashMap<>(defaults);
        result.putAll(overrides);
        return result;
    }

    private static ViewBox parseViewBox(String raw, String resourcePath) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Missing SVG viewBox: " + resourcePath);
        }

        String[] parts = raw.trim().split("[\\s,]+");
        if (parts.length != 4) {
            throw new IllegalStateException("Invalid SVG viewBox: " + resourcePath);
        }

        try {
            double minX = Double.parseDouble(parts[0]);
            double minY = Double.parseDouble(parts[1]);
            double width = Double.parseDouble(parts[2]);
            double height = Double.parseDouble(parts[3]);

            if (!Double.isFinite(minX) || !Double.isFinite(minY)
                    || !Double.isFinite(width) || width <= 0
                    || !Double.isFinite(height) || height <= 0) {
                throw new IllegalStateException("Invalid SVG viewBox values: " + resourcePath);
            }
            if (minX != 0.0 || minY != 0.0) {
                throw new IllegalStateException("Unsupported non-zero SVG viewBox origin: " + resourcePath);
            }
            return new ViewBox(width, height);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid SVG viewBox numbers: " + resourcePath, e);
        }
    }

    private static StrokeLineCap parseLineCap(String value) {
        if (value == null) return StrokeLineCap.ROUND;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "butt" -> StrokeLineCap.BUTT;
            case "square" -> StrokeLineCap.SQUARE;
            default -> StrokeLineCap.ROUND;
        };
    }

    private static StrokeLineJoin parseLineJoin(String value) {
        if (value == null) return StrokeLineJoin.ROUND;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "bevel" -> StrokeLineJoin.BEVEL;
            case "miter" -> StrokeLineJoin.MITER;
            default -> StrokeLineJoin.ROUND;
        };
    }

    private static double number(Map<String, String> attrs, String name, double fallback) {
        String raw = attrs.get(name);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            double value = Double.parseDouble(raw);
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double normalizeSize(double iconSize) {
        if (!Double.isFinite(iconSize) || iconSize <= 0) return 16.0;
        return iconSize;
    }

    private static void disableExternalXmlFeatures(DocumentBuilderFactory factory) {
        setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            factory.setXIncludeAware(false);
        } catch (UnsupportedOperationException ignored) {
        }
        factory.setExpandEntityReferences(false);
    }

    private static void setFeature(
            DocumentBuilderFactory factory,
            String feature,
            boolean value
    ) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
        }
    }

    private record ViewBox(double width, double height) {}

    private record ShapeSpec(String tag, Map<String, String> attributes) {}

    private record IconDefinition(
            double viewWidth,
            double viewHeight,
            Map<String, String> defaults,
            List<ShapeSpec> shapes
    ) {}
}
