package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.common.ResultCode;
import com.bluesky.entity.LandingPoint;
import com.bluesky.entity.MicroscaleWeather;
import com.bluesky.entity.Route;
import com.bluesky.entity.RouteWaypoint;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.MicroscaleWeatherMapper;
import com.bluesky.mapper.RouteMapper;
import com.bluesky.util.TimeBucketUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * 航路分析服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteMapper routeMapper;
    private final RouteLifecycleService routeLifecycleService;
    private final MicroscaleWeatherMapper microscaleWeatherMapper;
    private final LandingPointService landingPointService;

    private static final int SEGMENT_SAMPLE_COUNT = 7;
    private static final int PATH_POINT_COUNT = 10;

    private static final class GridSnapshot {
        private final int gridSize;
        private final double minLng;
        private final double minLat;
        private final double maxLng;
        private final double maxLat;
        private final double riskScale;
        private final double[][] risk;
        private final double[][] windSpeed;
        private final double[][] windShear;
        private final double[][] turbulence;
        private final String[][] reason;

        private GridSnapshot(int gridSize,
                             double minLng, double minLat, double maxLng, double maxLat,
                             double riskScale,
                             double[][] risk,
                             double[][] windSpeed,
                             double[][] windShear,
                             double[][] turbulence,
                             String[][] reason) {
            this.gridSize = gridSize;
            this.minLng = minLng;
            this.minLat = minLat;
            this.maxLng = maxLng;
            this.maxLat = maxLat;
            this.riskScale = riskScale;
            this.risk = risk;
            this.windSpeed = windSpeed;
            this.windShear = windShear;
            this.turbulence = turbulence;
            this.reason = reason;
        }
    }

    private static final class SampleValue {
        private final double risk01;
        private final double windSpeed;
        private final double windShear;
        private final double turbulence;
        private final String reason;

        private SampleValue(double risk01, double windSpeed, double windShear, double turbulence, String reason) {
            this.risk01 = risk01;
            this.windSpeed = windSpeed;
            this.windShear = windShear;
            this.turbulence = turbulence;
            this.reason = reason;
        }
    }

    private static final class SegmentMetrics {
        private double risk01Max = 0d;
        private double windSpeedMax = 0d;
        private double windShearMax = 0d;
        private double turbulenceMax = 0d;
        private String reason = null;

        private void accept(SampleValue value) {
            if (value == null) {
                return;
            }
            if (value.risk01 > risk01Max) {
                risk01Max = value.risk01;
                if (value.reason != null && !value.reason.isBlank()) {
                    reason = value.reason;
                }
            }
            windSpeedMax = Math.max(windSpeedMax, value.windSpeed);
            windShearMax = Math.max(windShearMax, value.windShear);
            turbulenceMax = Math.max(turbulenceMax, value.turbulence);
        }
    }

    private static final class WaypointNode {
        private final String name;
        private final double longitude;
        private final double latitude;
        private final double altitude;

        private WaypointNode(String name, double longitude, double latitude, double altitude) {
            this.name = name;
            this.longitude = longitude;
            this.latitude = latitude;
            this.altitude = altitude;
        }
    }

    private static final class RouteMetrics {
        private final List<WaypointNode> waypoints;
        private final List<Map<String, Object>> segmentData;
        private final List<Integer> dangers;
        private final double totalDistance;
        private final double averageRisk;
        private final double highestRisk;
        private final int highestRiskSegment;

        private RouteMetrics(List<WaypointNode> waypoints,
                             List<Map<String, Object>> segmentData,
                             List<Integer> dangers,
                             double totalDistance,
                             double averageRisk,
                             double highestRisk,
                             int highestRiskSegment) {
            this.waypoints = waypoints;
            this.segmentData = segmentData;
            this.dangers = dangers;
            this.totalDistance = totalDistance;
            this.averageRisk = averageRisk;
            this.highestRisk = highestRisk;
            this.highestRiskSegment = highestRiskSegment;
        }
    }

    private LocalDateTime resolveAnalysisTime(Route route, Map<String, Object> params) {
        if (params != null && params.containsKey("currentTime") && params.get("currentTime") != null) {
            String timeStr = params.get("currentTime").toString().trim();
            if (!timeStr.isEmpty()) {
                try {
                    if (timeStr.endsWith("Z") || timeStr.contains("+")) {
                        return OffsetDateTime.parse(timeStr).toLocalDateTime();
                    }
                    return LocalDateTime.parse(timeStr.replace("Z", ""));
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
        if (route != null && route.getStartTime() != null) {
            return route.getStartTime();
        }
        return LocalDateTime.now();
    }

    private LandingPoint selectLandingPoint(List<LandingPoint> points, double lng, double lat) {
        if (points == null || points.isEmpty()) {
            return null;
        }

        LandingPoint best = null;
        double bestArea = Double.POSITIVE_INFINITY;
        for (LandingPoint point : points) {
            if (point == null || point.getBboxMinLng() == null || point.getBboxMinLat() == null
                    || point.getBboxMaxLng() == null || point.getBboxMaxLat() == null) {
                continue;
            }

            double minLng = point.getBboxMinLng().doubleValue();
            double minLat = point.getBboxMinLat().doubleValue();
            double maxLng = point.getBboxMaxLng().doubleValue();
            double maxLat = point.getBboxMaxLat().doubleValue();

            if (lng >= minLng && lng <= maxLng && lat >= minLat && lat <= maxLat) {
                double area = Math.abs((maxLng - minLng) * (maxLat - minLat));
                if (area < bestArea) {
                    bestArea = area;
                    best = point;
                }
            }
        }

        if (best != null) {
            return best;
        }

        double bestD2 = Double.POSITIVE_INFINITY;
        for (LandingPoint point : points) {
            if (point == null || point.getLongitude() == null || point.getLatitude() == null) {
                continue;
            }
            double dx = lng - point.getLongitude().doubleValue();
            double dy = lat - point.getLatitude().doubleValue();
            double d2 = dx * dx + dy * dy;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = point;
            }
        }

        return best;
    }

    private GridSnapshot loadSnapshot(LandingPoint point, LocalDateTime targetTime) {
        if (point == null || point.getLandingPointId() == null) {
            return null;
        }

        try {
            return loadSnapshotInternal(point, targetTime);
        } catch (Exception ex) {
            log.warn("微尺度天气数据不可用，起降点 {}: {}", point.getLandingPointId(), ex.getMessage());
            return null;
        }
    }

    private GridSnapshot loadSnapshotInternal(LandingPoint point, LocalDateTime targetTime) {
        LambdaQueryWrapper<MicroscaleWeather> latestQuery = new LambdaQueryWrapper<MicroscaleWeather>()
                .eq(MicroscaleWeather::getPointId, point.getLandingPointId());
        if (targetTime != null) {
            latestQuery.le(MicroscaleWeather::getDataTime, targetTime);
        }
        latestQuery.orderByDesc(MicroscaleWeather::getDataTime).last("LIMIT 1");

        MicroscaleWeather latest = microscaleWeatherMapper.selectOne(latestQuery);
        if (latest == null || latest.getDataTime() == null) {
            return null;
        }

        List<MicroscaleWeather> rows = microscaleWeatherMapper.selectList(
                new LambdaQueryWrapper<MicroscaleWeather>()
                        .eq(MicroscaleWeather::getPointId, point.getLandingPointId())
                        .eq(MicroscaleWeather::getDataTime, latest.getDataTime()));
        if (rows == null || rows.isEmpty()) {
            return null;
        }

        Integer gridSizeValue = rows.get(0).getGridSize();
        int gridSize = gridSizeValue != null ? gridSizeValue : 0;
        if (gridSize <= 1) {
            return null;
        }

        if (point.getBboxMinLng() == null || point.getBboxMinLat() == null || point.getBboxMaxLng() == null || point.getBboxMaxLat() == null) {
            return null;
        }

        double minLng = point.getBboxMinLng().doubleValue();
        double minLat = point.getBboxMinLat().doubleValue();
        double maxLng = point.getBboxMaxLng().doubleValue();
        double maxLat = point.getBboxMaxLat().doubleValue();

        double[][] risk = new double[gridSize][gridSize];
        double[][] windSpeed = new double[gridSize][gridSize];
        double[][] windShear = new double[gridSize][gridSize];
        double[][] turbulence = new double[gridSize][gridSize];
        String[][] reason = new String[gridSize][gridSize];

        double maxRiskValue = 0d;
        for (MicroscaleWeather row : rows) {
            if (row == null || row.getGridX() == null || row.getGridY() == null) {
                continue;
            }
            int gx = row.getGridX().intValue();
            int gy = row.getGridY().intValue();
            if (gx < 0 || gx >= gridSize || gy < 0 || gy >= gridSize) {
                continue;
            }

            double riskValue = row.getRiskLevel() != null ? row.getRiskLevel().doubleValue() : 0d;
            risk[gx][gy] = riskValue;
            maxRiskValue = Math.max(maxRiskValue, riskValue);

            windSpeed[gx][gy] = row.getWindSpeed() != null ? row.getWindSpeed().doubleValue() : 0d;
            windShear[gx][gy] = row.getWindShear() != null ? row.getWindShear().doubleValue() : 0d;
            turbulence[gx][gy] = row.getTurbulence() != null ? row.getTurbulence().doubleValue() : 0d;
            reason[gx][gy] = row.getReason();
        }

        double riskScale = maxRiskValue <= 3.5d ? (1d / 3d) : (1d / 100d);
        return new GridSnapshot(gridSize, minLng, minLat, maxLng, maxLat, riskScale, risk, windSpeed, windShear, turbulence, reason);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private SampleValue sampleAt(GridSnapshot snapshot, double lng, double lat) {
        if (snapshot == null) {
            return null;
        }

        if (!(snapshot.maxLng > snapshot.minLng) || !(snapshot.maxLat > snapshot.minLat)) {
            return null;
        }

        int gridSize = snapshot.gridSize;
        double x = (lng - snapshot.minLng) / (snapshot.maxLng - snapshot.minLng) * (gridSize - 1d);
        double y = (lat - snapshot.minLat) / (snapshot.maxLat - snapshot.minLat) * (gridSize - 1d);

        x = clamp(x, 0d, gridSize - 1d);
        y = clamp(y, 0d, gridSize - 1d);

        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = Math.min(gridSize - 1, x0 + 1);
        int y1 = Math.min(gridSize - 1, y0 + 1);

        if (x0 == x1 && x0 > 0) {
            x0 -= 1;
        }
        if (y0 == y1 && y0 > 0) {
            y0 -= 1;
        }
        x1 = Math.min(gridSize - 1, x0 + 1);
        y1 = Math.min(gridSize - 1, y0 + 1);

        double fx = x - x0;
        double fy = y - y0;

        double r00 = snapshot.risk[x0][y0];
        double r10 = snapshot.risk[x1][y0];
        double r01 = snapshot.risk[x0][y1];
        double r11 = snapshot.risk[x1][y1];

        double riskValue = r00 * (1 - fx) * (1 - fy)
                + r10 * fx * (1 - fy)
                + r01 * (1 - fx) * fy
                + r11 * fx * fy;

        double ws00 = snapshot.windSpeed[x0][y0];
        double ws10 = snapshot.windSpeed[x1][y0];
        double ws01 = snapshot.windSpeed[x0][y1];
        double ws11 = snapshot.windSpeed[x1][y1];
        double windSpeed = ws00 * (1 - fx) * (1 - fy)
                + ws10 * fx * (1 - fy)
                + ws01 * (1 - fx) * fy
                + ws11 * fx * fy;

        double wsh00 = snapshot.windShear[x0][y0];
        double wsh10 = snapshot.windShear[x1][y0];
        double wsh01 = snapshot.windShear[x0][y1];
        double wsh11 = snapshot.windShear[x1][y1];
        double windShear = wsh00 * (1 - fx) * (1 - fy)
                + wsh10 * fx * (1 - fy)
                + wsh01 * (1 - fx) * fy
                + wsh11 * fx * fy;

        double t00 = snapshot.turbulence[x0][y0];
        double t10 = snapshot.turbulence[x1][y0];
        double t01 = snapshot.turbulence[x0][y1];
        double t11 = snapshot.turbulence[x1][y1];
        double turbulence = t00 * (1 - fx) * (1 - fy)
                + t10 * fx * (1 - fy)
                + t01 * (1 - fx) * fy
                + t11 * fx * fy;

        String pickedReason = snapshot.reason[x0][y0];
        double maxCornerRisk = r00;
        if (r10 > maxCornerRisk) {
            maxCornerRisk = r10;
            pickedReason = snapshot.reason[x1][y0];
        }
        if (r01 > maxCornerRisk) {
            maxCornerRisk = r01;
            pickedReason = snapshot.reason[x0][y1];
        }
        if (r11 > maxCornerRisk) {
            pickedReason = snapshot.reason[x1][y1];
        }

        double risk01 = clamp(riskValue * snapshot.riskScale, 0d, 1d);
        return new SampleValue(risk01, windSpeed, windShear, turbulence, pickedReason);
    }

    private SegmentMetrics evaluateSegment(List<LandingPoint> points,
                                          Map<String, GridSnapshot> snapshotCache,
                                          double startLon, double startLat,
                                          double endLon, double endLat,
                                          LocalDateTime analysisTime) {
        SegmentMetrics metrics = new SegmentMetrics();
        if (SEGMENT_SAMPLE_COUNT <= 1) {
            return metrics;
        }

        for (int i = 0; i < SEGMENT_SAMPLE_COUNT; i++) {
            double t = i / (double) (SEGMENT_SAMPLE_COUNT - 1);
            double lng = startLon + (endLon - startLon) * t;
            double lat = startLat + (endLat - startLat) * t;

            LandingPoint point = selectLandingPoint(points, lng, lat);
            if (point == null || point.getLandingPointId() == null) {
                continue;
            }

            GridSnapshot snapshot = snapshotCache.get(point.getLandingPointId());
            if (snapshot == null) {
                snapshot = loadSnapshot(point, analysisTime);
                if (snapshot != null) {
                    snapshotCache.put(point.getLandingPointId(), snapshot);
                }
            }

            metrics.accept(sampleAt(snapshot, lng, lat));
        }

        return metrics;
    }

    private List<List<Double>> buildPathCoordinates(double startLon, double startLat, double endLon, double endLat) {
        List<List<Double>> coords = new ArrayList<>();
        int count = Math.max(2, PATH_POINT_COUNT);
        for (int i = 0; i < count; i++) {
            double t = i / (double) (count - 1);
            coords.add(List.of(
                    startLon + (endLon - startLon) * t,
                    startLat + (endLat - startLat) * t
            ));
        }
        return coords;
    }

    private String normalizeRouteName(Route route) {
        if (route == null) {
            return "";
        }
        if (route.getName() != null && !route.getName().isBlank()) {
            return route.getName();
        }
        String startName = route.getStartName() != null ? route.getStartName() : "起点";
        String endName = route.getEndName() != null ? route.getEndName() : "终点";
        return startName + "-" + endName;
    }

    private double parseDouble(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String parseString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String str = value.toString().trim();
        return str.isEmpty() ? fallback : str;
    }

    private LocalDateTime parseDateTime(Object value, LocalDateTime fallback) {
        if (value == null) {
            return fallback;
        }
        String timeStr = value.toString().trim();
        if (timeStr.isEmpty()) {
            return fallback;
        }
        try {
            if (timeStr.endsWith("Z") || timeStr.contains("+")) {
                return OffsetDateTime.parse(timeStr).toLocalDateTime();
            }
            return LocalDateTime.parse(timeStr.replace("Z", ""));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Map<String, Object> buildWaypointMap(WaypointNode node) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", node.name);
        map.put("longitude", node.longitude);
        map.put("latitude", node.latitude);
        map.put("height", node.altitude);
        map.put("altitude", node.altitude);
        return map;
    }

    private List<Map<String, Object>> toWaypointMaps(List<WaypointNode> waypoints) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (WaypointNode waypoint : waypoints) {
            maps.add(buildWaypointMap(waypoint));
        }
        return maps;
    }

    private List<RouteWaypoint> getOrderedWaypoints(String routeId, String routeVersionId) {
        return routeLifecycleService.listWaypoints(routeId, routeVersionId);
    }

    private List<WaypointNode> toWaypointNodes(List<RouteWaypoint> waypoints, double defaultHeight) {
        List<WaypointNode> nodes = new ArrayList<>();
        if (waypoints == null) {
            return nodes;
        }
        for (int i = 0; i < waypoints.size(); i++) {
            RouteWaypoint waypoint = waypoints.get(i);
            if (waypoint == null || waypoint.getLongitude() == null || waypoint.getLatitude() == null) {
                continue;
            }
            double altitude = waypoint.getAltitude() != null ? waypoint.getAltitude() : defaultHeight;
            String name = waypoint.getName();
            if (name == null || name.isBlank()) {
                name = switch (i) {
                    case 0 -> "起点";
                    case 1 -> waypoints.size() == 2 ? "终点" : "途经点1";
                    default -> i == waypoints.size() - 1 ? "终点" : "途经点" + i;
                };
            }
            nodes.add(new WaypointNode(
                    name,
                    waypoint.getLongitude(),
                    waypoint.getLatitude(),
                    altitude
            ));
        }
        return nodes;
    }

    private WaypointNode buildWaypointNodeFromRequest(Map<String, Object> payload,
                                                      String fallbackName,
                                                      double defaultHeight) {
        if (payload == null) {
            return null;
        }
        double longitude = parseDouble(
                payload.containsKey("longitude") ? payload.get("longitude") : payload.get("lon"),
                Double.NaN
        );
        double latitude = parseDouble(
                payload.containsKey("latitude") ? payload.get("latitude") : payload.get("lat"),
                Double.NaN
        );
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)) {
            return null;
        }

        double altitude = parseDouble(
                payload.containsKey("height") ? payload.get("height") : payload.get("altitude"),
                defaultHeight
        );
        String name = parseString(payload.get("name"), fallbackName);
        return new WaypointNode(name, longitude, latitude, altitude);
    }

    @SuppressWarnings("unchecked")
    private List<WaypointNode> buildWaypointNodesFromRequest(Map<String, Object> routeData,
                                                             String startName,
                                                             double startLon,
                                                             double startLat,
                                                             String endName,
                                                             double endLon,
                                                             double endLat,
                                                             double flightHeight) {
        List<WaypointNode> nodes = new ArrayList<>();
        nodes.add(new WaypointNode(startName, startLon, startLat, flightHeight));

        Object waypointObj = routeData.get("waypoints");
        if (waypointObj instanceof List<?> rawList) {
            int index = 1;
            for (Object raw : rawList) {
                if (!(raw instanceof Map<?, ?> mapLike)) {
                    continue;
                }
                Map<String, Object> waypointPayload = (Map<String, Object>) mapLike;
                WaypointNode node = buildWaypointNodeFromRequest(waypointPayload, "途经点" + index, flightHeight);
                if (node != null) {
                    nodes.add(node);
                    index++;
                }
            }
        }

        nodes.add(new WaypointNode(endName, endLon, endLat, flightHeight));
        return nodes;
    }

    private String inferSegmentReason(SegmentMetrics metrics) {
        if (metrics == null) {
            return "";
        }
        if (metrics.reason != null && !metrics.reason.isBlank()) {
            return metrics.reason;
        }
        List<String> reasonParts = new ArrayList<>();
        if (metrics.windSpeedMax >= 10d) {
            reasonParts.add("风速偏大");
        }
        if (metrics.windShearMax >= 0.35d) {
            reasonParts.add("风切变增强");
        }
        if (metrics.turbulenceMax >= 0.35d) {
            reasonParts.add("湍流增强");
        }
        return reasonParts.isEmpty() ? "" : String.join("，", reasonParts);
    }

    private String getRiskLevelKey(double risk) {
        if (risk >= 0.7d) {
            return "high";
        }
        if (risk >= 0.3d) {
            return "medium";
        }
        return "low";
    }

    private String getRiskLevelText(double risk) {
        return switch (getRiskLevelKey(risk)) {
            case "high" -> "高风险";
            case "medium" -> "中风险";
            default -> "低风险";
        };
    }

    private String getSafetyLevel(double risk) {
        if (risk >= 0.7d) {
            return "谨慎飞行";
        }
        if (risk >= 0.3d) {
            return "条件可飞";
        }
        return "适宜飞行";
    }

    private int estimateMinutes(double distance) {
        if (!Double.isFinite(distance) || distance <= 0d) {
            return 0;
        }
        return Math.max(1, (int) Math.round(distance / 1.2d));
    }

    private RouteMetrics buildRouteMetrics(List<WaypointNode> waypoints, LocalDateTime analysisTime) {
        List<Map<String, Object>> segmentData = new ArrayList<>();
        List<Integer> dangers = new ArrayList<>();
        if (waypoints == null || waypoints.size() < 2) {
            return new RouteMetrics(waypoints == null ? List.of() : waypoints, segmentData, dangers, 0d, 0d, 0d, 0);
        }

        List<LandingPoint> landingPoints = landingPointService.listAllEntities();
        Map<String, GridSnapshot> snapshotCache = new HashMap<>();

        double totalDistance = 0d;
        double riskSum = 0d;
        double highestRisk = 0d;
        int highestRiskSegment = 0;

        for (int i = 0; i < waypoints.size() - 1; i++) {
            WaypointNode start = waypoints.get(i);
            WaypointNode end = waypoints.get(i + 1);
            double segmentLength = calculateDistance(start.longitude, start.latitude, end.longitude, end.latitude);
            totalDistance += segmentLength;

            SegmentMetrics metrics = evaluateSegment(
                    landingPoints,
                    snapshotCache,
                    start.longitude, start.latitude,
                    end.longitude, end.latitude,
                    analysisTime
            );

            double risk = clamp(metrics.risk01Max, 0d, 1d);
            String reason = inferSegmentReason(metrics);
            Map<String, Object> segment = new HashMap<>();
            segment.put("segment", i + 1);
            segment.put("distance", totalDistance);
            segment.put("segmentLength", segmentLength);
            segment.put("risk", risk);
            segment.put("riskLevel", getRiskLevelKey(risk));
            segment.put("windSpeed", metrics.windSpeedMax);
            segment.put("windDir", 0);
            segment.put("windShear", metrics.windShearMax);
            segment.put("turbulence", metrics.turbulenceMax);
            segment.put("rainfall", 0d);
            segment.put("startCoordinates", List.of(start.longitude, start.latitude));
            segment.put("endCoordinates", List.of(end.longitude, end.latitude));
            segment.put("pathCoordinates", buildPathCoordinates(start.longitude, start.latitude, end.longitude, end.latitude));
            if (!reason.isBlank()) {
                segment.put("reason", reason);
            }

            segmentData.add(segment);
            dangers.add((int) Math.round(risk * 10d));
            riskSum += risk;

            if (risk >= highestRisk) {
                highestRisk = risk;
                highestRiskSegment = i + 1;
            }
        }

        double averageRisk = segmentData.isEmpty() ? 0d : riskSum / segmentData.size();
        return new RouteMetrics(waypoints, segmentData, dangers, totalDistance, averageRisk, highestRisk, highestRiskSegment);
    }

    private Map<String, Object> buildRoutePayload(Route route,
                                                  List<WaypointNode> waypoints,
                                                  RouteMetrics metrics) {
        Map<String, Object> payload = new HashMap<>();
        String routeId = route != null ? route.getId() : "";
        String routeName = route != null ? normalizeRouteName(route) : "";
        double flightHeight = route != null && route.getFlightHeight() != null ? route.getFlightHeight() : 300d;

        payload.put("id", routeId);
        payload.put("name", routeName);
        payload.put("startName", route != null ? route.getStartName() : (waypoints.isEmpty() ? "起点" : waypoints.get(0).name));
        payload.put("endName", route != null ? route.getEndName() : (waypoints.isEmpty() ? "终点" : waypoints.get(waypoints.size() - 1).name));
        payload.put("length", metrics.totalDistance);
        payload.put("segments", metrics.segmentData.size());
        payload.put("averageRisk", metrics.averageRisk);
        payload.put("riskLevel", getRiskLevelKey(metrics.averageRisk));
        payload.put("highestRisk", metrics.highestRisk);
        payload.put("highestRiskSegment", metrics.highestRiskSegment);
        payload.put("segmentData", metrics.segmentData);
        payload.put("dangers", metrics.dangers);
        payload.put("waypoints", toWaypointMaps(waypoints));
        payload.put("aircraftModel", route != null ? route.getAircraftModel() : null);
        payload.put("flightHeight", flightHeight);
        payload.put("estimatedMinutes", estimateMinutes(metrics.totalDistance));
        payload.put("estimatedTime", estimateMinutes(metrics.totalDistance) + "分钟");
        payload.put("dataCompleteness", buildDataCompleteness());

        if (route != null && route.getStartTime() != null) {
            payload.put("startTime", route.getStartTime().toString());
        }
        if (route != null && route.getEndTime() != null) {
            payload.put("endTime", route.getEndTime().toString());
        }

        return payload;
    }

    private List<Map<String, Object>> buildRiskDimensions(RouteMetrics metrics) {
        double windSpeedMax = metrics.segmentData.stream()
                .map(segment -> parseDouble(segment.get("windSpeed"), 0d))
                .max(Double::compareTo)
                .orElse(0d);
        double windShearMax = metrics.segmentData.stream()
                .map(segment -> parseDouble(segment.get("windShear"), 0d))
                .max(Double::compareTo)
                .orElse(0d);
        double turbulenceMax = metrics.segmentData.stream()
                .map(segment -> parseDouble(segment.get("turbulence"), 0d))
                .max(Double::compareTo)
                .orElse(0d);

        List<Map<String, Object>> dimensions = new ArrayList<>();
        dimensions.add(createRiskDimension(
                "风速风险",
                Math.min(10d, windSpeedMax),
                windSpeedMax >= 10d ? "存在大风航段，需要控制飞行速度和姿态稳定性。" : "沿线风速整体可控。"
        ));
        dimensions.add(createRiskDimension(
                "风切变风险",
                Math.min(10d, windShearMax * 10d),
                windShearMax >= 0.35d ? "局地风切变较明显，建议关注高度层变化。" : "风切变水平整体较低。"
        ));
        dimensions.add(createRiskDimension(
                "湍流风险",
                Math.min(10d, turbulenceMax * 10d),
                turbulenceMax >= 0.35d ? "部分航段存在中等以上湍流。" : "湍流影响整体较弱。"
        ));
        dimensions.add(createRiskDimension(
                "降水风险",
                0d,
                "当前航线分析尚未接入降水实况格点，暂按 0 返回。"
        ));
        return dimensions;
    }

    private Map<String, Object> createRiskDimension(String dimension, double score, String description) {
        Map<String, Object> item = new HashMap<>();
        double normalizedScore = clamp(score / 10d, 0d, 1d);
        item.put("dimension", dimension);
        item.put("level", getRiskLevelKey(normalizedScore));
        item.put("score", score);
        item.put("description", description);
        return item;
    }

    private Map<String, Object> buildOverallAssessment(RouteMetrics metrics) {
        Map<String, Object> assessment = new HashMap<>();
        assessment.put("riskLevel", getRiskLevelKey(metrics.averageRisk));
        assessment.put("overallScore", Math.round(metrics.averageRisk * 1000d) / 100d);
        assessment.put("safetyLevel", getSafetyLevel(metrics.averageRisk));

        String recommendation;
        if (metrics.highestRiskSegment > 0 && metrics.highestRisk >= 0.7d) {
            recommendation = "建议重点避让第 " + metrics.highestRiskSegment + " 航段，必要时切换备选航线。";
        } else if (metrics.highestRiskSegment > 0 && metrics.highestRisk >= 0.3d) {
            recommendation = "建议按计划飞行，但需重点关注第 " + metrics.highestRiskSegment + " 航段的局地天气变化。";
        } else {
            recommendation = "当前航线整体风险较低，可按计划执行并持续跟踪气象变化。";
        }
        assessment.put("recommendation", recommendation);
        return assessment;
    }

    private List<Map<String, Object>> buildSegmentAnalysis(RouteMetrics metrics) {
        List<Map<String, Object>> analysis = new ArrayList<>();
        if (metrics.waypoints == null || metrics.waypoints.size() < 2) {
            return analysis;
        }
        int segmentCount = Math.min(metrics.segmentData.size(), metrics.waypoints.size() - 1);
        for (int i = 0; i < segmentCount; i++) {
            Map<String, Object> segment = metrics.segmentData.get(i);
            WaypointNode start = metrics.waypoints.get(i);
            WaypointNode end = metrics.waypoints.get(i + 1);

            Map<String, Object> item = new HashMap<>();
            item.put("segment", i + 1);
            item.put("distance", segment.get("segmentLength"));
            item.put("startWaypoint", start.name);
            item.put("endWaypoint", end.name);
            item.put("windSpeed", segment.get("windSpeed"));
            item.put("windShear", segment.get("windShear"));
            item.put("turbulence", segment.get("turbulence"));
            item.put("risk", segment.get("risk"));
            item.put("riskLevel", segment.get("riskLevel"));
            item.put("reason", segment.get("reason"));
            item.put("recommendations", getSegmentRecommendationsFromMap(segment, i));
            analysis.add(item);
        }
        return analysis;
    }

    private List<Map<String, Object>> buildMeasures(Route route, RouteMetrics metrics) {
        List<Map<String, Object>> measures = new ArrayList<>();
        int id = 1;

        if (metrics.highestRisk >= 0.7d) {
            measures.add(createMeasure(
                    "measure-" + id++,
                    "调整航线或启用绕飞方案",
                    "最高风险航段达到高风险等级，建议优先避让该航段并预备切换备选航线。",
                    "high"
            ));
        }

        double windSpeedMax = metrics.segmentData.stream()
                .mapToDouble(segment -> parseDouble(segment.get("windSpeed"), 0d))
                .max()
                .orElse(0d);
        if (windSpeedMax >= 10d) {
            measures.add(createMeasure(
                    "measure-" + id++,
                    "控制巡航速度",
                    "沿线存在较大风速，建议降低飞行速度并加强姿态稳定控制。",
                    "medium"
            ));
        }

        double windShearMax = metrics.segmentData.stream()
                .mapToDouble(segment -> parseDouble(segment.get("windShear"), 0d))
                .max()
                .orElse(0d);
        if (windShearMax >= 0.35d) {
            double targetHeight = route != null && route.getFlightHeight() != null
                    ? Math.max(200d, route.getFlightHeight() + 100d)
                    : 400d;
            measures.add(createMeasure(
                    "measure-" + id++,
                    "微调飞行高度",
                    "检测到局地风切变增强，建议将飞行高度调整至约 " + Math.round(targetHeight) + " 米并持续观察。",
                    "high"
            ));
        }

        double turbulenceMax = metrics.segmentData.stream()
                .mapToDouble(segment -> parseDouble(segment.get("turbulence"), 0d))
                .max()
                .orElse(0d);
        if (turbulenceMax >= 0.35d) {
            measures.add(createMeasure(
                    "measure-" + id++,
                    "加强姿态与载荷监控",
                    "沿线存在湍流增强航段，建议检查载荷固定状态并提高返航触发敏感度。",
                    "medium"
            ));
        }

        if (measures.isEmpty()) {
            measures.add(createMeasure(
                    "measure-" + id,
                    "维持计划飞行",
                    "当前航线未发现显著高风险段，可按计划执行并保持实时气象监测。",
                    "low"
            ));
        }

        return measures;
    }

    private Map<String, Object> createMeasure(String id, String title, String description, String priority) {
        Map<String, Object> measure = new HashMap<>();
        measure.put("id", id);
        measure.put("title", title);
        measure.put("description", description);
        measure.put("priority", priority);
        measure.put("level", priority);
        measure.put("content", description);
        return measure;
    }

    private Map<String, Object> buildDataCompleteness() {
        Map<String, Object> completeness = new HashMap<>();
        completeness.put("isRealtimeWeather", true);
        completeness.put("supportsRouteSampling", true);
        completeness.put("supportsAlternativeRouteSimulation", true);
        completeness.put("missingInterfaces", List.of("windDir", "rainfall"));
        completeness.put("notes", List.of(
                "风向暂未接入独立格点接口，当前统一返回 0。",
                "降水暂未接入沿航线格点接口，当前统一返回 0。",
                "备选航线为基于主航线高风险段的算法绕飞结果，不依赖独立数据库表。"
        ));
        return completeness;
    }

    private List<Map<String, Object>> buildAlternativeRoutes(Route route, RouteMetrics primaryMetrics, LocalDateTime analysisTime) {
        if (primaryMetrics.waypoints.size() < 2
                || primaryMetrics.highestRiskSegment <= 0
                || primaryMetrics.highestRisk < 0.3d) {
            return List.of();
        }

        int focusIndex = Math.max(0, Math.min(primaryMetrics.highestRiskSegment - 1, primaryMetrics.waypoints.size() - 2));
        WaypointNode start = primaryMetrics.waypoints.get(focusIndex);
        WaypointNode end = primaryMetrics.waypoints.get(focusIndex + 1);
        double dx = end.longitude - start.longitude;
        double dy = end.latitude - start.latitude;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length <= 0d) {
            return List.of();
        }

        double normalLon = -dy / length;
        double normalLat = dx / length;
        double offsetBase = Math.max(0.01d, Math.min(0.04d, length * 0.35d));

        List<Map<String, Object>> alternatives = new ArrayList<>();
        alternatives.add(createAlternativeRoute(route, primaryMetrics, analysisTime, "北侧绕飞方案", focusIndex, normalLon, normalLat, offsetBase, 1, "北侧偏移绕开高风险核心区域。"));
        alternatives.add(createAlternativeRoute(route, primaryMetrics, analysisTime, "南侧绕飞方案", focusIndex, normalLon, normalLat, offsetBase * 1.2d, -1, "南侧偏移增加冗余距离，但有助于降低局地风险。"));

        alternatives.removeIf(Objects::isNull);
        return alternatives;
    }

    private Map<String, Object> createAlternativeRoute(Route route,
                                                       RouteMetrics primaryMetrics,
                                                       LocalDateTime analysisTime,
                                                       String name,
                                                       int focusIndex,
                                                       double normalLon,
                                                       double normalLat,
                                                       double offset,
                                                       int direction,
                                                       String description) {
        List<WaypointNode> nodes = new ArrayList<>();
        for (int i = 0; i < primaryMetrics.waypoints.size(); i++) {
            nodes.add(primaryMetrics.waypoints.get(i));
            if (i == focusIndex) {
                WaypointNode start = primaryMetrics.waypoints.get(i);
                WaypointNode end = primaryMetrics.waypoints.get(i + 1);
                double midLon = (start.longitude + end.longitude) / 2d;
                double midLat = (start.latitude + end.latitude) / 2d;
                nodes.add(new WaypointNode(
                        "绕飞点" + (direction > 0 ? "A" : "B"),
                        midLon + normalLon * offset * direction,
                        midLat + normalLat * offset * direction,
                        route != null && route.getFlightHeight() != null ? route.getFlightHeight() : start.altitude
                ));
            }
        }

        RouteMetrics altMetrics = buildRouteMetrics(nodes, analysisTime);
        if (altMetrics.segmentData.isEmpty()) {
            return null;
        }

        String routeId = (route != null ? route.getId() : "route") + "-alt-" + (direction > 0 ? "1" : "2");
        Map<String, Object> altRoute = new HashMap<>();
        altRoute.put("id", routeId);
        altRoute.put("name", name);
        altRoute.put("startName", route != null ? route.getStartName() : primaryMetrics.waypoints.get(0).name);
        altRoute.put("endName", route != null ? route.getEndName() : primaryMetrics.waypoints.get(primaryMetrics.waypoints.size() - 1).name);
        altRoute.put("length", altMetrics.totalDistance);
        altRoute.put("estimatedMinutes", estimateMinutes(altMetrics.totalDistance));
        altRoute.put("estimatedTime", estimateMinutes(altMetrics.totalDistance) + "分钟");
        altRoute.put("flightHeight", route != null && route.getFlightHeight() != null ? route.getFlightHeight() : 300d);
        altRoute.put("averageRisk", altMetrics.averageRisk);
        altRoute.put("riskLevel", getRiskLevelKey(altMetrics.averageRisk));
        altRoute.put("segments", altMetrics.segmentData.size());
        altRoute.put("highestRisk", altMetrics.highestRisk);
        altRoute.put("highestRiskSegment", altMetrics.highestRiskSegment);
        altRoute.put("aircraftModel", route != null ? route.getAircraftModel() : null);
        altRoute.put("description", description);
        altRoute.put("waypoints", toWaypointMaps(nodes));
        altRoute.put("segmentData", altMetrics.segmentData);
        altRoute.put("dangers", altMetrics.dangers);
        altRoute.put("dataCompleteness", buildDataCompleteness());

        if (route != null && route.getStartTime() != null) {
            altRoute.put("startTime", route.getStartTime().toString());
        }
        if (route != null && route.getEndTime() != null) {
            altRoute.put("endTime", route.getEndTime().toString());
        }

        return altRoute;
    }

    private Map<String, Object> generateRiskChartData(Route route, List<WaypointNode> waypoints) {
        Map<String, Object> chartData = new HashMap<>();
        List<String> timeLabels = new ArrayList<>();
        List<Double> riskValues = new ArrayList<>();

        LocalDateTime startTime = route != null && route.getStartTime() != null ? route.getStartTime() : LocalDateTime.now();
        LocalDateTime endTime = route != null && route.getEndTime() != null ? route.getEndTime() : startTime.plusHours(2);
        long totalMinutes = java.time.Duration.between(startTime, endTime).toMinutes();
        if (totalMinutes <= 0) {
            totalMinutes = 120;
            endTime = startTime.plusMinutes(totalMinutes);
        }

        int pointCount = (int) Math.max(2, Math.min(9, (totalMinutes / 15L) + 1));
        long stepMinutes = pointCount <= 1 ? totalMinutes : Math.max(1L, totalMinutes / (pointCount - 1));

        for (int i = 0; i < pointCount; i++) {
            LocalDateTime pointTime = i == pointCount - 1 ? endTime : startTime.plusMinutes(stepMinutes * i);
            RouteMetrics metrics = buildRouteMetrics(waypoints, pointTime);
            timeLabels.add(String.format("%02d:%02d", pointTime.getHour(), pointTime.getMinute()));
            riskValues.add(metrics.averageRisk);
        }

        chartData.put("timeLabels", timeLabels);
        chartData.put("riskValues", riskValues);

        double maxRisk = riskValues.stream().mapToDouble(Double::doubleValue).max().orElse(0d);
        double minRisk = riskValues.stream().mapToDouble(Double::doubleValue).min().orElse(0d);
        double avgRisk = riskValues.stream().mapToDouble(Double::doubleValue).average().orElse(0d);

        Map<String, Object> stats = new HashMap<>();
        stats.put("max", maxRisk);
        stats.put("min", minRisk);
        stats.put("average", avgRisk);
        stats.put("durationMinutes", totalMinutes);
        chartData.put("stats", stats);

        return chartData;
    }

    public Map<String, Object> getRouteDetail(String routeId, String routeVersionId) {
        log.debug("查询航线详情，航线ID: {}, 版本: {}", routeId, routeVersionId);
        Map<String, Object> detail = routeLifecycleService.getRouteDetail(routeId, routeVersionId);
        Route route = routeMapper.selectById(routeId);
        if (route != null) {
            applyTemporalMeta(detail, route.getStartTime() != null ? route.getStartTime() : LocalDateTime.now());
        }
        return detail;
    }

    private void applyTemporalMeta(Map<String, Object> payload, LocalDateTime requestedTime) {
        OffsetDateTime requested = requestedTime.atZone(TimeBucketUtil.ZONE).toOffsetDateTime();
        var meta = TimeBucketUtil.buildMeta(requested, TimeBucketUtil.now(), false);
        payload.put("requestedTime", meta.getRequestedTime());
        payload.put("bucketTime", meta.getBucketTime());
        payload.put("computedAt", meta.getComputedAt());
        payload.put("isStale", meta.getIsStale());
    }



    /**
     * 计算两点间距离（简化版，与前端算法一致）
     * 经度差转换为公里：111.32 km/度
     * 纬度差转换为公里：110.57 km/度
     */
    private double calculateDistance(double lon1, double lat1, double lon2, double lat2) {
        double dx = (lon2 - lon1) * 111.32; // 经度差转换为公里
        double dy = (lat2 - lat1) * 110.57; // 纬度差转换为公里
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 分析航线风险
     */
    public Map<String, Object> analyzeRouteRisk(String routeId, Map<String, Object> params) {
        try {
            Route route = routeMapper.selectById(routeId);
            if (route == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "航线不存在: " + routeId);
            }

            String routeVersionId = params != null && params.get("routeVersionId") != null
                    ? String.valueOf(params.get("routeVersionId"))
                    : null;
            String versionId = routeLifecycleService.resolveVersionId(route, routeVersionId);

            LocalDateTime currentAnalysisTime = resolveAnalysisTime(route, params);
            List<RouteWaypoint> orderedWaypoints = getOrderedWaypoints(routeId, versionId);
            double defaultHeight = route.getFlightHeight() != null ? route.getFlightHeight() : 300d;
            List<WaypointNode> waypointNodes = toWaypointNodes(orderedWaypoints, defaultHeight);
            RouteMetrics metrics = buildRouteMetrics(waypointNodes, currentAnalysisTime);
            List<Map<String, Object>> measures = buildMeasures(route, metrics);
            List<Map<String, Object>> alternativeRoutes = buildAlternativeRoutes(route, metrics, currentAnalysisTime);

            Map<String, Object> analysis = buildRoutePayload(route, waypointNodes, metrics);
            analysis.put("routeVersionId", versionId);
            analysis.put("currentVersionId", route.getCurrentVersionId());
            analysis.put("regionId", route.getRegionId());
            analysis.put("analysisTime", LocalDateTime.now().toString());
            analysis.put("currentAnalysisTime", currentAnalysisTime.toString());
            analysis.put("riskDimensions", buildRiskDimensions(metrics));
            analysis.put("overallAssessment", buildOverallAssessment(metrics));
            analysis.put("segmentAnalysis", buildSegmentAnalysis(metrics));
            analysis.put("measures", measures);
            analysis.put("alternativeRoutes", alternativeRoutes);
            try {
                analysis.put("riskChart", generateRiskChartData(route, waypointNodes));
            } catch (Exception chartEx) {
                log.warn("风险图表生成跳过，routeId={}: {}", routeId, chartEx.getMessage());
                analysis.put("riskChart", Map.of("timeLabels", List.of(), "riskValues", List.of()));
            }
            applyTemporalMeta(analysis, currentAnalysisTime);

            log.info("航线风险分析完成，航线ID: {}, 版本: {}, 分析时间点: {}, 航段数: {}",
                    routeId, versionId, currentAnalysisTime, metrics.segmentData.size());

            return analysis;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("航线分析失败，routeId={}", routeId, e);
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "航线分析失败");
        }
    }
    

    
    private List<String> getSegmentRecommendationsFromMap(Map<String, Object> segment, int index) {
        List<String> recommendations = new ArrayList<>();

        double risk = parseDouble(segment.get("risk"), 0d);
        String riskLevel = parseString(segment.get("riskLevel"), getRiskLevelKey(risk));
        String reason = parseString(segment.get("reason"), "");
        double windSpeed = parseDouble(segment.get("windSpeed"), 0d);
        double windShear = parseDouble(segment.get("windShear"), 0d);
        double turbulence = parseDouble(segment.get("turbulence"), 0d);

        if ("high".equals(riskLevel) || risk >= 0.7d) {
            recommendations.add("高风险航段，建议优先绕飞或暂停执行该航段。");
        } else if ("medium".equals(riskLevel) || risk >= 0.3d) {
            recommendations.add("中风险航段，建议降低速度并保持更高的气象更新频率。");
        } else {
            recommendations.add("当前航段风险较低，可按计划飞行并保持常规监控。");
        }

        if (windSpeed >= 10d) {
            recommendations.add("风速偏大，建议降低飞行速度并预留返航余量。");
        } else if (windSpeed >= 8d) {
            recommendations.add("风速有一定抬升，注意侧风修正。");
        }

        if (windShear >= 0.35d) {
            recommendations.add("检测到明显风切变，建议微调高度层并避免大幅姿态变化。");
        }

        if (turbulence >= 0.35d) {
            recommendations.add("湍流较强，建议检查载荷固定情况并提高稳定控制等级。");
        }

        if (!reason.isBlank()) {
            recommendations.add("主要风险因子：" + reason + "。");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("未识别到显著气象风险，可维持当前飞行方案。");
        }

        return recommendations;
    }
}
