package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.service.RiskZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "风险区", description = "禁飞/谨慎驾驶圆柱风险区")
@RestController
@RequestMapping("/risk-zones")
@RequiredArgsConstructor
public class RiskZoneController {

    private final RiskZoneService riskZoneService;

    @Operation(summary = "风险区列表", description = "返回全部风险区，用于地图圆柱绘制")
    @GetMapping
    public Result<Map<String, Object>> list() {
        return Result.success(riskZoneService.listAll());
    }
}
