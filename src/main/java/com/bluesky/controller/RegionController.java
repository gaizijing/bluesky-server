package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.dto.RegionRequest;
import com.bluesky.service.RegionService;
import com.bluesky.vo.RegionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Region", description = "区域管理")
@RestController
@RequestMapping("/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @Operation(summary = "Region 列表（按当前用户权限）")
    @GetMapping
    public Result<List<RegionVO>> list() {
        return Result.success(regionService.listForCurrentUser());
    }

    @Operation(summary = "默认 Region")
    @GetMapping("/default")
    public Result<RegionVO> getDefault() {
        return Result.success(regionService.getDefault());
    }

    @Operation(summary = "Region 详情")
    @GetMapping("/{regionId}")
    public Result<RegionVO> getById(@PathVariable String regionId) {
        return Result.success(regionService.getById(regionId));
    }

    @Operation(summary = "创建 Region")
    @PostMapping
    public Result<RegionVO> create(@Valid @RequestBody RegionRequest request) {
        return Result.success(regionService.create(request));
    }

    @Operation(summary = "更新 Region")
    @PutMapping("/{regionId}")
    public Result<RegionVO> update(@PathVariable String regionId, @Valid @RequestBody RegionRequest request) {
        return Result.success(regionService.update(regionId, request));
    }

    @Operation(summary = "设为默认 Region")
    @PutMapping("/{regionId}/default")
    public Result<RegionVO> setDefault(@PathVariable String regionId) {
        return Result.success(regionService.setAsDefault(regionId));
    }

    @Operation(summary = "删除 Region")
    @DeleteMapping("/{regionId}")
    public Result<Void> delete(@PathVariable String regionId) {
        regionService.delete(regionId);
        return Result.success();
    }
}
