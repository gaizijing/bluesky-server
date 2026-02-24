package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.entity.FlightTask;
import com.bluesky.service.FlightTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 飞行任务控制器
 * 涵盖任务CRUD和飞行器气象适配分析
 */
@Tag(name = "飞行任务管理", description = "飞行任务增删改查、状态更新、飞行器适配分析接口")
@RestController
@RequestMapping("/flight")
@RequiredArgsConstructor
public class FlightTaskController {

    private final FlightTaskService flightTaskService;

    /**
     * 获取飞行任务列表
     * GET /api/flight/tasks?taskDate=2025-11-03&status=ongoing&type=救援
     */
    @Operation(summary = "获取飞行任务列表", description = "按日期、状态、类型筛选今日飞行任务")
    @GetMapping("/tasks")
    public Result<Map<String, Object>> getFlightTasks(
            @Parameter(description = "任务日期(yyyy-MM-dd),默认今天") @RequestParam(required = false) String taskDate,
            @Parameter(description = "任务状态: waiting/ongoing/completed/cancelled") @RequestParam(required = false) String status,
            @Parameter(description = "任务类型: 救援/物流/巡检/训练") @RequestParam(required = false) String type) {
        return Result.success(flightTaskService.getFlightTasks(taskDate, status, type));
    }

    /**
     * 获取单个飞行任务详情
     * GET /api/flight/tasks/{taskId}
     */
    @Operation(summary = "获取飞行任务详情", description = "根据任务ID获取飞行任务详细信息")
    @GetMapping("/tasks/{taskId}")
    public Result<FlightTask> getTaskById(@PathVariable String taskId) {
        return Result.success(flightTaskService.getTaskById(taskId));
    }

    /**
     * 创建飞行任务
     * POST /api/flight/tasks
     */
    @Operation(summary = "创建飞行任务", description = "新建飞行任务，初始状态为waiting")
    @PostMapping("/tasks")
    public Result<FlightTask> createTask(@RequestBody FlightTask task) {
        return Result.success(flightTaskService.createTask(task));
    }

    /**
     * 更新飞行任务
     * PUT /api/flight/tasks/{taskId}
     */
    @Operation(summary = "更新飞行任务", description = "更新飞行任务信息")
    @PutMapping("/tasks/{taskId}")
    public Result<FlightTask> updateTask(@PathVariable String taskId, @RequestBody FlightTask task) {
        return Result.success(flightTaskService.updateTask(taskId, task));
    }

    /**
     * 更新飞行任务状态
     * PATCH /api/flight/tasks/{taskId}/status
     */
    @Operation(summary = "更新飞行任务状态", description = "更新任务状态，支持: waiting→ongoing→completed/cancelled")
    @PatchMapping("/tasks/{taskId}/status")
    public Result<Void> updateTaskStatus(
            @PathVariable String taskId,
            @RequestBody Map<String, String> body) {
        flightTaskService.updateTaskStatus(taskId, body.get("status"));
        return Result.success();
    }

    /**
     * 飞行器气象适配分析
     * GET /api/flight/aircraft-adapt?pointId=point-1
     */
    @Operation(summary = "飞行器气象适配分析", description = "根据当前气象条件分析各型号飞行器是否适合执行飞行任务")
    @GetMapping("/aircraft-adapt")
    public Result<Map<String, Object>> getAircraftAdapt(
            @Parameter(description = "重点关注区域ID") @RequestParam String pointId) {
        return Result.success(flightTaskService.getAircraftAdapt(pointId));
    }

    /**
     * 获取飞行器型号列表
     * GET /api/flight/aircraft-models
     */
    @Operation(summary = "获取飞行器型号列表", description = "获取系统中所有激活的飞行器型号及其气象限制参数")
    @GetMapping("/aircraft-models")
    public Result<Object> getAircraftModels() {
        return Result.success(flightTaskService.getAircraftModels());
    }
}
