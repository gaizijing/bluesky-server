package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.entity.RouteVersion;
import com.bluesky.service.RouteLifecycleService;
import com.bluesky.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "航路", description = "V2 航路接口")
@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    private final RouteLifecycleService routeLifecycleService;

    @Operation(summary = "航路列表")
    @GetMapping
    public Result<Map<String, Object>> getRouteList(
            @RequestParam String regionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(routeLifecycleService.listByRegion(regionId, page, size));
    }

    @Operation(summary = "航路详情")
    @GetMapping("/{routeId}")
    public Result<Map<String, Object>> getRouteDetail(
            @Parameter(description = "航路ID") @PathVariable String routeId,
            @RequestParam(required = false) String routeVersionId) {
        return Result.success(routeService.getRouteDetail(routeId, routeVersionId));
    }

    @Operation(summary = "航路版本列表")
    @GetMapping("/{routeId}/versions")
    public Result<List<RouteVersion>> listVersions(@PathVariable String routeId) {
        return Result.success(routeLifecycleService.listVersions(routeId));
    }

    @Operation(summary = "创建航路")
    @PostMapping
    public Result<Map<String, Object>> createRoute(
            @RequestParam String regionId,
            @RequestBody Map<String, Object> routeData) {
        return Result.success(routeLifecycleService.createRoute(regionId, routeData));
    }

    @Operation(summary = "导入 GeoJSON 航路")
    @PostMapping("/import")
    public Result<Map<String, Object>> importRoute(
            @RequestParam String regionId,
            @RequestBody Map<String, Object> geoJson) {
        return Result.success(routeLifecycleService.importGeoJson(regionId, geoJson));
    }

    @Operation(summary = "分析航线风险")
    @PostMapping("/{routeId}/analyze")
    public Result<Map<String, Object>> analyzeRouteRisk(
            @PathVariable String routeId,
            @RequestBody(required = false) Map<String, Object> params) {
        return Result.success(routeService.analyzeRouteRisk(routeId, params));
    }

    @Operation(summary = "按 Region 清空航路")
    @DeleteMapping
    public Result<Void> clearByRegion(@RequestParam String regionId) {
        routeLifecycleService.deleteByRegion(regionId);
        return Result.success();
    }
}
