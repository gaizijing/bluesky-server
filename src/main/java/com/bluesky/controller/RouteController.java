package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 航路分析控制器
 */
@Tag(name = "航路分析", description = "航路列表和详情接口")
@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    /**
     * 获取航路列表
     */
    @Operation(summary = "获取航路列表", description = "获取最近创建的5条航路列表（系统最多保存5条历史记录）")
    @GetMapping
    public Result<Map<String, Object>> getRouteList() {
        return Result.success(routeService.getRouteList());
    }

    /**
     * 获取航路详情
     */
    @Operation(summary = "获取航路详情", description = "根据航路ID获取详细信息")
    @GetMapping("/{routeId}")
    public Result<Map<String, Object>> getRouteDetail(
            @Parameter(description = "航路ID") @PathVariable String routeId) {
        return Result.success(routeService.getRouteDetail(routeId));
    }

    /**
     * 创建新航线
     */
    @Operation(summary = "创建新航线", description = "根据用户输入创建新航线")
    @PostMapping
    public Result<Map<String, Object>> createRoute(
            @Parameter(description = "航线数据") @RequestBody Map<String, Object> routeData) {
        return Result.success(routeService.createRoute(routeData));
    }

    /**
     * 分析航线风险
     */
    @Operation(summary = "分析航线风险", description = "根据航线经过的空域分析风险等级，支持时间轴动态分析（可传入currentTime参数指定分析时间点）")
    @PostMapping("/{routeId}/analyze")
    public Result<Map<String, Object>> analyzeRouteRisk(
            @Parameter(description = "航路ID") @PathVariable String routeId,
            @Parameter(description = "分析参数，可包含currentTime（ISO时间格式）指定当前分析时间点") @RequestBody(required = false) Map<String, Object> params) {
        return Result.success(routeService.analyzeRouteRisk(routeId, params));
    }

    /**
     * 清空航路历史记录
     */
    @Operation(summary = "清空航路历史记录", description = "清空所有航路历史数据（包括航线和途经点），系统会自动保持最多5条最新记录")
    @DeleteMapping("/clear-history")
    public Result<Map<String, Object>> clearHistory() {
        return Result.success(routeService.clearHistory());
    }
}
