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
 * 适飞分析控制器
 * 涵盖适飞状态、热力图数据
 */
@Tag(name = "适飞分析", description = "适飞状态查询、热力图数据接口")
@RestController
@RequestMapping("/suitability")
@RequiredArgsConstructor
public class SuitabilityController {

    private final SuitabilityService suitabilityService;

    /**
     * 获取适飞状态 - 时间轴热力图
     * GET /api/suitability/status?pointId=point-1&factor=综合&totalHours=24
     */
    @Operation(summary = "获取适飞状态", description = "按时间维度和气象因素返回适飞分析热力图数据")
    @GetMapping("/status")
    public Result<Map<String, Object>> getSuitabilityStatus(
            @Parameter(description = "重点关注区域ID") @RequestParam String pointId,
            @Parameter(description = "气象因素: 综合/风/风切变/颠簸指数/湍流/降水/能见度") @RequestParam(required = false) String factor,
            @Parameter(description = "预测总时长(小时),默认24") @RequestParam(required = false, defaultValue = "24") Integer totalHours) {
        return Result.success(suitabilityService.getSuitabilityStatus(pointId, factor, totalHours));
    }

    /**
     * 获取适飞热力图 - 空间区域
     * GET /api/suitability/heatmap?timePoint=2025-11-03T14:00:00&factor=综合
     */
    @Operation(summary = "获取适飞热力图数据", description = "获取指定时间点的空间维度适飞热力图数据")
    @GetMapping("/heatmap")
    public Result<Map<String, Object>> getSuitabilityHeatmap(
            @Parameter(description = "时间点,ISO格式") @RequestParam(required = false) String timePoint,
            @Parameter(description = "气象因素") @RequestParam(required = false) String factor) {
        return Result.success(suitabilityService.getSuitabilityHeatmap(timePoint, factor));
    }
}
