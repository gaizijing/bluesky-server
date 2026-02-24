package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.entity.MonitoringPoint;
import com.bluesky.service.MonitoringPointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 重点关注区域控制器
 *
 * @author BlueSky Team
 */
@Tag(name = "重点关注区域管理", description = "重点关注区域的增删改查等接口")
@RestController
@RequestMapping("/monitoring-points")
@RequiredArgsConstructor
public class MonitoringPointController {

    private final MonitoringPointService monitoringPointService;

    /**
     * 获取重点关注区域列表
     */
    @Operation(summary = "获取重点关注区域列表", description = "获取所有重点关注区域列表")
    @GetMapping
    public Result<List<MonitoringPoint>> getAll() {
        List<MonitoringPoint> points = monitoringPointService.getAll();
        return Result.success(points);
    }

    /**
     * 获取当前选中的重点关注区域
     */
    @Operation(summary = "获取当前选中的重点关注区域", description = "获取当前用户选中的重点关注区域")
    @GetMapping("/selected")
    public Result<MonitoringPoint> getSelected() {
        // 这里简化处理,实际应从用户会话中获取
        List<MonitoringPoint> points = monitoringPointService.getAll();
        MonitoringPoint selected = points.isEmpty() ? null : points.get(0);
        return Result.success(selected);
    }

    /**
     * 更新选中的重点关注区域
     */
    @Operation(summary = "更新选中的重点关注区域", description = "更新当前用户选中的重点关注区域")
    @PostMapping("/selected")
    public Result<Map<String, Object>> updateSelected(@RequestBody Map<String, String> request) {
        String pointId = request.get("pointId");

        Map<String, Object> result = new HashMap<>();
        result.put("pointId", pointId);
        result.put("selectedTime", java.time.LocalDateTime.now().toString());
        result.put("status", "updated");

        return Result.success(result);
    }

    /**
     * 添加新的重点关注区域
     */
    @Operation(summary = "添加新的重点关注区域", description = "添加新的重点关注区域")
    @PostMapping
    public Result<MonitoringPoint> add(@RequestBody MonitoringPoint point) {
        MonitoringPoint created = monitoringPointService.add(point);
        return Result.success(created);
    }

    /**
     * 更新重点关注区域
     */
    @Operation(summary = "更新重点关注区域", description = "更新重点关注区域信息")
    @PutMapping("/{id}")
    public Result<MonitoringPoint> update(@PathVariable String id, @RequestBody MonitoringPoint point) {
        point.setId(id);
        MonitoringPoint updated = monitoringPointService.update(point);
        return Result.success(updated);
    }

    /**
     * 删除重点关注区域
     */
    @Operation(summary = "删除重点关注区域", description = "删除重点关注区域(逻辑删除)")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        monitoringPointService.delete(id);
        return Result.success();
    }
}
