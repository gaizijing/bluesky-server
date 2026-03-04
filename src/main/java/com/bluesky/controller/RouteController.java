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
    @Operation(summary = "获取航路列表", description = "获取所有可用的航路列表")
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
}
