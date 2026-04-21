package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.dto.CameraRequest;
import com.bluesky.entity.Camera;
import com.bluesky.service.CameraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Camera Management", description = "Camera CRUD, preview, stream, and activation APIs")
@RestController
@RequestMapping("/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;

    @Operation(summary = "Get camera list", description = "Get cameras filtered by status and monitoring point")
    @GetMapping
    public Result<List<Camera>> getCameras(
            @Parameter(description = "status: online/offline") @RequestParam(required = false) String status,
            @Parameter(description = "monitoring point id") @RequestParam(required = false) String pointId) {
        return Result.success(cameraService.getCameras(status, pointId));
    }

    @Operation(summary = "Get camera detail", description = "Get camera detail by id")
    @GetMapping("/{id}")
    public Result<Camera> getCamera(
            @Parameter(description = "camera id") @PathVariable String id) {
        return Result.success(cameraService.getCameraById(id));
    }

    @Operation(summary = "Create camera", description = "Create a camera record")
    @PostMapping
    public Result<Camera> createCamera(@Valid @RequestBody CameraRequest request) {
        return Result.success(cameraService.createCamera(request));
    }

    @Operation(summary = "Update camera", description = "Update a camera record by id")
    @PutMapping("/{id}")
    public Result<Camera> updateCamera(
            @Parameter(description = "camera id") @PathVariable String id,
            @Valid @RequestBody CameraRequest request) {
        return Result.success(cameraService.updateCamera(id, request));
    }

    @Operation(summary = "Delete camera", description = "Delete a camera by id")
    @DeleteMapping("/{id}")
    public Result<Void> deleteCamera(
            @Parameter(description = "camera id") @PathVariable String id) {
        cameraService.deleteCamera(id);
        return Result.success();
    }

    @Operation(summary = "Get camera preview", description = "Get camera preview image url")
    @GetMapping("/{id}/preview")
    public Result<String> getCameraPreview(
            @Parameter(description = "camera id") @PathVariable String id) {
        return Result.success(cameraService.getCameraPreviewUrl(id));
    }

    @Operation(summary = "Get camera stream", description = "Get camera stream url")
    @GetMapping("/{id}/stream")
    public Result<String> getCameraStream(
            @Parameter(description = "camera id") @PathVariable String id) {
        return Result.success(cameraService.getCameraStreamUrl(id));
    }

    @Operation(summary = "Set camera active status", description = "Enable or disable a camera")
    @PutMapping("/{id}/active")
    public Result<Camera> setCameraActive(
            @Parameter(description = "camera id") @PathVariable String id,
            @Parameter(description = "active status") @RequestParam Boolean active) {
        return Result.success(cameraService.setCameraActive(id, active));
    }
}
