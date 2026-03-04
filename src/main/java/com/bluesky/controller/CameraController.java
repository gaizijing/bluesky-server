package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.service.CameraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 摄像头监控控制器
 */
@Tag(name = "摄像头监控", description = "摄像头列表和预览接口")
@RestController
@RequestMapping("/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;

    /**
     * 获取摄像头列表
     */
    @Operation(summary = "获取摄像头列表", description = "获取摄像头列表，支持按状态筛选")
    @GetMapping
    public Result<List<Map<String, Object>>> getCameras(
            @Parameter(description = "状态: online/offline") @RequestParam(required = false) String status) {
        return Result.success(cameraService.getCameras(status));
    }
}
