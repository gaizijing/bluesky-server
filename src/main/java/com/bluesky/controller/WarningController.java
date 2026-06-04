package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.service.WarningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "预警")
@RestController
@RequestMapping("/warnings")
@RequiredArgsConstructor
public class WarningController {

    private final WarningService warningService;

    @GetMapping
    public Result<List<Map<String, Object>>> list(
            @RequestParam String regionId,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) String statuses,
            @RequestParam(required = false) Integer limit) {
        return Result.success(warningService.list(regionId, types, statuses, limit));
    }

    @GetMapping("/{warningId}")
    public Result<Map<String, Object>> get(@PathVariable String warningId) {
        return Result.success(warningService.getById(warningId));
    }

    @PostMapping("/{warningId}/ack")
    @Operation(summary = "标记已读 NEW → ACKNOWLEDGED")
    public Result<Map<String, Object>> ack(
            @PathVariable String warningId,
            @RequestParam(required = false) String remark) {
        return Result.success(warningService.ack(warningId, remark));
    }

    @PostMapping("/{warningId}/handle")
    @Operation(summary = "处理预警 → HANDLED")
    public Result<Map<String, Object>> handle(
            @PathVariable String warningId,
            @RequestParam(required = false) String remark) {
        return Result.success(warningService.handle(warningId, remark));
    }

    @PostMapping("/{warningId}/close")
    @Operation(summary = "关闭预警 → CLOSED")
    public Result<Map<String, Object>> close(
            @PathVariable String warningId,
            @RequestParam(required = false) String remark) {
        return Result.success(warningService.close(warningId, remark));
    }
}
