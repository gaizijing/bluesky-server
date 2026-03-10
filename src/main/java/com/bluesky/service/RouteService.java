package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.config.RouteConfig;
import com.bluesky.entity.Route;
import com.bluesky.entity.RouteWaypoint;
import com.bluesky.mapper.RouteMapper;
import com.bluesky.mapper.RouteWaypointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 航路分析服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteMapper routeMapper;
    private final RouteWaypointMapper waypointMapper;
    private final RouteConfig routeConfig;

    /**
     * 获取航路列表
     */
    public Map<String, Object> getRouteList() {
        int maxHistoryCount = routeConfig.getMaxHistoryCount() != null ? routeConfig.getMaxHistoryCount() : 5;
        
        List<Route> routes = routeMapper.selectList(
                new LambdaQueryWrapper<Route>()
                        .eq(Route::getIsActive, true)
                        .orderByDesc(Route::getCreatedAt)
                        .last("LIMIT " + maxHistoryCount)
        );

        List<Map<String, Object>> routeList = new ArrayList<>();
        for (Route route : routes) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", route.getId());
            map.put("name", route.getStartName() + "-" + route.getEndName());
            map.put("startName", route.getStartName());
            map.put("endName", route.getEndName());
            map.put("length", route.getDistance());
            
            // 获取途经点
            List<RouteWaypoint> waypoints = waypointMapper.selectList(
                    new LambdaQueryWrapper<RouteWaypoint>()
                            .eq(RouteWaypoint::getRouteId, route.getId())
                            .orderByAsc(RouteWaypoint::getSequence)
            );
            
            int segmentCount = Math.max(0, waypoints.size() - 1);
            map.put("segments", segmentCount);
            
            // 计算航段数据（根据途经点实时计算，不再从数据库查询）
            double maxRisk = 0;
            int highestRiskSegment = 1;
            List<Map<String, Object>> segmentDataList = new ArrayList<>();
            List<Double> dangers = new ArrayList<>();
            
            double accumulatedDistance = 0.0;
            for (int i = 0; i < segmentCount; i++) {
                RouteWaypoint startWp = waypoints.get(i);
                RouteWaypoint endWp = waypoints.get(i + 1);
                
                double startLon = startWp.getLongitude().doubleValue();
                double startLat = startWp.getLatitude().doubleValue();
                double endLon = endWp.getLongitude().doubleValue();
                double endLat = endWp.getLatitude().doubleValue();
                
                // 计算当前航段长度
                double segmentLength = calculateDistance(startLon, startLat, endLon, endLat);
                accumulatedDistance += segmentLength;
                
                // 生成航段数据（使用与createRoute相同的逻辑）
                Map<String, Object> segData = generateSegmentData(
                    i + 1, startLon, startLat, endLon, endLat, 
                    segmentLength, accumulatedDistance
                );
                
                double risk = (Double) segData.get("risk");
                if (risk > maxRisk) {
                    maxRisk = risk;
                    highestRiskSegment = i + 1;
                }
                dangers.add(risk);
                
                // 调整segData结构以匹配前端期望
                Map<String, Object> adjustedSegData = new HashMap<>();
                adjustedSegData.put("segment", i + 1);
                adjustedSegData.put("distance", accumulatedDistance);
                adjustedSegData.put("risk", risk);
                adjustedSegData.put("windSpeed", segData.get("windSpeed"));
                adjustedSegData.put("windDir", segData.get("windDir"));
                adjustedSegData.put("windShear", segData.get("windShear"));
                adjustedSegData.put("turbulence", segData.get("turbulence"));
                adjustedSegData.put("rainfall", segData.get("rainfall"));
                adjustedSegData.put("pathCoordinates", segData.get("pathCoordinates"));
                adjustedSegData.put("startCoordinates", List.of(startLon, startLat));
                adjustedSegData.put("endCoordinates", List.of(endLon, endLat));
                
                segmentDataList.add(adjustedSegData);
            }
            
            map.put("averageRisk", route.getAverageRisk());
            map.put("highestRisk", maxRisk);
            map.put("highestRiskSegment", highestRiskSegment);
            map.put("segmentData", segmentDataList);
            map.put("dangers", dangers);
            
            // 构建途经点列表（waypoints变量已在前面的代码中获取）
            List<Map<String, Object>> waypointList = new ArrayList<>();
            for (RouteWaypoint wp : waypoints) {
                Map<String, Object> wpMap = new HashMap<>();
                wpMap.put("name", wp.getName());
                wpMap.put("longitude", wp.getLongitude());
                wpMap.put("latitude", wp.getLatitude());
                waypointList.add(wpMap);
            }
            map.put("waypoints", waypointList);
            
            // 时间信息
            map.put("startTime", route.getCreatedAt());
            map.put("endTime", route.getUpdatedAt());
            
            routeList.add(map);
        }

        long availableCount = routes.stream()
                .filter(r -> "available".equals(r.getStatus()))
                .count();

        Map<String, Object> result = new HashMap<>();
        result.put("routes", routeList);
        result.put("total", routes.size());
        result.put("available", (int) availableCount);
        
        return result;
    }

    /**
     * 获取航路详情
     */
    public Map<String, Object> getRouteDetail(String routeId) {
        log.debug("查询航线详情，航线ID: {}", routeId);
        Route route = routeMapper.selectById(routeId);
        if (route == null) {
            log.warn("航线不存在，ID: {}", routeId);
            return null;
        }
        
        // 获取途经点
        List<RouteWaypoint> waypoints = waypointMapper.selectList(
                new LambdaQueryWrapper<RouteWaypoint>()
                        .eq(RouteWaypoint::getRouteId, routeId)
                        .orderByAsc(RouteWaypoint::getSequence)
        );
        
        // 构建与createRoute返回一致的数据结构
        Map<String, Object> result = new HashMap<>();
        result.put("id", route.getId());
        result.put("name", route.getName());
        result.put("startName", route.getStartName());
        result.put("endName", route.getEndName());
        result.put("length", route.getDistance());
        result.put("averageRisk", route.getAverageRisk());
        
        // 根据途经点实时计算航段数据（不再从数据库查询）
        double totalLength = 0.0;
        double maxRisk = 0;
        int highestRiskSegment = 1;
        List<Map<String, Object>> segmentDataList = new ArrayList<>();
        List<Double> dangers = new ArrayList<>();
        
        for (int i = 0; i < waypoints.size() - 1; i++) {
            RouteWaypoint startWp = waypoints.get(i);
            RouteWaypoint endWp = waypoints.get(i + 1);
            
            double startLon = startWp.getLongitude().doubleValue();
            double startLat = startWp.getLatitude().doubleValue();
            double endLon = endWp.getLongitude().doubleValue();
            double endLat = endWp.getLatitude().doubleValue();
            
            // 计算当前航段长度
            double segmentLength = calculateDistance(startLon, startLat, endLon, endLat);
            totalLength += segmentLength;
            
            // 生成航段数据（使用与createRoute相同的逻辑）
            Map<String, Object> segData = generateSegmentData(
                i + 1, startLon, startLat, endLon, endLat, 
                segmentLength, totalLength
            );
            
            double risk = (Double) segData.get("risk");
            if (risk > maxRisk) {
                maxRisk = risk;
                highestRiskSegment = i + 1;
            }
            dangers.add(risk);
            
            segmentDataList.add(segData);
        }
        
        result.put("segments", segmentDataList.size());
        result.put("highestRisk", maxRisk);
        result.put("highestRiskSegment", highestRiskSegment);
        result.put("segmentData", segmentDataList);
        result.put("dangers", dangers.stream().map(r -> Math.round(r * 10)).collect(java.util.stream.Collectors.toList()));
        
        // 构建途经点数据
        List<Map<String, Object>> waypointList = new ArrayList<>();
        for (RouteWaypoint wp : waypoints) {
            Map<String, Object> wpMap = new HashMap<>();
            wpMap.put("name", wp.getName());
            wpMap.put("longitude", wp.getLongitude().doubleValue());
            wpMap.put("latitude", wp.getLatitude().doubleValue());
            wpMap.put("height", wp.getAltitude().doubleValue());
            waypointList.add(wpMap);
        }
        result.put("waypoints", waypointList);
        
        // 飞机型号和飞行高度
        if (route.getAircraftModel() != null) {
            result.put("aircraftModel", route.getAircraftModel());
        }
        if (route.getFlightHeight() != null) {
            result.put("flightHeight", route.getFlightHeight());
        }
        
        // 时间信息
        if (route.getStartTime() != null) {
            result.put("startTime", route.getStartTime().toString());
        }
        if (route.getEndTime() != null) {
            result.put("endTime", route.getEndTime().toString());
        }
        
        log.debug("航线详情查询成功，ID: {}, 航段数: {}, 途经点数: {}", 
            routeId, segmentDataList.size(), waypoints.size());
        
        return result;
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
     * 生成航段数据（模拟，未来可集成真实气象数据）
     */
    private Map<String, Object> generateSegmentData(int segmentNum, double startLon, double startLat, 
                                                   double endLon, double endLat, double segmentLength, 
                                                   double accumulatedDistance) {
        // 生成随机风险值（模拟，未来应根据真实气象数据计算）
        double baseRisk = 0.2 + Math.random() * 0.6;
        double risk = Math.min(1, Math.max(0, baseRisk));
        
        // 生成随机风速等气象数据
        double windSpeed = 3 + Math.random() * 12;
        int windDir = (int)(Math.random() * 360);
        double windShear = risk * 10;
        double turbulence = risk * 8 + Math.random() * 2;
        double rainfall = risk * 4 + Math.random() * 1;
        
        // 生成路径坐标（贝塞尔曲线效果）
        List<List<Double>> pathCoordinates = new ArrayList<>();
        pathCoordinates.add(List.of(startLon, startLat));
        
        double midLon = (startLon + endLon) / 2 + (Math.random() - 0.5) * 0.02;
        double midLat = (startLat + endLat) / 2 + (Math.random() - 0.5) * 0.02;
        pathCoordinates.add(List.of(midLon, midLat));
        pathCoordinates.add(List.of(endLon, endLat));
        
        Map<String, Object> segData = new HashMap<>();
        segData.put("segment", segmentNum);
        segData.put("distance", accumulatedDistance);
        segData.put("segmentLength", segmentLength);
        segData.put("risk", risk);
        segData.put("windSpeed", windSpeed);
        segData.put("windDir", windDir);
        segData.put("windShear", windShear);
        segData.put("turbulence", turbulence);
        segData.put("rainfall", rainfall);
        segData.put("startCoordinates", List.of(startLon, startLat));
        segData.put("endCoordinates", List.of(endLon, endLat));
        segData.put("pathCoordinates", pathCoordinates);
        
        return segData;
    }

    /**
     * 创建新航线（前端只传递基本数据，后端计算里程、风险等）
     */
    public Map<String, Object> createRoute(Map<String, Object> routeData) {
        try {
            // 0. 检查历史记录数量，最多保存配置的最大历史记录数
            List<Route> allRoutes = routeMapper.selectList(
                new LambdaQueryWrapper<Route>()
                    .eq(Route::getIsActive, true)
                    .orderByAsc(Route::getCreatedAt)  // 最旧的在前
            );
            
            int maxHistoryCount = routeConfig.getMaxHistoryCount() != null ? routeConfig.getMaxHistoryCount() : 5;
            
            if (allRoutes.size() >= maxHistoryCount) {
                // 删除最旧的一条，保持最多maxHistoryCount条历史记录
                Route oldest = allRoutes.get(0);
                routeMapper.deleteById(oldest.getId());
                // 由于 ON DELETE CASCADE 约束，关联的途经点会自动删除
                log.info("删除最旧航线记录，ID: {}, 名称: {}, 保持最多 {} 条记录", 
                    oldest.getId(), oldest.getName(), maxHistoryCount);
            }
            
            // 1. 验证基本数据
            if (!routeData.containsKey("startName") || !routeData.containsKey("startLon") || 
                !routeData.containsKey("startLat") || !routeData.containsKey("endName") ||
                !routeData.containsKey("endLon") || !routeData.containsKey("endLat")) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "缺少必要的起点或终点数据");
                return error;
            }
            
            // 提取起点数据
            String startName = routeData.get("startName").toString();
            double startLon = Double.parseDouble(routeData.get("startLon").toString());
            double startLat = Double.parseDouble(routeData.get("startLat").toString());
            
            // 提取终点数据
            String endName = routeData.get("endName").toString();
            double endLon = Double.parseDouble(routeData.get("endLon").toString());
            double endLat = Double.parseDouble(routeData.get("endLat").toString());
            
            // 提取途经点数据
            List<Map<String, Object>> waypointsInput = new ArrayList<>();
            if (routeData.containsKey("waypoints") && routeData.get("waypoints") instanceof List) {
                waypointsInput = (List<Map<String, Object>>) routeData.get("waypoints");
            }
            
            // 提取其他信息
            String aircraftModel = routeData.containsKey("aircraftModel") ? 
                routeData.get("aircraftModel").toString() : "未知型号";
            double flightHeight = routeData.containsKey("flightHeight") ? 
                Double.parseDouble(routeData.get("flightHeight").toString()) : 300.0;
            String startTime = routeData.containsKey("startTime") ? 
                routeData.get("startTime").toString() : java.time.LocalDateTime.now().toString();
            String endTime = routeData.containsKey("endTime") ? 
                routeData.get("endTime").toString() : java.time.LocalDateTime.now().plusHours(1).toString();
            
            // 2. 构建完整途经点列表（起点 + 途经点 + 终点）
            List<Map<String, Object>> allWaypoints = new ArrayList<>();
            
            // 起点
            Map<String, Object> startWp = new HashMap<>();
            startWp.put("name", startName);
            startWp.put("longitude", startLon);
            startWp.put("latitude", startLat);
            startWp.put("height", flightHeight);
            allWaypoints.add(startWp);
            
            // 途经点
            for (int i = 0; i < waypointsInput.size(); i++) {
                Map<String, Object> wpInput = waypointsInput.get(i);
                Map<String, Object> wp = new HashMap<>();
                wp.put("name", wpInput.containsKey("name") ? wpInput.get("name").toString() : "途经点" + (i + 1));
                wp.put("longitude", Double.parseDouble(wpInput.get("lon").toString()));
                wp.put("latitude", Double.parseDouble(wpInput.get("lat").toString()));
                wp.put("height", flightHeight);
                allWaypoints.add(wp);
            }
            
            // 终点
            Map<String, Object> endWp = new HashMap<>();
            endWp.put("name", endName);
            endWp.put("longitude", endLon);
            endWp.put("latitude", endLat);
            endWp.put("height", flightHeight);
            allWaypoints.add(endWp);
            
            // 3. 计算总距离和生成航段数据
            double totalLength = 0.0;
            List<Map<String, Object>> segmentDataList = new ArrayList<>();
            List<Double> risks = new ArrayList<>();
            
            for (int i = 0; i < allWaypoints.size() - 1; i++) {
                Map<String, Object> startWpCurr = allWaypoints.get(i);
                Map<String, Object> endWpCurr = allWaypoints.get(i + 1);
                
                double segStartLon = Double.parseDouble(startWpCurr.get("longitude").toString());
                double segStartLat = Double.parseDouble(startWpCurr.get("latitude").toString());
                double segEndLon = Double.parseDouble(endWpCurr.get("longitude").toString());
                double segEndLat = Double.parseDouble(endWpCurr.get("latitude").toString());
                
                // 计算当前航段长度
                double segmentLength = calculateDistance(segStartLon, segStartLat, segEndLon, segEndLat);
                totalLength += segmentLength;
                
                // 生成航段数据
                Map<String, Object> segData = generateSegmentData(
                    i + 1, segStartLon, segStartLat, segEndLon, segEndLat, 
                    segmentLength, totalLength
                );
                
                segmentDataList.add(segData);
                risks.add((Double) segData.get("risk"));
            }
            
            // 4. 计算风险统计
            double averageRisk = risks.stream().mapToDouble(Double::doubleValue).average().orElse(0.5);
            double highestRisk = risks.stream().mapToDouble(Double::doubleValue).max().orElse(0.5);
            int highestRiskSegment = risks.indexOf(highestRisk) + 1;
            
            // 5. 创建航线基本信息
            Route route = new Route();
            String routeId = "route-" + System.currentTimeMillis();
            route.setId(routeId);
            route.setName(startName + "-" + endName);
            route.setStartName(startName);
            route.setEndName(endName);
            route.setDistance(totalLength);
            route.setAverageRisk(averageRisk);
            route.setStatus("available");
            route.setIsActive(true);
            route.setAircraftModel(aircraftModel);
            route.setFlightHeight(flightHeight);
            
            // 解析时间字符串为LocalDateTime
            try {
                java.time.LocalDateTime startDateTime = java.time.LocalDateTime.parse(startTime.replace("Z", ""));
                route.setStartTime(startDateTime);
            } catch (Exception e) {
                route.setStartTime(java.time.LocalDateTime.now());
            }
            
            try {
                java.time.LocalDateTime endDateTime = java.time.LocalDateTime.parse(endTime.replace("Z", ""));
                route.setEndTime(endDateTime);
            } catch (Exception e) {
                route.setEndTime(java.time.LocalDateTime.now().plusHours(1));
            }
            
            route.setCreatedAt(java.time.LocalDateTime.now());
            route.setUpdatedAt(java.time.LocalDateTime.now());
            
            routeMapper.insert(route);
            
            // 6. 创建途经点（保存到数据库）
            for (int i = 0; i < allWaypoints.size(); i++) {
                Map<String, Object> wp = allWaypoints.get(i);
                RouteWaypoint waypoint = new RouteWaypoint();
                waypoint.setId(routeId + "-wp-" + i);
                waypoint.setRouteId(routeId);
                waypoint.setSequence(i);
                waypoint.setName(wp.get("name").toString());
                waypoint.setLongitude(Double.parseDouble(wp.get("longitude").toString()));
                waypoint.setLatitude(Double.parseDouble(wp.get("latitude").toString()));
                waypoint.setAltitude(Double.parseDouble(wp.get("height").toString()));
                waypoint.setCreatedAt(java.time.LocalDateTime.now());
                
                waypointMapper.insert(waypoint);
            }
            
            // 7. 航段数据不再保存到数据库，每次查询时实时生成
            
            // 8. 构建返回数据（包含完整航线信息，供前端直接显示）
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("routeId", routeId);
            result.put("message", "航线创建成功");
            
            // 返回完整航线数据，供前端直接使用
            Map<String, Object> routeInfo = new HashMap<>();
            routeInfo.put("id", routeId);
            routeInfo.put("name", startName + "-" + endName);
            routeInfo.put("startName", startName);
            routeInfo.put("endName", endName);
            routeInfo.put("length", totalLength);
            routeInfo.put("segments", segmentDataList.size());
            routeInfo.put("averageRisk", averageRisk);
            routeInfo.put("highestRisk", highestRisk);
            routeInfo.put("highestRiskSegment", highestRiskSegment);
            routeInfo.put("segmentData", segmentDataList);
            routeInfo.put("waypoints", allWaypoints);
            routeInfo.put("dangers", risks.stream().map(r -> Math.round(r * 10)).collect(java.util.stream.Collectors.toList()));
            routeInfo.put("aircraftModel", aircraftModel);
            routeInfo.put("flightHeight", flightHeight);
            routeInfo.put("startTime", startTime);
            routeInfo.put("endTime", endTime);
            
            result.put("route", routeInfo);
            
            log.info("成功创建航线，ID: {}, 名称: {}, 距离: {} km, 途经点: {} 个", 
                routeId, route.getName(), totalLength, allWaypoints.size());
            
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "航线创建失败: " + e.getMessage());
            e.printStackTrace();
            return result;
        }
    }

    /**
     * 分析航线风险
     */
    public Map<String, Object> analyzeRouteRisk(String routeId, Map<String, Object> params) {
        try {
            Route route = routeMapper.selectById(routeId);
            if (route == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "航线不存在");
                return error;
            }
            
            // 获取当前分析时间点（支持动态时间轴）
            java.time.LocalDateTime currentAnalysisTime;
            if (params != null && params.containsKey("currentTime")) {
                try {
                    String currentTimeStr = params.get("currentTime").toString();
                    currentAnalysisTime = java.time.LocalDateTime.parse(currentTimeStr.replace("Z", ""));
                } catch (Exception e) {
                    // 解析失败，使用航线起始时间
                    currentAnalysisTime = route.getStartTime() != null ? 
                        route.getStartTime() : java.time.LocalDateTime.now();
                }
            } else {
                // 没有指定时间，使用航线起始时间
                currentAnalysisTime = route.getStartTime() != null ? 
                    route.getStartTime() : java.time.LocalDateTime.now();
            }
            
            // 获取航线途经点
            List<RouteWaypoint> waypoints = waypointMapper.selectList(
                    new LambdaQueryWrapper<RouteWaypoint>()
                            .eq(RouteWaypoint::getRouteId, routeId)
                            .orderByAsc(RouteWaypoint::getSequence)
            );
            
            // 根据途经点实时生成航段数据（不再从数据库查询）
            // 实际项目中应该调用气象服务，分析航线经过空域的气象数据
            int segmentCount = Math.max(0, waypoints.size() - 1);
            List<Map<String, Object>> segments = new ArrayList<>();
            
            double accumulatedDistance = 0.0;
            for (int i = 0; i < segmentCount; i++) {
                RouteWaypoint startWp = waypoints.get(i);
                RouteWaypoint endWp = waypoints.get(i + 1);
                
                double startLon = startWp.getLongitude().doubleValue();
                double startLat = startWp.getLatitude().doubleValue();
                double endLon = endWp.getLongitude().doubleValue();
                double endLat = endWp.getLatitude().doubleValue();
                
                // 计算当前航段长度
                double segmentLength = calculateDistance(startLon, startLat, endLon, endLat);
                accumulatedDistance += segmentLength;
                
                // 生成航段数据（模拟）
                Map<String, Object> segData = generateSegmentData(
                    i + 1, startLon, startLat, endLon, endLat, 
                    segmentLength, accumulatedDistance
                );
                
                // 转换为简化格式用于分析
                Map<String, Object> seg = new HashMap<>();
                seg.put("segment", i + 1);
                seg.put("distance", segmentLength);
                seg.put("windSpeed", segData.get("windSpeed"));
                seg.put("windDir", segData.get("windDir"));
                seg.put("risk", segData.get("risk"));
                
                // 根据风险值设置风险等级
                double risk = (Double) segData.get("risk");
                String riskLevel = risk < 0.3 ? "low" : (risk < 0.7 ? "medium" : "high");
                seg.put("riskLevel", riskLevel);
                
                segments.add(seg);
            }
            
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("routeId", routeId);
            analysis.put("routeName", route.getName());
            analysis.put("analysisTime", java.time.LocalDateTime.now().toString());
            analysis.put("currentAnalysisTime", currentAnalysisTime.toString());
            
            // 航线时间信息
            if (route.getStartTime() != null) {
                analysis.put("routeStartTime", route.getStartTime().toString());
            }
            if (route.getEndTime() != null) {
                analysis.put("routeEndTime", route.getEndTime().toString());
            }
            
            // 风险维度分析
            List<Map<String, Object>> riskDimensions = new ArrayList<>();
            
            // 1. 风速风险
            Map<String, Object> windRisk = new HashMap<>();
            windRisk.put("dimension", "风速风险");
            windRisk.put("level", "medium");
            windRisk.put("score", 6.5);
            windRisk.put("description", "航线中存在3个航段风速超过8m/s");
            riskDimensions.add(windRisk);
            
            // 2. 能见度风险
            Map<String, Object> visibilityRisk = new HashMap<>();
            visibilityRisk.put("dimension", "能见度风险");
            visibilityRisk.put("level", "low");
            visibilityRisk.put("score", 3.2);
            visibilityRisk.put("description", "能见度良好，平均大于10km");
            riskDimensions.add(visibilityRisk);
            
            // 3. 降水风险
            Map<String, Object> precipitationRisk = new HashMap<>();
            precipitationRisk.put("dimension", "降水风险");
            precipitationRisk.put("level", "high");
            precipitationRisk.put("score", 8.1);
            precipitationRisk.put("description", "第2、4航段有中到大雨");
            riskDimensions.add(precipitationRisk);
            
            // 4. 湍流风险
            Map<String, Object> turbulenceRisk = new HashMap<>();
            turbulenceRisk.put("dimension", "湍流风险");
            turbulenceRisk.put("level", "medium");
            turbulenceRisk.put("score", 5.7);
            turbulenceRisk.put("description", "山区航段存在中度湍流");
            riskDimensions.add(turbulenceRisk);
            
            analysis.put("riskDimensions", riskDimensions);
            
            // 综合风险评估
            Map<String, Object> overallAssessment = new HashMap<>();
            overallAssessment.put("overallRisk", "medium");
            overallAssessment.put("overallScore", 6.0);
            overallAssessment.put("safetyLevel", "可飞行");
            overallAssessment.put("recommendation", "建议避开第2、4航段，或调整飞行高度至500米以上");
            analysis.put("overallAssessment", overallAssessment);
            
            // 航段详细分析
            List<Map<String, Object>> segmentAnalysis = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                Map<String, Object> seg = segments.get(i);
                Map<String, Object> segAnalysis = new HashMap<>();
                segAnalysis.put("segment", i + 1);
                segAnalysis.put("distance", seg.get("distance"));
                segAnalysis.put("startWaypoint", waypoints.size() > i ? waypoints.get(i).getName() : "未知");
                segAnalysis.put("endWaypoint", waypoints.size() > i + 1 ? waypoints.get(i + 1).getName() : "未知");
                segAnalysis.put("windSpeed", seg.get("windSpeed"));
                segAnalysis.put("riskLevel", seg.get("riskLevel"));
                segAnalysis.put("recommendations", getSegmentRecommendationsFromMap(seg, i));
                
                segmentAnalysis.add(segAnalysis);
            }
            analysis.put("segmentAnalysis", segmentAnalysis);
            
            // 应对措施
            List<Map<String, Object>> measures = new ArrayList<>();
            measures.add(createMeasure("调整飞行高度", "将飞行高度提升至500米以上，避开低空湍流", "high"));
            measures.add(createMeasure("调整飞行速度", "在降水航段将飞行速度降低20%", "medium"));
            measures.add(createMeasure("备用航线", "准备北部绕行航线作为备用", "low"));
            analysis.put("measures", measures);
            
            // 备选航线建议
            List<Map<String, Object>> alternativeRoutes = new ArrayList<>();
            alternativeRoutes.add(createAlternativeRoute("北部绕行航线", "low", 22.3, "避开所有高风险区域"));
            alternativeRoutes.add(createAlternativeRoute("南部沿海航线", "medium", 25.7, "路程稍远但气象条件更好"));
            analysis.put("alternativeRoutes", alternativeRoutes);
            
            // 风险概率图表数据（时间维度）
            Map<String, Object> riskChartData = generateRiskChartData(route, segments, waypoints);
            analysis.put("riskChart", riskChartData);
            
            log.info("航线风险分析完成，航线ID: {}, 分析时间点: {}, 航段数: {}", 
                routeId, currentAnalysisTime, segments.size());
            
            analysis.put("success", true);
            return analysis;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "航线分析失败: " + e.getMessage());
            return result;
        }
    }
    

    
    private List<String> getSegmentRecommendationsFromMap(Map<String, Object> segment, int index) {
        List<String> recommendations = new ArrayList<>();
        
        String riskLevel = (String) segment.get("riskLevel");
        if ("high".equals(riskLevel)) {
            recommendations.add("高风险航段，建议绕行或调整飞行高度");
        } else if ("medium".equals(riskLevel)) {
            recommendations.add("中风险航段，注意风速变化");
        } else {
            recommendations.add("低风险航段，可正常飞行");
        }
        
        double windSpeed = ((Number) segment.get("windSpeed")).doubleValue();
        if (windSpeed > 8) {
            recommendations.add("风速较大，建议降低飞行速度");
        }
        
        if (index == 1 || index == 3) { // 模拟特定航段有降水
            recommendations.add("可能有降水，注意能见度影响");
        }
        
        return recommendations;
    }
    
    private Map<String, Object> createMeasure(String title, String description, String priority) {
        Map<String, Object> measure = new HashMap<>();
        measure.put("title", title);
        measure.put("description", description);
        measure.put("priority", priority);
        return measure;
    }
    
    private Map<String, Object> createAlternativeRoute(String name, String riskLevel, double distance, String description) {
        Map<String, Object> altRoute = new HashMap<>();
        altRoute.put("name", name);
        altRoute.put("riskLevel", riskLevel);
        altRoute.put("distance", distance);
        altRoute.put("description", description);
        altRoute.put("estimatedTime", Math.round(distance * 0.8) + "分钟"); // 估算时间
        return altRoute;
    }

    /**
     * 生成风险概率图表数据（时间维度）
     */
    private Map<String, Object> generateRiskChartData(Route route, List<Map<String, Object>> segments, List<RouteWaypoint> waypoints) {
        Map<String, Object> chartData = new HashMap<>();
        
        // 时间轴数据
        List<String> timeLabels = new ArrayList<>();
        List<Double> riskValues = new ArrayList<>();
        
        // 使用航线起始时间，如果没有则使用当前时间
        java.time.LocalDateTime startTime = route.getStartTime() != null ? 
            route.getStartTime() : java.time.LocalDateTime.now();
        java.time.LocalDateTime endTime = route.getEndTime() != null ? 
            route.getEndTime() : startTime.plusHours(2); // 默认2小时
        
        // 计算航线总时长（分钟）
        long totalMinutes = java.time.Duration.between(startTime, endTime).toMinutes();
        if (totalMinutes <= 0) {
            totalMinutes = 120; // 默认2小时
        }
        
        // 生成时间点（每15分钟一个点）
        int pointCount = Math.max(2, (int) (totalMinutes / 15) + 1);
        for (int i = 0; i < pointCount; i++) {
            java.time.LocalDateTime time = startTime.plusMinutes(i * 15L);
            timeLabels.add(String.format("%02d:%02d", time.getHour(), time.getMinute()));
            
            // 计算当前时间点的风险值
            // 风险值随时间变化：早上风险较低，下午风险较高
            double hourFactor = 0.5 + 0.3 * Math.sin(Math.PI * time.getHour() / 12.0);
            
            // 基于航段风险计算平均风险
            double segmentRiskSum = 0;
            for (Map<String, Object> seg : segments) {
                double risk = ((Number) seg.get("risk")).doubleValue();
                segmentRiskSum += risk;
            }
            double avgSegmentRisk = segments.isEmpty() ? 0.5 : segmentRiskSum / segments.size();
            
            // 添加随机波动（基于时间作为种子，确保相同时间返回相同结果）
            long seed = time.toEpochSecond(java.time.ZoneOffset.UTC);
            Random random = new Random(seed);
            double randomFactor = 0.9 + 0.2 * random.nextDouble();
            
            // 计算最终风险值 (0-1)
            double riskValue = avgSegmentRisk * hourFactor * randomFactor;
            riskValue = Math.min(1.0, Math.max(0.0, riskValue));
            
            riskValues.add(riskValue);
        }
        
        chartData.put("timeLabels", timeLabels);
        chartData.put("riskValues", riskValues);
        
        // 风险统计
        double maxRisk = riskValues.stream().mapToDouble(Double::doubleValue).max().orElse(0.5);
        double minRisk = riskValues.stream().mapToDouble(Double::doubleValue).min().orElse(0.3);
        double avgRisk = riskValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.5);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("max", maxRisk);
        stats.put("min", minRisk);
        stats.put("average", avgRisk);
        stats.put("durationMinutes", totalMinutes);
        
        chartData.put("stats", stats);
        
        return chartData;
    }

    /**
     * 清空航路历史记录
     */
    public Map<String, Object> clearHistory() {
        try {
            // 删除所有航路记录，由于外键约束会级联删除相关表数据
            int deletedCount = routeMapper.delete(null); // 删除所有记录
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "历史记录已清空，共删除 " + deletedCount + " 条航路记录");
            result.put("deletedCount", deletedCount);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "清空历史记录失败: " + e.getMessage());
            return result;
        }
    }
}
