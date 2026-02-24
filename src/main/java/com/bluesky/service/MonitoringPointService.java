package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.MonitoringPoint;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.MonitoringPointMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 重点关注区域服务
 *
 * @author BlueSky Team
 */
@Service
@RequiredArgsConstructor
public class MonitoringPointService {

    private final MonitoringPointMapper monitoringPointMapper;

    /**
     * 获取所有重点关注区域列表
     */
    public List<MonitoringPoint> getAll() {
        return monitoringPointMapper.selectList(
                new LambdaQueryWrapper<MonitoringPoint>()
                        .eq(MonitoringPoint::getIsActive, true)
                        .orderByDesc(MonitoringPoint::getCreatedAt));
    }

    /**
     * 根据ID获取重点关注区域
     */
    public MonitoringPoint getById(String id) {
        MonitoringPoint point = monitoringPointMapper.selectById(id);
        if (point == null) {
            throw new BusinessException(404, "重点关注区域不存在");
        }
        return point;
    }

    /**
     * 添加重点关注区域
     */
    public MonitoringPoint add(MonitoringPoint point) {
        point.setStatus("available");
        point.setIsActive(true);
        point.setLastUpdate(System.currentTimeMillis());
        point.setCreatedAt(LocalDateTime.now());
        point.setUpdatedAt(LocalDateTime.now());

        monitoringPointMapper.insert(point);
        return point;
    }

    /**
     * 更新重点关注区域
     */
    public MonitoringPoint update(MonitoringPoint point) {
        MonitoringPoint existing = getById(point.getId());

        point.setUpdatedAt(LocalDateTime.now());
        point.setLastUpdate(System.currentTimeMillis());

        monitoringPointMapper.updateById(point);
        return point;
    }

    /**
     * 删除重点关注区域(逻辑删除)
     */
    public void delete(String id) {
        MonitoringPoint point = getById(id);
        point.setIsActive(false);
        point.setUpdatedAt(LocalDateTime.now());
        monitoringPointMapper.updateById(point);
    }
}
