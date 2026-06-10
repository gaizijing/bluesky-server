package com.bluesky.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 从 GeoJSON 计算经纬度包络（west, south, east, north）。
 */
public final class GeoJsonEnvelope {

    public record Envelope(double west, double south, double east, double north) {}

    private GeoJsonEnvelope() {}

    public static Envelope parse(String geoJson, ObjectMapper objectMapper) {
        try {
            JsonNode root = objectMapper.readTree(geoJson);
            return parse(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("GeoJSON 解析失败: " + e.getMessage(), e);
        }
    }

    public static Envelope parse(JsonNode root) {
        double[] box = new double[] { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY };
        String type = root.path("type").asText("");
        switch (type) {
            case "FeatureCollection" -> {
                for (JsonNode feature : root.withArray("features")) {
                    walkGeometry(feature.path("geometry"), box);
                }
            }
            case "Feature" -> walkGeometry(root.path("geometry"), box);
            default -> walkGeometry(root, box);
        }
        if (!Double.isFinite(box[0])) {
            throw new IllegalArgumentException("GeoJSON 中未找到有效坐标");
        }
        return new Envelope(box[0], box[1], box[2], box[3]);
    }

    private static void walkGeometry(JsonNode geometry, double[] box) {
        if (geometry == null || geometry.isMissingNode()) {
            return;
        }
        String type = geometry.path("type").asText("");
        JsonNode coordinates = geometry.get("coordinates");
        if (coordinates == null) {
            return;
        }
        switch (type) {
            case "Point" -> extendBox(box, coordinates.get(0).asDouble(), coordinates.get(1).asDouble());
            case "MultiPoint", "LineString" -> walkCoordList(coordinates, box, false);
            case "MultiLineString", "Polygon" -> walkCoordList(coordinates, box, true);
            case "MultiPolygon" -> {
                for (JsonNode polygon : coordinates) {
                    walkCoordList(polygon, box, true);
                }
            }
            case "GeometryCollection" -> {
                for (JsonNode g : geometry.withArray("geometries")) {
                    walkGeometry(g, box);
                }
            }
            default -> throw new IllegalArgumentException("不支持的 GeoJSON 类型: " + type);
        }
    }

    private static void walkCoordList(JsonNode node, double[] box, boolean isPolygonRing) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return;
        }
        JsonNode first = node.get(0);
        if (first.isNumber()) {
            extendBox(box, first.asDouble(), node.get(1).asDouble());
            return;
        }
        for (JsonNode child : node) {
            walkCoordList(child, box, isPolygonRing);
        }
    }

    private static void extendBox(double[] box, double lng, double lat) {
        box[0] = Math.min(box[0], lng);
        box[1] = Math.min(box[1], lat);
        box[2] = Math.max(box[2], lng);
        box[3] = Math.max(box[3], lat);
    }
}
