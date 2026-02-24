package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.service.SuitabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 核心气象要素控制器
 */
@Tag(name = "核心气象要素", description = "实时核心气象要素监测接口")
@RestController
@RequestMapping("/meteorology/core-indicators")
@RequiredArgsConstructor
public class CoreIndicatorController {

    private final SuitabilityService suitabilityService;

    /**
     * 获取核心气象要素监测数据
     * GET /api/meteorology/core-indicators?pointId=point-1
     */
    @Operation(summary = "获取核心气象要素", description = "获取温度、湿度、风速、能见度等核心气象要素的实时监测值及状态")
    @GetMapping
    public Result<Map<String, Object>> getCoreIndicators(
            @Parameter(description = "重点关注区域ID") @RequestParam(required = false) String pointId) {
        return Result.success(suitabilityService.getCoreIndicators(pointId));
    }
}
