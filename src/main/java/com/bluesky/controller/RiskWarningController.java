package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.service.RiskWarningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 风险预警控制器
 *
 * @author BlueSky Team
 */
@Tag(name = "风险预警模块", description = "风险预警数据查询和处理接口")
@RestController
@RequestMapping("/weather/risk")
@RequiredArgsConstructor
public class RiskWarningController {

    private final RiskWarningService riskWarningService;

    /**
     * 获取风险预警数据
     */
    @Operation(summary = "获取风险预警数据", description = "获取指定重点关注区域和时间范围内的风险预警信息")
    @GetMapping("/report")
    public Result<Map<String, Object>> getWarnings(
            @RequestParam String pointId,
            @RequestParam String timeRange) {
        Map<String, Object> warnings = riskWarningService.getWarnings(pointId, timeRange);
        return Result.success(warnings);
    }

    /**
     * 处理预警
     */
    @Operation(summary = "处理预警", description = "标记预警为已处理")
    @PostMapping("/{id}/handle")
    public Result<Void> handleWarning(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        String handler = request.get("handler");
        String remark = request.get("remark");

        riskWarningService.handle(id, handler, remark);
        return Result.success();
    }
}
