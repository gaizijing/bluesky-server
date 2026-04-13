package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.entity.RegionConfigEntity;
import com.bluesky.service.RegionConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 地区配置控制器
 *
 * @author BlueSky Team
 */
@Tag(name = "地区配置管理", description = "地区配置的增删改查等接口")
@RestController
@RequestMapping("/region-config")
@RequiredArgsConstructor
public class RegionConfigController {

    private final RegionConfigService regionConfigService;

    /**
     * 获取所有地区配置
     */
    @Operation(summary = "获取所有地区配置", description = "获取所有地区配置列表")
    @GetMapping("/list")
    public Result<List<RegionConfigEntity>> getAll() {
        List<RegionConfigEntity> configs = regionConfigService.getAll();
        return Result.success(configs);
    }

    /**
     * 获取默认地区配置
     */
    @Operation(summary = "获取默认地区配置", description = "获取当前默认的地区配置")
    @GetMapping("/default")
    public Result<RegionConfigEntity> getDefault() {
        RegionConfigEntity config = regionConfigService.getDefaultConfig();
        return Result.success(config);
    }

    /**
     * 根据ID获取地区配置
     */
    @Operation(summary = "根据ID获取地区配置", description = "根据ID获取地区配置详情")
    @GetMapping("/{id}")
    public Result<RegionConfigEntity> getById(@PathVariable Long id) {
        RegionConfigEntity config = regionConfigService.getById(id);
        return Result.success(config);
    }

    /**
     * 添加地区配置
     */
    @Operation(summary = "添加地区配置", description = "添加新的地区配置")
    @PostMapping
    public Result<RegionConfigEntity> add(@RequestBody RegionConfigEntity config) {
        RegionConfigEntity created = regionConfigService.add(config);
        return Result.success(created);
    }

    /**
     * 更新地区配置
     */
    @Operation(summary = "更新地区配置", description = "更新地区配置信息")
    @PutMapping
    public Result<RegionConfigEntity> update(@RequestBody RegionConfigEntity config) {
        RegionConfigEntity updated = regionConfigService.update(config);
        return Result.success(updated);
    }

    /**
     * 删除地区配置
     */
    @Operation(summary = "删除地区配置", description = "删除地区配置")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        regionConfigService.delete(id);
        return Result.success();
    }
}
