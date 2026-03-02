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

    /**
     * 获取当前选中的监测点
     */
    public MonitoringPoint getSelected() {
        List<MonitoringPoint> selectedPoints = monitoringPointMapper.selectList(
                new LambdaQueryWrapper<MonitoringPoint>()
                        .eq(MonitoringPoint::getIsActive, true)
                        .eq(MonitoringPoint::getIsSelected, true)
                        .orderByDesc(MonitoringPoint::getUpdatedAt));
        
        if (selectedPoints.isEmpty()) {
            // 如果没有选中的监测点，返回第一个激活的监测点
            List<MonitoringPoint> activePoints = getAll();
            if (!activePoints.isEmpty()) {
                // 自动选中第一个监测点
                MonitoringPoint firstPoint = activePoints.get(0);
                updateSelected(firstPoint.getId());
                return firstPoint;
            } else {
                // 如果没有监测点，返回null
                return null;
            }
        }
        
        // 理论上应该只有一个选中的监测点，返回第一个
        return selectedPoints.get(0);
    }

    /**
     * 更新选中的监测点
     */
    public void updateSelected(String pointId) {
        // 1. 先取消所有监测点的选中状态
        List<MonitoringPoint> allPoints = getAll();
        for (MonitoringPoint point : allPoints) {
            if (Boolean.TRUE.equals(point.getIsSelected())) {
                point.setIsSelected(false);
                point.setUpdatedAt(LocalDateTime.now());
                monitoringPointMapper.updateById(point);
            }
        }
        
        // 2. 设置指定监测点为选中状态
        MonitoringPoint pointToSelect = getById(pointId);
        pointToSelect.setIsSelected(true);
        pointToSelect.setUpdatedAt(LocalDateTime.now());
        monitoringPointMapper.updateById(pointToSelect);
    }
}
