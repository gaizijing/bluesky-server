package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.config.RegionConfig;
import com.bluesky.entity.MonitoringPoint;
import com.bluesky.service.MonitoringPointService;
import com.bluesky.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 常规气象数据
 * 
 */
@Tag(name = "气象数据", description = "实时天气、风向趋势、3D风场、微尺度天气等接口")
@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final MonitoringPointService monitoringPointService;
    private final RegionConfig regionConfig;

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
     * 获取地理空间热力图数据（用于Cesium地图）
     * GET /api/weather/heatmap/geo?pointId=point-1&time=2026-02-28T15:33:00
     */
    @Operation(summary = "获取地理空间热力图数据（地图）", description = "获取区域风险分布数据，用于Cesium地图热力图展示")
    @GetMapping("/heatmap/geo")
    public Result<Map<String, Object>> getWeatherHeatmapGeo(
            @Parameter(description = "监测点ID") @RequestParam String pointId,
            @Parameter(description = "时间，ISO格式") @RequestParam(required = false) String time) {
        
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
        
        return Result.success(weatherService.getWeatherHeatmapGeo(bounds, time, pointId));
    }


    /**
     * 获取全市范围热力图数据
     * GET /api/weather/heatmap/citywide?totalHours=3&resolution=medium&baseTime=2026-03-09T10:00:00
     */
    @Operation(summary = "获取全市范围热力图数据", description = "获取全市范围内的风险分布热力图数据，支持时间参数")
    @GetMapping("/heatmap/citywide")
    public Result<Map<String, Object>> getCitywideHeatmap() {
        return Result.success(weatherService.getCitywideHeatmap());
    }

    /**
     * 获取地区配置信息
     * GET /api/weather/region-config
     */
    @Operation(summary = "获取地区配置信息", description = "获取当前系统配置的地区信息，包括地区名称和边界坐标")
    @GetMapping("/region-config")
    public Result<Map<String, Object>> getRegionConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("defaultName", regionConfig.getDefaultName());
        Map<String, Double> bounds = new HashMap<>();
        bounds.put("west", regionConfig.getBounds().getWest());
        bounds.put("east", regionConfig.getBounds().getEast());
        bounds.put("south", regionConfig.getBounds().getSouth());
        bounds.put("north", regionConfig.getBounds().getNorth());
        config.put("bounds", bounds);
        return Result.success(config);
    }

    /**
     * 获取天气预测趋势数据
     * GET /weather/forecast-trend?pointId=point-1
     */
    @Operation(summary = "获取天气预测趋势数据", description = "调用 Open-Meteo API 获取 15 分钟间隔的天气预测数据，包括降水量、风速、能见度")
    @GetMapping("/forecast-trend")
    public Result<Map<String, Object>> getWeatherForecastTrend(
            @Parameter(description = "重点关注区域ID") @RequestParam String pointId) {
        return Result.success(weatherService.getWeatherForecastTrend(pointId));
    }
}
