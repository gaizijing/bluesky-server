package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.service.RiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "风险")
@RestController
@RequestMapping("/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;

    @GetMapping("/heatmap")
    @Operation(summary = "风险热力网格")
    public Result<Map<String, Object>> heatmap(
            @RequestParam String regionId,
            @RequestParam(required = false) String time,
            @RequestParam(defaultValue = "100") int heightM,
            @RequestParam(required = false) Double west,
            @RequestParam(required = false) Double south,
            @RequestParam(required = false) Double east,
            @RequestParam(required = false) Double north) {
        return Result.success(riskService.queryHeatmap(regionId, time, heightM, west, south, east, north));
    }

    @GetMapping("/point")
    @Operation(summary = "单点风险查询")
    public Result<Map<String, Object>> point(
            @RequestParam double lng,
            @RequestParam double lat,
            @RequestParam(required = false) String time,
            @RequestParam(defaultValue = "100") int heightM) {
        return Result.success(riskService.queryPoint(lng, lat, time, heightM));
    }
}
