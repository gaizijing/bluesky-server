package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.Route;
import com.bluesky.entity.RouteSegment;
import com.bluesky.entity.RouteWaypoint;
import com.bluesky.mapper.RouteMapper;
import com.bluesky.mapper.RouteSegmentMapper;
import com.bluesky.mapper.RouteWaypointMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 航路分析服务
 */
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteMapper routeMapper;
    private final RouteWaypointMapper waypointMapper;
    private final RouteSegmentMapper segmentMapper;

    /**
     * 获取航路列表
     */
    public Map<String, Object> getRouteList() {
        List<Route> routes = routeMapper.selectList(
                new LambdaQueryWrapper<Route>()
                        .eq(Route::getIsActive, true)
                        .orderByAsc(Route::getCreatedAt)
        );

        List<Map<String, Object>> routeList = new ArrayList<>();
        for (Route route : routes) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", route.getId());
            map.put("name", route.getStartName() + "-" + route.getEndName());
            map.put("startName", route.getStartName());
            map.put("endName", route.getEndName());
            map.put("length", route.getDistance());
            
            // 获取航段信息
            List<RouteSegment> segments = segmentMapper.selectList(
                    new LambdaQueryWrapper<RouteSegment>()
                            .eq(RouteSegment::getRouteId, route.getId())
                            .orderByAsc(RouteSegment::getSequence)
            );
            
            int segmentCount = segments.size();
            map.put("segments", segmentCount);
            
            // 计算最高风险
            double maxRisk = 0;
            int highestRiskSegment = 1;
            List<Map<String, Object>> segmentDataList = new ArrayList<>();
            List<Double> dangers = new ArrayList<>();
            
            // 获取途经点用于生成路径坐标
            List<RouteWaypoint> waypointsForPath = waypointMapper.selectList(
                    new LambdaQueryWrapper<RouteWaypoint>()
                            .eq(RouteWaypoint::getRouteId, route.getId())
                            .orderByAsc(RouteWaypoint::getSequence)
            );
            
            for (int i = 0; i < segments.size(); i++) {
                RouteSegment seg = segments.get(i);
                double risk = seg.getRiskLevel().equals("low") ? 0.2 : 
                             (seg.getRiskLevel().equals("medium") ? 0.5 : 0.8);
                if (risk > maxRisk) {
                    maxRisk = risk;
                    highestRiskSegment = i + 1;
                }
                dangers.add(risk);
                
                Map<String, Object> segData = new HashMap<>();
                segData.put("segment", i + 1);
                segData.put("distance", seg.getDistance());
                segData.put("risk", risk);
                segData.put("windSpeed", seg.getWindSpeed());
                segData.put("windDir", 0);
                segData.put("windShear", risk * 10);
                segData.put("turbulence", risk * 8);
                segData.put("rainfall", risk * 4);
                
                // 生成路径坐标
                List<List<Double>> pathCoordinates = new ArrayList<>();
                if (waypointsForPath.size() > i + 1) {
                    RouteWaypoint startWp = waypointsForPath.get(i);
                    RouteWaypoint endWp = waypointsForPath.get(i + 1);
                    
                    // 起点
                    pathCoordinates.add(List.of(startWp.getLongitude().doubleValue(), startWp.getLatitude().doubleValue()));
                    
                    // 中间点（贝塞尔曲线效果）
                    double midLon = (startWp.getLongitude().doubleValue() + endWp.getLongitude().doubleValue()) / 2 + (Math.random() - 0.5) * 0.02;
                    double midLat = (startWp.getLatitude().doubleValue() + endWp.getLatitude().doubleValue()) / 2 + (Math.random() - 0.5) * 0.02;
                    pathCoordinates.add(List.of(midLon, midLat));
                    
                    // 终点
                    pathCoordinates.add(List.of(endWp.getLongitude().doubleValue(), endWp.getLatitude().doubleValue()));
                }
                segData.put("pathCoordinates", pathCoordinates);
                segData.put("startCoordinates", waypointsForPath.size() > i ? 
                    List.of(waypointsForPath.get(i).getLongitude().doubleValue(), waypointsForPath.get(i).getLatitude().doubleValue()) : 
                    List.of(0.0, 0.0));
                segData.put("endCoordinates", waypointsForPath.size() > i + 1 ? 
                    List.of(waypointsForPath.get(i + 1).getLongitude().doubleValue(), waypointsForPath.get(i + 1).getLatitude().doubleValue()) : 
                    List.of(0.0, 0.0));
                
                segmentDataList.add(segData);
            }
            
            map.put("averageRisk", route.getAverageRisk());
            map.put("highestRisk", maxRisk);
            map.put("highestRiskSegment", highestRiskSegment);
            map.put("segmentData", segmentDataList);
            map.put("dangers", dangers);
            
            // 获取途经点
            List<RouteWaypoint> waypoints = waypointMapper.selectList(
                    new LambdaQueryWrapper<RouteWaypoint>()
                            .eq(RouteWaypoint::getRouteId, route.getId())
                            .orderByAsc(RouteWaypoint::getSequence)
            );
            
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
        Route route = routeMapper.selectById(routeId);
        if (route == null) {
            return null;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("routeId", route.getId());
        result.put("routeName", route.getName());
        
        // 获取航段信息
        List<RouteSegment> segments = segmentMapper.selectList(
                new LambdaQueryWrapper<RouteSegment>()
                        .eq(RouteSegment::getRouteId, routeId)
                        .orderByAsc(RouteSegment::getSequence)
        );
        
        List<Map<String, Object>> weatherAlongRoute = new ArrayList<>();
        for (RouteSegment segment : segments) {
            Map<String, Object> segMap = new HashMap<>();
            segMap.put("segment", "航段" + segment.getSequence());
            segMap.put("wind", segment.getWindSpeed() + "m/s " + segment.getWindDirection());
            segMap.put("visibility", segment.getVisibility() + "km");
            segMap.put("precipitation", segment.getPrecipitation());
            weatherAlongRoute.add(segMap);
        }
        result.put("weatherAlongRoute", weatherAlongRoute);
        
        // 风险评估
        Map<String, Object> riskAssessment = new HashMap<>();
         String overallRisk = switch ((int)(route.getAverageRisk().doubleValue() * 10)) {
            case 0, 1, 2 -> "低";
             case 3, 4, 5 -> "中";
           default -> "高";
        };
        riskAssessment.put("overallRisk", overallRisk);

        List<Map<String, Object>> factors = new ArrayList<>();
        factors.add(createRiskFactor("风速", "3.5m/s", "低"));
        factors.add(createRiskFactor("能见度", "9.2km", "低"));
        factors.add(createRiskFactor("降水量", "0mm", "低"));
        riskAssessment.put("factors", factors);
        
        result.put("riskAssessment", riskAssessment);
        
        // 建议
        List<String> recommendations = new ArrayList<>();
        recommendations.add("建议飞行高度：300-500米");
        recommendations.add("建议飞行速度：60-80km/h");
        recommendations.add("注意中段风力变化");
        result.put("recommendations", recommendations);
        
        return result;
    }

    private Map<String, Object> createRiskFactor(String factor, String value, String risk) {
        Map<String, Object> map = new HashMap<>();
        map.put("factor", factor);
        map.put("value", value);
        map.put("risk", risk);
        return map;
    }
}
