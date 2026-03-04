package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 设备监测控制器
 */
@Tag(name = "设备监测", description = "设备统计、告警、历史数据接口")
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * 获取设备统计数量
     */
    @Operation(summary = "获取设备统计", description = "获取各类设备的在线/总数统计")
    @GetMapping("/count")
    public Result<List<Map<String, Object>>> getDeviceCount() {
        return Result.success(deviceService.getDeviceCount());
    }

    /**
     * 获取设备告警列表
     */
    @Operation(summary = "获取设备告警", description = "获取设备告警列表，支持按日期和等级筛选")
    @GetMapping("/alarms")
    public Result<List<Map<String, Object>>> getDeviceAlarms(
            @Parameter(description = "日期(yyyy-MM-dd)") @RequestParam(required = false) String date,
            @Parameter(description = "告警等级: danger/warning/info") @RequestParam(required = false) String level,
            @Parameter(description = "限制数量") @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return Result.success(deviceService.getDeviceAlarms(date, level, limit));
    }

    /**
     * 获取历史监测数据
     */
    @Operation(summary = "获取历史监测数据", description = "获取设备42小时历史监测数据")
    @GetMapping("/history")
    public Result<Map<String, Object>> getHistoryData() {
        return Result.success(deviceService.getHistoryData());
    }
}
