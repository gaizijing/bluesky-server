package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.entity.AircraftModel;
import com.bluesky.service.AircraftModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 飞行器模型控制器
 */
@RestController
@RequestMapping("/aircraft-models")
@RequiredArgsConstructor
@Tag(name = "飞行器模型", description = "飞行器模型管理接口")
public class AircraftModelController {

    private final AircraftModelService aircraftModelService;

    /**
     * 获取所有飞行器模型
     */
    @GetMapping("/list")
    @Operation(summary = "获取所有飞行器模型", description = "获取所有飞行器模型列表")
    public Result<List<AircraftModel>> list() {
        List<AircraftModel> models = aircraftModelService.getAll();
        return Result.success(models);
    }

    /**
     * 获取启用的飞行器模型
     */
    @GetMapping("/active")
    @Operation(summary = "获取启用的飞行器模型", description = "获取所有启用状态的飞行器模型列表")
    public Result<List<AircraftModel>> getActive() {
        List<AircraftModel> models = aircraftModelService.getActiveModels();
        return Result.success(models);
    }

    /**
     * 根据ID获取飞行器模型
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取飞行器模型", description = "根据ID获取指定的飞行器模型")
    public Result<AircraftModel> getById(@PathVariable String id) {
        AircraftModel model = aircraftModelService.getById(id);
        return Result.success(model);
    }

    /**
     * 添加飞行器模型
     */
    @PostMapping
    @Operation(summary = "添加飞行器模型", description = "添加新的飞行器模型")
    public Result<AircraftModel> add(@RequestBody AircraftModel model) {
        AircraftModel saved = aircraftModelService.add(model);
        return Result.success(saved);
    }

    /**
     * 更新飞行器模型
     */
    @PutMapping
    @Operation(summary = "更新飞行器模型", description = "更新指定的飞行器模型")
    public Result<AircraftModel> update(@RequestBody AircraftModel model) {
        AircraftModel updated = aircraftModelService.update(model);
        return Result.success(updated);
    }

    /**
     * 删除飞行器模型
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除飞行器模型", description = "删除指定的飞行器模型")
    public Result<Void> delete(@PathVariable String id) {
        aircraftModelService.delete(id);
        return Result.success();
    }
}
