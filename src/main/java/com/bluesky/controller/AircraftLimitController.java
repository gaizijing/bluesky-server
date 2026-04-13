package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.entity.AircraftLimit;
import com.bluesky.service.AircraftLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 飞行器气象阈值控制器
 */
@RestController
@RequestMapping("/aircraft-limits")
@RequiredArgsConstructor
@Tag(name = "飞行器气象阈值", description = "飞行器气象阈值管理接口")
public class AircraftLimitController {

    private final AircraftLimitService aircraftLimitService;

    /**
     * 获取所有阈值配置
     */
    @GetMapping("/list")
    @Operation(summary = "获取所有阈值配置", description = "获取所有飞行器的气象阈值配置列表")
    public Result<List<AircraftLimit>> list() {
        List<AircraftLimit> limits = aircraftLimitService.getAll();
        return Result.success(limits);
    }

    /**
     * 根据ID获取阈值配置
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取阈值配置", description = "根据ID获取指定的飞行器气象阈值配置")
    public Result<AircraftLimit> getById(@PathVariable Long id) {
        AircraftLimit limit = aircraftLimitService.getById(id);
        return Result.success(limit);
    }

    /**
     * 根据飞行器ID获取阈值配置
     */
    @GetMapping("/aircraft/{aircraftId}")
    @Operation(summary = "根据飞行器ID获取阈值配置", description = "根据飞行器ID获取对应的气象阈值配置")
    public Result<AircraftLimit> getByAircraftId(@PathVariable String aircraftId) {
        AircraftLimit limit = aircraftLimitService.getByAircraftId(aircraftId);
        return Result.success(limit);
    }

    /**
     * 获取默认阈值配置（与飞行器无关）
     */
    @GetMapping("/default")
    @Operation(summary = "获取默认阈值配置", description = "获取与飞行器无关的默认气象阈值配置")
    public Result<AircraftLimit> getDefault() {
        AircraftLimit limit = aircraftLimitService.getDefaultLimits();
        return Result.success(limit);
    }

    /**
     * 更新默认阈值配置
     */
    @PutMapping("/default")
    @Operation(summary = "更新默认阈值配置", description = "更新与飞行器无关的默认气象阈值配置")
    public Result<AircraftLimit> updateDefault(@RequestBody AircraftLimit limit) {
        AircraftLimit updated = aircraftLimitService.updateDefaultLimits(limit);
        return Result.success(updated);
    }

    /**
     * 添加阈值配置
     */
    @PostMapping
    @Operation(summary = "添加阈值配置", description = "添加新的飞行器气象阈值配置")
    public Result<AircraftLimit> add(@RequestBody AircraftLimit limit) {
        AircraftLimit saved = aircraftLimitService.add(limit);
        return Result.success(saved);
    }

    /**
     * 更新阈值配置
     */
    @PutMapping
    @Operation(summary = "更新阈值配置", description = "更新指定的飞行器气象阈值配置")
    public Result<AircraftLimit> update(@RequestBody AircraftLimit limit) {
        AircraftLimit updated = aircraftLimitService.update(limit);
        return Result.success(updated);
    }

    /**
     * 删除阈值配置
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除阈值配置", description = "删除指定的飞行器气象阈值配置")
    public Result<Void> delete(@PathVariable Long id) {
        aircraftLimitService.delete(id);
        return Result.success();
    }
}
