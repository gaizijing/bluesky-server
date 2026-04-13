package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.Device;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备管理服务
 */
@Service
@RequiredArgsConstructor
public class DeviceManagementService {

    private final DeviceMapper deviceMapper;

    /**
     * 获取所有设备
     */
    public List<Device> getAll() {
        return deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .orderByDesc(Device::getUpdatedAt)
        );
    }

    /**
     * 根据ID获取设备
     */
    public Device getById(String id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BusinessException(404, "设备不存在");
        }
        return device;
    }

    /**
     * 添加设备
     */
    public Device add(Device device) {
        // 检查是否已存在相同ID的设备
        Device existing = deviceMapper.selectById(device.getId());
        if (existing != null) {
            throw new BusinessException(400, "该设备ID已存在");
        }

        device.setCreatedAt(LocalDateTime.now());
        device.setUpdatedAt(LocalDateTime.now());
        device.setIsActive(true);
        deviceMapper.insert(device);
        return device;
    }

    /**
     * 更新设备
     */
    public Device update(Device device) {
        Device existing = getById(device.getId());
        if (existing == null) {
            throw new BusinessException(404, "设备不存在");
        }

        device.setUpdatedAt(LocalDateTime.now());
        deviceMapper.updateById(device);
        return device;
    }

    /**
     * 删除设备
     */
    public void delete(String id) {
        Device existing = getById(id);
        if (existing == null) {
            throw new BusinessException(404, "设备不存在");
        }
        deviceMapper.deleteById(id);
    }

    /**
     * 获取在线设备
     */
    public List<Device> getOnlineDevices() {
        return deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getStatus, "online")
                        .orderByDesc(Device::getUpdatedAt)
        );
    }

    /**
     * 获取设备按类型分组
     */
    public List<Device> getDevicesByType(String type) {
        return deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getType, type)
                        .orderByDesc(Device::getUpdatedAt)
        );
    }
}
