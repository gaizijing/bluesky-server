package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.dto.CameraRequest;
import com.bluesky.entity.Camera;
import com.bluesky.mapper.CameraMapper;
import com.bluesky.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Camera management service.
 */
@Service
@RequiredArgsConstructor
public class CameraService {

    private final CameraMapper cameraMapper;
    private final MonitoringPointService monitoringPointService;

    /**
     * Get cameras filtered by status and monitoring point.
     */
    public List<Camera> getCameras(String status, String pointId) {
        LambdaQueryWrapper<Camera> wrapper = new LambdaQueryWrapper<Camera>()
                .orderByDesc(Camera::getUpdatedAt)
                .orderByDesc(Camera::getCreatedAt);

        String normalizedStatus = normalizeNullableText(status);
        if (normalizedStatus != null) {
            wrapper.eq(Camera::getStatus, normalizeStatus(normalizedStatus));
        }

        String normalizedPointId = normalizeNullableText(pointId);
        if (normalizedPointId != null) {
            wrapper.eq(Camera::getPointId, normalizedPointId);
        }

        return cameraMapper.selectList(wrapper);
    }

    /**
     * Get camera detail by id.
     */
    public Camera getCameraById(String id) {
        String normalizedId = normalizeRequiredText(id, "摄像头ID不能为空");
        Camera camera = cameraMapper.selectById(normalizedId);
        if (camera == null) {
            throw new BusinessException(404, "摄像头不存在");
        }
        return camera;
    }

    /**
     * Create a new camera.
     */
    public Camera createCamera(CameraRequest request) {
        String id = normalizeRequiredText(request.getId(), "摄像头ID不能为空");
        if (cameraMapper.selectById(id) != null) {
            throw new BusinessException(400, "该摄像头ID已存在");
        }

        Camera camera = new Camera();
        camera.setId(id);
        fillCameraFields(camera, request, true);

        LocalDateTime now = LocalDateTime.now();
        camera.setCreatedAt(now);
        camera.setUpdatedAt(now);

        cameraMapper.insert(camera);
        return camera;
    }

    /**
     * Update camera by id.
     */
    public Camera updateCamera(String id, CameraRequest request) {
        String normalizedId = normalizeRequiredText(id, "摄像头ID不能为空");
        Camera camera = getCameraById(normalizedId);

        String requestId = normalizeNullableText(request.getId());
        if (requestId != null && !normalizedId.equals(requestId)) {
            throw new BusinessException(400, "请求体中的摄像头ID与路径参数不一致");
        }

        fillCameraFields(camera, request, false);
        camera.setId(normalizedId);
        camera.setUpdatedAt(LocalDateTime.now());

        cameraMapper.updateById(camera);
        return getCameraById(normalizedId);
    }

    /**
     * Delete camera by id.
     */
    public void deleteCamera(String id) {
        Camera camera = getCameraById(id);
        cameraMapper.deleteById(camera.getId());
    }

    /**
     * Get preview url by camera id.
     */
    public String getCameraPreviewUrl(String id) {
        return getCameraById(id).getPreviewUrl();
    }

    /**
     * Get stream url by camera id.
     */
    public String getCameraStreamUrl(String id) {
        return getCameraById(id).getStreamUrl();
    }

    /**
     * Enable or disable a camera.
     */
    public Camera setCameraActive(String id, Boolean active) {
        if (active == null) {
            throw new BusinessException(400, "active参数不能为空");
        }

        Camera camera = getCameraById(id);
        camera.setIsActive(active);
        camera.setUpdatedAt(LocalDateTime.now());
        cameraMapper.updateById(camera);
        return getCameraById(id);
    }

    private void fillCameraFields(Camera camera, CameraRequest request, boolean creating) {
        camera.setName(normalizeRequiredText(request.getName(), "摄像头名称不能为空"));
        camera.setLocation(normalizeNullableText(request.getLocation()));
        camera.setPointId(normalizePointId(request.getPointId()));
        camera.setLongitude(request.getLongitude());
        camera.setLatitude(request.getLatitude());
        camera.setStatus(normalizeStatus(request.getStatus()));
        camera.setResolution(normalizeResolution(request.getResolution()));
        camera.setPreviewUrl(normalizeNullableText(request.getPreviewUrl()));
        camera.setStreamUrl(normalizeNullableText(request.getStreamUrl()));
        camera.setLastHeartbeat(request.getLastHeartbeat());

        if (request.getIsActive() != null) {
            camera.setIsActive(request.getIsActive());
        } else if (creating) {
            camera.setIsActive(Boolean.TRUE);
        }
    }

    private String normalizePointId(String pointId) {
        String normalizedPointId = normalizeRequiredText(pointId, "关联监测点不能为空");

        // Validate the referenced monitoring point exists.
        return monitoringPointService.getById(normalizedPointId).getId();
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullableText(status);
        if (normalized == null) {
            return "online";
        }

        String lowerCase = normalized.toLowerCase();
        if (!"online".equals(lowerCase) && !"offline".equals(lowerCase)) {
            throw new BusinessException(400, "摄像头状态只能是online或offline");
        }
        return lowerCase;
    }

    private String normalizeResolution(String resolution) {
        String normalized = normalizeNullableText(resolution);
        return normalized == null ? "1920x1080" : normalized;
    }

    private String normalizeRequiredText(String value, String message) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            throw new BusinessException(400, message);
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
