package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.dto.NoFlyZoneRequest;
import com.bluesky.service.NoFlyZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "禁飞区")
@RestController
@RequestMapping("/no-fly-zones")
@RequiredArgsConstructor
public class NoFlyZoneController {

    private final NoFlyZoneService service;

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam String regionId) {
        return Result.success(service.listByRegion(regionId));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody NoFlyZoneRequest request) {
        return Result.success(service.create(request));
    }

    @PutMapping("/{zoneId}")
    public Result<Map<String, Object>> update(@PathVariable String zoneId, @Valid @RequestBody NoFlyZoneRequest request) {
        return Result.success(service.update(zoneId, request));
    }

    @DeleteMapping("/{zoneId}")
    public Result<Void> delete(@PathVariable String zoneId) {
        service.delete(zoneId);
        return Result.success();
    }

    @PostMapping("/import")
    @Operation(summary = "GeoJSON FeatureCollection 导入")
    public Result<List<Map<String, Object>>> importGeoJson(
            @RequestParam String regionId,
            @RequestBody Map<String, Object> geoJson) {
        return Result.success(service.importGeoJson(regionId, geoJson));
    }
}
