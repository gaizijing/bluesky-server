package com.bluesky.controller;

import com.bluesky.dto.WeatherBatchRequest;
import com.bluesky.common.Result;
import com.bluesky.service.WeatherQueryService;
import com.bluesky.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "天气接口", description = "提供实时天气、热力图和趋势预测数据")
@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final WeatherQueryService weatherQueryService;

    @Operation(summary = "获取实时天气", description = "根据起降点 ID 返回实时天气数据（含 TemporalMeta）")
    @GetMapping("/realtime")
    public Result<Map<String, Object>> getRealtimeWeather(
            @Parameter(description = "起降点 ID") @RequestParam String pointId,
            @Parameter(description = "时间，ISO 格式，可选") @RequestParam(required = false) String time) {
        return Result.success(weatherQueryService.queryRealtime(pointId, time));
    }

    @Operation(summary = "单点气象查询")
    @GetMapping("/point")
    public Result<Map<String, Object>> queryPoint(
            @RequestParam double lng,
            @RequestParam double lat,
            @RequestParam(required = false) String time,
            @RequestParam(defaultValue = "100") int heightM,
            @RequestParam(required = false) String products,
            @RequestParam(defaultValue = "false") boolean includeRisk) {
        return Result.success(weatherQueryService.queryPoint(lng, lat, time, heightM, products, includeRisk));
    }

    @Operation(summary = "网格场查询")
    @GetMapping("/grid-field")
    public Result<Map<String, Object>> queryGridField(
            @RequestParam String regionId,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String time,
            @RequestParam(defaultValue = "100") int heightM) {
        return Result.success(weatherQueryService.queryGridField(regionId, product, time, heightM));
    }

    @Operation(summary = "垂直剖面")
    @GetMapping("/vertical-profile")
    public Result<Map<String, Object>> queryVerticalProfile(
            @RequestParam String landingPointId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String heightLevelsM) {
        return Result.success(weatherQueryService.queryVerticalProfile(
                landingPointId, startTime, endTime, heightLevelsM));
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

    @Operation(summary = "批量按经纬度获取气象数据",
            description = "顺序与请求一致；服务端对 4 位小数经纬度去重，减少 Open-Meteo QPS")
    @PostMapping("/by-coords/batch")
    public Result<Map<String, Object>> getWeatherByCoordinatesBatch(
            @Valid @RequestBody WeatherBatchRequest body) {
        Map<String, Object> result = weatherService.getWeatherByCoordinatesBatch(body.getCoordinates());
        return Result.success(result);
    }
}
