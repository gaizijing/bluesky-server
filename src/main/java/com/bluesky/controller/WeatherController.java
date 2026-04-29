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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 天气相关接口。
 */
@Tag(name = "天气接口", description = "提供实时天气、热力图和趋势预测数据")
@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final MonitoringPointService monitoringPointService;
    private final RegionConfig regionConfig;

    /**
     * 获取实时天气。
     * GET /weather/realtime?pointId=point-1
     */
    @Operation(summary = "获取实时天气", description = "根据点位 ID 返回实时天气数据")
    @GetMapping("/realtime")
    public Result<Map<String, Object>> getRealtimeWeather(
            @Parameter(description = "点位 ID") @RequestParam String pointId) {
        return Result.success(weatherService.getRealtimeWeather(pointId));
    }

    /**
     * 获取点位级热力图。
     * GET /weather/heatmap/geo?pointId=point-1&time=2026-02-28T15:33:00
     */
    @Operation(summary = "获取点位级热力图", description = "读取点位网格数据并返回 Cesium 热力图点集")
    @GetMapping("/heatmap/geo")
    public Result<Map<String, Object>> getWeatherHeatmapGeo(
            @Parameter(description = "点位 ID") @RequestParam String pointId,
            @Parameter(description = "时间，ISO 格式，可选") @RequestParam(required = false) String time) {

        MonitoringPoint point = monitoringPointService.getById(pointId);
        if (point == null) {
            throw new IllegalArgumentException("未找到点位：" + pointId);
        }

        if (point.getBboxMinLng() == null || point.getBboxMinLat() == null
                || point.getBboxMaxLng() == null || point.getBboxMaxLat() == null) {
            throw new IllegalArgumentException("点位 " + pointId + " 缺少边界框 bbox 配置，无法生成热力图");
        }

        String bounds = String.format("[%s,%s,%s,%s]",
                point.getBboxMinLng(), point.getBboxMinLat(),
                point.getBboxMaxLng(), point.getBboxMaxLat());

        return Result.success(weatherService.getWeatherHeatmapGeo(bounds, time, pointId));
    }

    /**
     * 获取城市级热力图。
     * GET /weather/heatmap/citywide
     */
    @Operation(summary = "获取城市级连续热力图", description = "聚合全市点位并通过 IDW 插值返回连续热力图")
    @GetMapping("/heatmap/citywide")
    public Result<Map<String, Object>> getCitywideHeatmap() {
        return Result.success(weatherService.getCitywideHeatmap());
    }

    /**
     * 获取默认区域配置。
     * GET /weather/region-config
     */
    @Operation(summary = "获取区域配置", description = "返回默认区域名称和经纬度范围")
    @GetMapping("/region-config")
    public Result<Map<String, Object>> getRegionConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("defaultName", regionConfig.getDefaultName());
        config.put("modelUrl", regionConfig.getModelUrl());

        Map<String, Double> bounds = new HashMap<>();
        bounds.put("west", regionConfig.getBounds().getWest());
        bounds.put("east", regionConfig.getBounds().getEast());
        bounds.put("south", regionConfig.getBounds().getSouth());
        bounds.put("north", regionConfig.getBounds().getNorth());

        config.put("bounds", bounds);
        return Result.success(config);
    }

    /**
     * 获取天气趋势预测。
     * GET /weather/forecast-trend?pointId=point-1
     */
    @Operation(summary = "获取天气趋势预测", description = "基于 Open-Meteo 返回未来 15 天趋势预测")
    @GetMapping("/forecast-trend")
    public Result<Map<String, Object>> getWeatherForecastTrend(
            @Parameter(description = "点位 ID") @RequestParam String pointId) {
        return Result.success(weatherService.getWeatherForecastTrend(pointId));
    }
}