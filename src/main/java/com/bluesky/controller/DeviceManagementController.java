package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.entity.Device;
import com.bluesky.service.DeviceManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备管理控制器
 */
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@Tag(name = "设备管理", description = "设备的增删改查等管理接口")
public class DeviceManagementController {

    private final DeviceManagementService deviceManagementService;

    /**
     * 获取所有设备
     */
    @GetMapping("/list")
    @Operation(summary = "获取所有设备", description = "获取所有设备列表")
    public Result<List<Device>> list() {
        List<Device> devices = deviceManagementService.getAll();
        return Result.success(devices);
    }

    /**
     * 获取在线设备
     */
    @GetMapping("/online")
    @Operation(summary = "获取在线设备", description = "获取所有在线状态的设备")
    public Result<List<Device>> getOnline() {
        List<Device> devices = deviceManagementService.getOnlineDevices();
        return Result.success(devices);
    }

    /**
     * 根据类型获取设备
     */
    @GetMapping("/by-type/{type}")
    @Operation(summary = "根据类型获取设备", description = "根据设备类型获取设备列表")
    public Result<List<Device>> getByType(@PathVariable String type) {
        List<Device> devices = deviceManagementService.getDevicesByType(type);
        return Result.success(devices);
    }

    /**
     * 根据ID获取设备
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取设备", description = "根据ID获取指定的设备")
    public Result<Device> getById(@PathVariable String id) {
        Device device = deviceManagementService.getById(id);
        return Result.success(device);
    }

    /**
     * 添加设备
     */
    @PostMapping
    @Operation(summary = "添加设备", description = "添加新的设备")
    public Result<Device> add(@RequestBody Device device) {
        Device saved = deviceManagementService.add(device);
        return Result.success(saved);
    }

    /**
     * 更新设备
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新设备", description = "更新指定的设备")
    public Result<Device> update(@PathVariable String id, @RequestBody Device device) {
        device.setId(id);
        Device updated = deviceManagementService.update(device);
        return Result.success(updated);
    }

    /**
     * 删除设备
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除设备", description = "删除指定的设备")
    public Result<Void> delete(@PathVariable String id) {
        deviceManagementService.delete(id);
        return Result.success();
    }
}
