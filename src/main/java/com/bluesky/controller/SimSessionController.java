package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.service.SimSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "模拟飞行")
@RestController
@RequestMapping("/sim/sessions")
@RequiredArgsConstructor
public class SimSessionController {

    private final SimSessionService simSessionService;

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam String regionId) {
        return Result.success(simSessionService.listByRegion(regionId));
    }

    @GetMapping("/{sessionId}")
    public Result<Map<String, Object>> get(@PathVariable String sessionId) {
        return Result.success(simSessionService.getById(sessionId));
    }

    @PostMapping
    @Operation(summary = "创建模拟会话")
    public Result<Map<String, Object>> create(
            @RequestParam String regionId,
            @RequestParam(required = false) String routeId) {
        return Result.success(simSessionService.create(regionId, routeId));
    }

    @PutMapping("/{sessionId}/status")
    public Result<Map<String, Object>> updateStatus(
            @PathVariable String sessionId,
            @RequestParam String status) {
        return Result.success(simSessionService.updateStatus(sessionId, status));
    }

    @PostMapping("/{sessionId}/close")
    public Result<Map<String, Object>> close(@PathVariable String sessionId) {
        simSessionService.close(sessionId);
        return Result.success(simSessionService.getById(sessionId));
    }
}
