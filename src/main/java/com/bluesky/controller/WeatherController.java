package com.bluesky.controller;

import com.bluesky.dto.WeatherBatchRequest;
import com.bluesky.common.Result;
import com.bluesky.entity.LandingPoint;
import com.bluesky.service.LandingPointService;
import com.bluesky.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "天气接口", description = "提供实时天气、热力图和趋势预测数据")
@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final LandingPointService landingPointService;

    @Operation(summary = "获取实时天气", description = "根据起降点 ID 返回实时天气数据")
    @GetMapping("/realtime")
    public Result<Map<String, Object>> getRealtimeWeather(
            @Parameter(description = "起降点 ID") @RequestParam String pointId) {
        return Result.success(weatherService.getRealtimeWeather(pointId));
    }

    @Operation(summary = "获取点位级热力图", description = "读取起降点网格数据并返回 Cesium 热力图点集")
    @GetMapping("/heatmap/geo")
    public Result<Map<String, Object>> getWeatherHeatmapGeo(
            @Parameter(description = "起降点 ID") @RequestParam String pointId,
            @Parameter(description = "时间，ISO 格式，可选") @RequestParam(required = false) String time) {

        LandingPoint point = landingPointService.getEntity(pointId);
        if (point.getBboxMinLng() == null || point.getBboxMinLat() == null
                || point.getBboxMaxLng() == null || point.getBboxMaxLat() == null) {
            throw new IllegalArgumentException("起降点 " + pointId + " 缺少边界框 bbox 配置，无法生成热力图");
        }

        String bounds = String.format("[%s,%s,%s,%s]",
                point.getBboxMinLng(), point.getBboxMinLat(),
                point.getBboxMaxLng(), point.getBboxMaxLat());

        return Result.success(weatherService.getWeatherHeatmapGeo(bounds, time, pointId));
    }

    @Operation(summary = "获取城市级连续热力图", description = "聚合全市起降点并通过 IDW 插值返回连续热力图")
    @GetMapping("/heatmap/citywide")
    public Result<Map<String, Object>> getCitywideHeatmap() {
        return Result.success(weatherService.getCitywideHeatmap());
    }

    @Operation(summary = "获取天气趋势预测", description = "基于 Open-Meteo 返回未来趋势预测")
    @GetMapping("/forecast-trend")
    public Result<Map<String, Object>> getWeatherForecastTrend(
            @Parameter(description = "起降点 ID") @RequestParam String pointId) {
        return Result.success(weatherService.getWeatherForecastTrend(pointId));
    }

    @Operation(summary = "根据经纬度获取气象数据",
               description = "直接传入经纬度坐标，获取该位置的实时气象信息")
    @GetMapping("/by-coords")
    public Result<Map<String, Object>> getWeatherByCoordinates(
            @Parameter(description = "经度", required = true) @RequestParam double lng,
            @Parameter(description = "纬度", required = true) @RequestParam double lat) {
        Map<String, Object> result = weatherService.getWeatherByCoordinates(lng, lat);

        if (Boolean.TRUE.equals(result.get("error"))) {
            return Result.error(400, result.get("message").toString());
        }

        return Result.success(result);
    }

    @Operation(summary = "批量按经纬度获取气象数据",
            description = "顺序与请求一致；服务端对 4 位小数经纬度去重，减少和风 QPS")
    @PostMapping("/by-coords/batch")
    public Result<Map<String, Object>> getWeatherByCoordinatesBatch(
            @Valid @RequestBody WeatherBatchRequest body) {
        Map<String, Object> result = weatherService.getWeatherByCoordinatesBatch(body.getCoordinates());
        return Result.success(result);
    }
}
