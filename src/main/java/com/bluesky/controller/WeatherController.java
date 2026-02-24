package com.bluesky.controller;

import com.bluesky.common.Result;
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
}
