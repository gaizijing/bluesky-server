package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.service.FlyabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "适飞分析")
@RestController
@RequestMapping("/flyability")
@RequiredArgsConstructor
public class FlyabilityController {

    private final FlyabilityService flyabilityService;

    @GetMapping("/landing-matrix")
    @Operation(summary = "起降点适飞矩阵")
    public Result<Map<String, Object>> landingMatrix(
            @RequestParam String regionId,
            @RequestParam(required = false) String landingPointId,
            @RequestParam(required = false) String time,
            @RequestParam(defaultValue = "1") int hours) {
        return Result.success(flyabilityService.landingMatrix(regionId, landingPointId, time, hours));
    }

    @GetMapping("/route-matrix")
    @Operation(summary = "航路适飞矩阵")
    public Result<Map<String, Object>> routeMatrix(
            @RequestParam String regionId,
            @RequestParam String routeId,
            @RequestParam(required = false) String routeVersionId,
            @RequestParam(required = false) String time,
            @RequestParam(defaultValue = "1") int hours) {
        return Result.success(flyabilityService.routeMatrix(regionId, routeId, routeVersionId, time, hours));
    }
}
