package com.bluesky.util;

import com.bluesky.exception.BusinessException;
import com.bluesky.common.ResultCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GeoUtil {

    private GeoUtil() {
    }

    public static double haversineKm(double lon1, double lat1, double lon2, double lat2) {
        double dx = (lon2 - lon1) * 111.32;
        double dy = (lat2 - lat1) * 110.57;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static double pathLengthKm(List<double[]> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            return 0d;
        }
        double total = 0d;
        for (int i = 0; i < coordinates.size() - 1; i++) {
            double[] start = coordinates.get(i);
            double[] end = coordinates.get(i + 1);
            total += haversineKm(start[0], start[1], end[0], end[1]);
        }
        return total;
    }

    @SuppressWarnings("unchecked")
    public static List<double[]> parseLineCoordinates(Map<String, Object> geoJson) {
        if (geoJson == null || geoJson.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "GeoJSON 不能为空");
        }

        String type = String.valueOf(geoJson.get("type"));
        return switch (type) {
            case "LineString" -> parseCoordinateList((List<?>) geoJson.get("coordinates"));
            case "Feature" -> parseLineCoordinates((Map<String, Object>) geoJson.get("geometry"));
            case "FeatureCollection" -> parseFeatureCollection(geoJson);
            default -> throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的 GeoJSON 类型: " + type);
        };
    }

    @SuppressWarnings("unchecked")
    private static List<double[]> parseFeatureCollection(Map<String, Object> geoJson) {
        Object featuresObj = geoJson.get("features");
        if (!(featuresObj instanceof List<?> features) || features.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "FeatureCollection 缺少 features");
        }
        Object first = features.get(0);
        if (!(first instanceof Map<?, ?> featureMap)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "FeatureCollection 格式无效");
        }
        return parseLineCoordinates((Map<String, Object>) featureMap);
    }

    private static List<double[]> parseCoordinateList(List<?> rawCoordinates) {
        if (rawCoordinates == null || rawCoordinates.size() < 2) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "LineString 至少需要 2 个坐标点");
        }

        List<double[]> coordinates = new ArrayList<>(rawCoordinates.size());
        for (Object item : rawCoordinates) {
            if (!(item instanceof List<?> point) || point.size() < 2) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "坐标格式无效，期望 [lng, lat]");
            }
            coordinates.add(new double[] {
                    toDouble(point.get(0)),
                    toDouble(point.get(1))
            });
        }
        return coordinates;
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    /**
     * 从 GeoJSON 根对象、Feature 或 FeatureCollection 首要素读取 properties。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractGeoJsonProperties(Map<String, Object> geoJson) {
        if (geoJson == null || geoJson.isEmpty()) {
            return Map.of();
        }

        Object props = geoJson.get("properties");
        if (props instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        String type = String.valueOf(geoJson.getOrDefault("type", ""));
        if ("FeatureCollection".equals(type)) {
            Object featuresObj = geoJson.get("features");
            if (featuresObj instanceof List<?> features && !features.isEmpty()) {
                Object first = features.get(0);
                if (first instanceof Map<?, ?> featureMap) {
                    Object featureProps = ((Map<String, Object>) featureMap).get("properties");
                    if (featureProps instanceof Map<?, ?> featurePropsMap) {
                        return (Map<String, Object>) featurePropsMap;
                    }
                }
            }
        }
        return Map.of();
    }

    /**
     * 解析 properties.altitudes（或与 coordinates 等长的高度数组），长度须与坐标点数一致。
     */
    public static List<Double> parseAltitudesList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Double> altitudes = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "properties.altitudes 不能含空值");
            }
            altitudes.add(toDouble(item));
        }
        return altitudes;
    }
}
