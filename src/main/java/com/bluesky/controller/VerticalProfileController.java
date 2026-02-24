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
 * 垂直剖面控制器
 */
@Tag(name = "垂直剖面", description = "不同高度层气象要素分布接口")
@RestController
@RequestMapping("/meteorology/vertical-profile")
@RequiredArgsConstructor
public class VerticalProfileController {

    private final SuitabilityService suitabilityService;

    /**
     * 获取垂直剖面数据
     * GET /api/meteorology/vertical-profile?pointId=point-1&timeType=current
     */
    @Operation(summary = "获取垂直剖面数据", description = "获取各高度层(50m~500m)风速、温度、湿度、能见度分布数据")
    @GetMapping
    public Result<Map<String, Object>> getVerticalProfile(
            @Parameter(description = "重点关注区域ID") @RequestParam String pointId,
            @Parameter(description = "时间类型: current(当前)/1h(1小时前)/3h(3小时前)/6h(6小时前)") @RequestParam(required = false, defaultValue = "current") String timeType) {
        return Result.success(suitabilityService.getVerticalProfile(pointId, timeType));
    }
}
