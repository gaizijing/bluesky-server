package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluesky.entity.Route;
import com.bluesky.entity.RouteVersion;
import com.bluesky.entity.RouteWaypoint;
import com.bluesky.exception.BusinessException;
import com.bluesky.common.ResultCode;
import com.bluesky.mapper.RouteMapper;
import com.bluesky.mapper.RouteVersionMapper;
import com.bluesky.mapper.RouteWaypointMapper;
import com.bluesky.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RouteLifecycleService {

    private final RouteMapper routeMapper;
    private final RouteVersionMapper routeVersionMapper;
    private final RouteWaypointMapper waypointMapper;
    private final RegionService regionService;

    public Map<String, Object> listByRegion(String regionId, int page, int size) {
        regionService.assertRegionAccess(regionId);
        Page<Route> routePage = routeMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Route>()
                        .eq(Route::getRegionId, regionId)
                        .eq(Route::getDeleted, 0)
                        .orderByDesc(Route::getCreatedAt));

        List<Map<String, Object>> records = new ArrayList<>();
        for (Route route : routePage.getRecords()) {
            records.add(toSummary(route));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", routePage.getTotal());
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public List<Route> listRoutesByRegion(String regionId) {
        regionService.assertRegionAccess(regionId);
        return routeMapper.selectList(new LambdaQueryWrapper<Route>()
                .eq(Route::getRegionId, regionId)
                .eq(Route::getDeleted, 0)
                .orderByDesc(Route::getCreatedAt));
    }

    public List<RouteVersion> listVersions(String routeId) {
        Route route = requireRoute(routeId);
        regionService.assertRegionAccess(route.getRegionId());
        return routeVersionMapper.selectList(new LambdaQueryWrapper<RouteVersion>()
                .eq(RouteVersion::getRouteId, routeId)
                .orderByDesc(RouteVersion::getVersionNo));
    }

    public Map<String, Object> getRouteDetail(String routeId, String routeVersionId) {
        Route route = requireRoute(routeId);
        regionService.assertRegionAccess(route.getRegionId());
        String versionId = resolveVersionId(route, routeVersionId);
        List<RouteWaypoint> waypoints = listWaypoints(routeId, versionId);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("routeId", route.getId());
        detail.put("regionId", route.getRegionId());
        detail.put("routeVersionId", versionId);
        detail.put("currentVersionId", route.getCurrentVersionId());
        detail.put("name", route.getName());
        detail.put("startName", route.getStartName());
        detail.put("endName", route.getEndName());
        detail.put("distance", route.getDistance());
        detail.put("status", route.getStatus());
        detail.put("flightHeight", route.getFlightHeight());
        detail.put("aircraftModel", route.getAircraftModel());
        detail.put("waypoints", waypoints.stream().map(this::toWaypointMap).toList());
        return detail;
    }

    public List<RouteWaypoint> listWaypoints(String routeId, String routeVersionId) {
        LambdaQueryWrapper<RouteWaypoint> wrapper = new LambdaQueryWrapper<RouteWaypoint>()
                .eq(RouteWaypoint::getRouteId, routeId)
                .orderByAsc(RouteWaypoint::getSequence);
        if (routeVersionId != null && !routeVersionId.isBlank()) {
            wrapper.eq(RouteWaypoint::getRouteVersionId, routeVersionId);
        }
        return waypointMapper.selectList(wrapper);
    }

    public String resolveVersionId(Route route, String routeVersionId) {
        if (routeVersionId != null && !routeVersionId.isBlank()) {
            RouteVersion version = routeVersionMapper.selectById(routeVersionId);
            if (version == null || !route.getId().equals(version.getRouteId())) {
                throw new BusinessException(ResultCode.NOT_FOUND, "航路版本不存在: " + routeVersionId);
            }
            return routeVersionId;
        }
        if (route.getCurrentVersionId() != null && !route.getCurrentVersionId().isBlank()) {
            return route.getCurrentVersionId();
        }
        throw new BusinessException(ResultCode.NOT_FOUND, "航路未设置当前版本");
    }

    @Transactional
    public Map<String, Object> createRoute(String regionId, Map<String, Object> routeData) {
        regionService.assertRegionAccess(regionId);
        if (routeData == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "航路数据不能为空");
        }

        List<double[]> coordinates = extractCoordinates(routeData);
        if (coordinates.size() < 2) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "航路至少需要 2 个坐标点");
        }

        double[] start = coordinates.get(0);
        double[] end = coordinates.get(coordinates.size() - 1);
        String startName = String.valueOf(routeData.getOrDefault("startName", "起点"));
        String endName = String.valueOf(routeData.getOrDefault("endName", "终点"));
        double flightHeight = parseDoubleObj(routeData.get("flightHeight"), 300d);
        double distance = GeoUtil.pathLengthKm(coordinates);
        List<Double> altitudes = extractAltitudes(routeData, coordinates.size());

        Route route = new Route();
        route.setRegionId(regionId);
        route.setName(String.valueOf(routeData.getOrDefault("name", startName + "-" + endName)));
        route.setStartName(startName);
        route.setEndName(endName);
        route.setDistance(distance);
        route.setStatus("available");
        route.setIsActive(true);
        route.setAircraftModel(String.valueOf(routeData.getOrDefault("aircraftModel", "未知")));
        route.setFlightHeight(flightHeight);
        route.setCreatedAt(LocalDateTime.now());
        route.setUpdatedAt(LocalDateTime.now());
        routeMapper.insert(route);

        String versionId = "RV" + route.getId();
        RouteVersion version = new RouteVersion();
        version.setRouteVersionId(versionId);
        version.setRouteId(route.getId());
        version.setVersionNo(1);
        version.setCruiseHeightM(resolveCruiseHeightM(flightHeight, altitudes));
        version.setDistanceM(distance);
        version.setWaypointCount(coordinates.size());
        version.setStatus("ACTIVE");
        version.setCreatedAt(LocalDateTime.now());
        routeVersionMapper.insert(version);

        for (int i = 0; i < coordinates.size(); i++) {
            double[] point = coordinates.get(i);
            String name = waypointName(i, coordinates.size(), startName, endName);
            double alt = resolveWaypointAltitude(i, flightHeight, altitudes);
            insertWaypoint(route.getId(), versionId, i + 1, name, point[0], point[1], alt);
        }

        route.setCurrentVersionId(versionId);
        routeMapper.updateById(route);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("routeId", route.getId());
        result.put("routeVersionId", versionId);
        result.put("currentVersionId", versionId);
        result.put("regionId", regionId);
        return result;
    }

    @Transactional
    public Map<String, Object> importGeoJson(String regionId, Map<String, Object> geoJson) {
        List<double[]> coordinates = GeoUtil.parseLineCoordinates(geoJson);
        Map<String, Object> properties = GeoUtil.extractGeoJsonProperties(geoJson);

        double flightHeight = parseDoubleObj(
                firstNonNull(properties.get("flightHeight"), geoJson.get("flightHeight")),
                300d);
        List<Double> altitudes = resolveGeoJsonAltitudes(geoJson, properties, coordinates.size());

        String defaultName = "导入航路-" + System.currentTimeMillis();
        String name = String.valueOf(firstNonNull(properties.get("name"), geoJson.get("name"), defaultName));

        Map<String, Object> routeData = new LinkedHashMap<>();
        routeData.put("name", name);
        routeData.put("startName", String.valueOf(properties.getOrDefault("startName", "起点")));
        routeData.put("endName", String.valueOf(properties.getOrDefault("endName", "终点")));
        routeData.put("flightHeight", flightHeight);
        if (altitudes != null) {
            routeData.put("altitudes", altitudes);
        }
        routeData.put("startLon", coordinates.get(0)[0]);
        routeData.put("startLat", coordinates.get(0)[1]);
        routeData.put("endLon", coordinates.get(coordinates.size() - 1)[0]);
        routeData.put("endLat", coordinates.get(coordinates.size() - 1)[1]);
        if (coordinates.size() > 2) {
            List<Map<String, Object>> waypoints = new ArrayList<>();
            for (int i = 1; i < coordinates.size() - 1; i++) {
                Map<String, Object> wp = new LinkedHashMap<>();
                wp.put("name", "途经点" + i);
                wp.put("longitude", coordinates.get(i)[0]);
                wp.put("latitude", coordinates.get(i)[1]);
                if (altitudes != null && i < altitudes.size()) {
                    wp.put("height", altitudes.get(i));
                    wp.put("altitude", altitudes.get(i));
                }
                waypoints.add(wp);
            }
            routeData.put("waypoints", waypoints);
        }
        return createRoute(regionId, routeData);
    }

    @Transactional
    public void deleteByRegion(String regionId) {
        regionService.assertRegionAccess(regionId);
        List<Route> routes = routeMapper.selectList(new LambdaQueryWrapper<Route>().eq(Route::getRegionId, regionId));
        for (Route route : routes) {
            routeMapper.deleteById(route.getId());
        }
    }

    @Transactional
    public void deleteRoute(String routeId) {
        Route route = requireRoute(routeId);
        regionService.assertRegionAccess(route.getRegionId());
        routeMapper.deleteById(routeId);
    }

    private Route requireRoute(String routeId) {
        Route route = routeMapper.selectById(routeId);
        if (route == null || Integer.valueOf(1).equals(route.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "航路不存在: " + routeId);
        }
        return route;
    }

    private Map<String, Object> toSummary(Route route) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("routeId", route.getId());
        item.put("regionId", route.getRegionId());
        item.put("name", route.getName());
        item.put("startName", route.getStartName());
        item.put("endName", route.getEndName());
        item.put("currentVersionId", route.getCurrentVersionId());
        item.put("distance", route.getDistance());
        item.put("status", route.getStatus());
        return item;
    }

    private Map<String, Object> toWaypointMap(RouteWaypoint waypoint) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sequence", waypoint.getSequence());
        map.put("name", waypoint.getName());
        map.put("longitude", waypoint.getLongitude());
        map.put("latitude", waypoint.getLatitude());
        map.put("altitude", waypoint.getAltitude());
        map.put("height", waypoint.getAltitude());
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<double[]> extractCoordinates(Map<String, Object> routeData) {
        Object rawCoordinates = routeData.get("coordinates");
        if (rawCoordinates instanceof List<?>) {
            return GeoUtil.parseLineCoordinates(Map.of("type", "LineString", "coordinates", rawCoordinates));
        }

        double startLon = parseDouble(routeData.get("startLon"));
        double startLat = parseDouble(routeData.get("startLat"));
        double endLon = parseDouble(routeData.get("endLon"));
        double endLat = parseDouble(routeData.get("endLat"));

        List<double[]> coordinates = new ArrayList<>();
        coordinates.add(new double[] { startLon, startLat });

        Object waypointObj = routeData.get("waypoints");
        if (waypointObj instanceof List<?> rawList) {
            for (Object raw : rawList) {
                if (!(raw instanceof Map<?, ?> mapLike)) {
                    continue;
                }
                Map<String, Object> waypoint = (Map<String, Object>) mapLike;
                coordinates.add(new double[] {
                        parseDouble(waypoint.getOrDefault("longitude", waypoint.get("lon"))),
                        parseDouble(waypoint.getOrDefault("latitude", waypoint.get("lat")))
                });
            }
        }

        coordinates.add(new double[] { endLon, endLat });
        return coordinates;
    }

    private String waypointName(int index, int total, String startName, String endName) {
        if (index == 0) {
            return startName;
        }
        if (index == total - 1) {
            return endName;
        }
        return "途经点" + index;
    }

    private void insertWaypoint(String routeId, String versionId, int seq, String name,
                                double lng, double lat, Double alt) {
        RouteWaypoint wp = new RouteWaypoint();
        wp.setRouteId(routeId);
        wp.setRouteVersionId(versionId);
        wp.setSequence(seq);
        wp.setName(name);
        wp.setLongitude(lng);
        wp.setLatitude(lat);
        wp.setAltitude(alt != null ? alt : 300d);
        wp.setCreatedAt(LocalDateTime.now());
        waypointMapper.insert(wp);
    }

    private double parseDouble(Object value) {
        if (value == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "坐标不能为空");
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private double parseDoubleObj(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private List<Double> extractAltitudes(Map<String, Object> routeData, int pointCount) {
        List<Double> altitudes = GeoUtil.parseAltitudesList(routeData.get("altitudes"));
        if (altitudes == null) {
            return null;
        }
        assertAltitudesLength(altitudes.size(), pointCount);
        return altitudes;
    }

    private List<Double> resolveGeoJsonAltitudes(Map<String, Object> geoJson,
                                                 Map<String, Object> properties,
                                                 int pointCount) {
        List<Double> altitudes = GeoUtil.parseAltitudesList(
                firstNonNull(properties.get("altitudes"), geoJson.get("altitudes")));
        if (altitudes == null) {
            return null;
        }
        assertAltitudesLength(altitudes.size(), pointCount);
        return altitudes;
    }

    private void assertAltitudesLength(int altCount, int pointCount) {
        if (altCount != pointCount) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "properties.altitudes 长度(" + altCount + ")须与坐标点数(" + pointCount + ")一致");
        }
    }

    private double resolveCruiseHeightM(double flightHeight, List<Double> altitudes) {
        if (altitudes == null || altitudes.isEmpty()) {
            return flightHeight;
        }
        return altitudes.stream().mapToDouble(Double::doubleValue).max().orElse(flightHeight);
    }

    private double resolveWaypointAltitude(int index, double flightHeight, List<Double> altitudes) {
        if (altitudes != null && index >= 0 && index < altitudes.size()) {
            return altitudes.get(index);
        }
        return flightHeight;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
