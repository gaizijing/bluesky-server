package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.dto.LandingPointRequest;
import com.bluesky.service.LandingPointService;
import com.bluesky.vo.LandingPointVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "LandingPoint", description = "V2 起降点")
@RestController
@RequestMapping("/landing-points")
@RequiredArgsConstructor
public class LandingPointController {

    private final LandingPointService landingPointService;

    @Operation(summary = "起降点列表")
    @GetMapping
    public Result<List<LandingPointVO>> list(@RequestParam String regionId) {
        return Result.success(landingPointService.listByRegion(regionId));
    }

    @Operation(summary = "起降点详情")
    @GetMapping("/{id}")
    public Result<LandingPointVO> getById(@PathVariable String id,
                                          @RequestParam(required = false) String time) {
        return Result.success(landingPointService.getById(id, time));
    }

    @Operation(summary = "创建起降点")
    @PostMapping
    public Result<LandingPointVO> create(@Valid @RequestBody LandingPointRequest request) {
        return Result.success(landingPointService.create(request));
    }

    @Operation(summary = "更新起降点")
    @PutMapping("/{id}")
    public Result<LandingPointVO> update(@PathVariable String id, @Valid @RequestBody LandingPointRequest request) {
        return Result.success(landingPointService.update(id, request));
    }

    @Operation(summary = "删除起降点")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        landingPointService.delete(id);
        return Result.success();
    }
}
