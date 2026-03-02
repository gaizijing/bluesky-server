package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.entity.MonitoringPoint;
import com.bluesky.service.MonitoringPointService;
import com.bluesky.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 气象数据控制器
 * 涵盖实时天气、风向趋势、3D风场、微尺度天气
 */
@Tag(name = "气象数据", description = "实时天气、风向趋势、3D风场、微尺度天气等接口")
@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final MonitoringPointService monitoringPointService;

    /**
     * 获取重点关注区域实时气象数据
     * GET /api/weather/realtime?pointId=point-1
     */
    @Operation(summary = "获取实时气象数据", description = "获取指定重点关注区域的最新实时气象信息")
    @GetMapping("/realtime")
    public Result<Map<String, Object>> getRealtimeWeather(
            @Parameter(description = "重点关注区域ID") @RequestParam String pointId) {
        return Result.success(weatherService.getRealtimeWeather(pointId));
    }

    /**
     * 获取风向趋势数据(用于折线图)
     * GET /api/weather/wind-trend?pointId=point-1&timeRange=2025-11-03
     * 00:00:00,2025-11-03 23:59:59
     */
    @Operation(summary = "获取风向趋势数据", description = "获取指定时间范围内的风速风向趋势数据,用于图表展示")
    @GetMapping("/wind-trend")
    public Result<Map<String, Object>> getWindTrend(
            @Parameter(description = "重点关注区域ID") @RequestParam String pointId,
            @Parameter(description = "时间范围，格式: 开始时间,结束时间") @RequestParam String timeRange) {
        return Result.success(weatherService.getWindTrend(pointId, timeRange));
    }

    /**
     * 获取3D风场数据(Cesium粒子渲染)
     * GET /api/weather/wind-field?timeRange=...&height=100
     */
    @Operation(summary = "获取3D风场数据", description = "获取用于Cesium粒子系统渲染的三维风场矢量数据")
    @GetMapping("/wind-field")
    public Result<Map<String, Object>> getWindField(
            @Parameter(description = "时间范围") @RequestParam(required = false) String timeRange,
            @Parameter(description = "指定高度层(米),不传则返回所有高度层") @RequestParam(required = false) Integer height) {
        return Result.success(weatherService.getWindField(timeRange, height));
    }

    /**
     * 获取3D风场数据(简化接口，无参数)
     * GET /api/weather/wind
     */
    @Operation(summary = "获取3D风场数据(简化)", description = "获取最新的三维风场矢量数据")
    @GetMapping("/wind")
    public Result<Map<String, Object>> getWindFieldSimple() {
        return Result.success(weatherService.getWindField(null, null));
    }

    /**
     * 获取微尺度天气数据(热力图)
     * GET /api/weather/microscale?region=青岛中心区
     */
    @Operation(summary = "获取微尺度天气数据", description = "获取精细化网格化微尺度天气数据，用于风险热力图展示")
    @GetMapping("/microscale")
    public Result<Map<String, Object>> getMicroscaleWeather(
            @Parameter(description = "区域名称") @RequestParam(required = false) String region,
            @Parameter(description = "时间范围") @RequestParam(required = false) String timeRange) {
        return Result.success(weatherService.getMicroscaleWeather(region, timeRange));
    }

    /**
     * 获取天气预测热力图数据（用于ECharts图表）
     * GET /api/weather/heatmap/chart?pointId=POINT003&timeRange=3h&resolution=medium
     */
    @Operation(summary = "获取天气预测热力图数据（图表）", description = "获取时间-高度风险矩阵数据，用于ECharts热力图展示")
    @GetMapping("/heatmap/chart")
    public Result<Map<String, Object>> getWeatherHeatmapChart(
            @Parameter(description = "监测点ID") @RequestParam(required = false) String pointId,
            @Parameter(description = "时间范围，如：3h、6h、12h") @RequestParam(required = false, defaultValue = "3h") String timeRange,
            @Parameter(description = "分辨率：low/medium/high") @RequestParam(required = false, defaultValue = "medium") String resolution,
            @Parameter(description = "是否用于航路分析") @RequestParam(required = false, defaultValue = "false") Boolean forRouteAnalysis) {
        return Result.success(weatherService.getWeatherHeatmapChart(pointId, timeRange, resolution, forRouteAnalysis));
    }

    /**
     * 获取地理空间热力图数据（用于Cesium地图）
     * GET /api/weather/heatmap/geo?pointId=point-1&time=2026-02-28T15:33:00&resolution=medium
     */
    @Operation(summary = "获取地理空间热力图数据（地图）", description = "获取区域风险分布数据，用于Cesium地图热力图展示")
    @GetMapping("/heatmap/geo")
    public Result<Map<String, Object>> getWeatherHeatmapGeo(
            @Parameter(description = "监测点ID") @RequestParam String pointId,
            @Parameter(description = "时间，ISO格式") @RequestParam(required = false) String time,
            @Parameter(description = "分辨率：low/medium/high") @RequestParam(required = false, defaultValue = "medium") String resolution) {
        
        // 根据 pointId 查询监测点信息，获取边界框
        MonitoringPoint point = monitoringPointService.getById(pointId);
        
        // 检查边界框数据是否完整
        if (point.getBboxMinLng() == null || point.getBboxMinLat() == null || 
            point.getBboxMaxLng() == null || point.getBboxMaxLat() == null) {
            throw new IllegalArgumentException("监测点 " + pointId + " 的边界框数据不完整，无法生成热力图");
        }
        
        // 构建边界框字符串
        String bounds = String.format("[%s,%s,%s,%s]", 
            point.getBboxMinLng(), point.getBboxMinLat(),
            point.getBboxMaxLng(), point.getBboxMaxLat());
        
        return Result.success(weatherService.getWeatherHeatmapGeo(bounds, time, resolution, pointId));
    }

    /**
     * 批量获取天气热力图数据
     * GET /api/weather/heatmap/batch?areaIds=point-1,point-2,point-3&timeRange=3h&resolution=medium
     */
    @Operation(summary = "批量获取天气热力图数据", description = "批量获取多个区域的天气热力图数据，用于航路分析")
    @GetMapping("/heatmap/batch")
    public Result<Map<String, Object>> getBatchWeatherHeatmap(
            @Parameter(description = "区域ID列表，逗号分隔") @RequestParam String areaIds,
            @Parameter(description = "时间范围") @RequestParam(required = false, defaultValue = "3h") String timeRange,
            @Parameter(description = "分辨率") @RequestParam(required = false, defaultValue = "medium") String resolution) {
        return Result.success(weatherService.getBatchWeatherHeatmap(areaIds, timeRange, resolution));
    }
}
