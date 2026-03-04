package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.Camera;
import com.bluesky.mapper.CameraMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 摄像头监控服务
 */
@Service
@RequiredArgsConstructor
public class CameraService {

    private final CameraMapper cameraMapper;

    /**
     * 获取摄像头列表
     */
    public List<Map<String, Object>> getCameras(String status) {
        LambdaQueryWrapper<Camera> wrapper = new LambdaQueryWrapper<Camera>()
                .eq(Camera::getIsActive, true)
                .orderByAsc(Camera::getCreatedAt);
        
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Camera::getStatus, status);
        }

        List<Camera> cameras = cameraMapper.selectList(wrapper);
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (Camera camera : cameras) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", camera.getId());
            map.put("name", camera.getName());
            map.put("location", camera.getLocation());
            map.put("status", camera.getStatus());
            map.put("resolution", camera.getResolution());
            // 使用默认预览图URL
            map.put("previewImage", "/api/cameras/" + camera.getId() + "/preview");
            result.add(map);
        }
        
        return result;
    }
}
