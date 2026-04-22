package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.config.RegionConfig;
import com.bluesky.entity.MonitoringPoint;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.MonitoringPointMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 重点关注区域服务
 *
 * @author BlueSky Team
 */
@Service
@RequiredArgsConstructor
public class MonitoringPointService {

    private final MonitoringPointMapper monitoringPointMapper;
    private final RegionConfig regionConfig;

    /**
     * 获取所有重点关注区域列表
     */
    public List<MonitoringPoint> getAll() {
        List<MonitoringPoint> allPoints = monitoringPointMapper.selectList(
                new LambdaQueryWrapper<MonitoringPoint>()
                        .orderByDesc(MonitoringPoint::getCreatedAt));
        
        // 根据配置的地区边界过滤监测点
        return allPoints.stream()
                .filter(this::isPointInConfiguredRegion)
                .collect(Collectors.toList());
    }
    
    /**
     * 判断监测点是否在配置的地区边界内
     */
    private boolean isPointInConfiguredRegion(MonitoringPoint point) {
        RegionConfig.Bounds bounds = regionConfig.getBounds();
        
        // 优先使用中心点经纬度判断
        if (point.getLongitude() != null && point.getLatitude() != null) {
            double longitude = point.getLongitude().doubleValue();
            double latitude = point.getLatitude().doubleValue();
            
            return longitude >= bounds.getWest() && longitude <= bounds.getEast() &&
                   latitude >= bounds.getSouth() && latitude <= bounds.getNorth();
        }
        
        // 如果没有中心点经纬度，使用边界框判断
        if (point.getBboxMinLng() != null && point.getBboxMinLat() != null &&
            point.getBboxMaxLng() != null && point.getBboxMaxLat() != null) {
            double minLng = point.getBboxMinLng().doubleValue();
            double minLat = point.getBboxMinLat().doubleValue();
            double maxLng = point.getBboxMaxLng().doubleValue();
            double maxLat = point.getBboxMaxLat().doubleValue();
            
            // 检查边界框是否与配置地区有重叠
            return !(maxLng < bounds.getWest() || minLng > bounds.getEast() ||
                     maxLat < bounds.getSouth() || minLat > bounds.getNorth());
        }
        
        return false;
    }

    /**
     * 根据ID获取重点关注区域
     */
    public MonitoringPoint getById(String id) {
        MonitoringPoint point = monitoringPointMapper.selectById(id);
        if (point == null) {
            throw new BusinessException(404, "重点关注区域不存在");
        }
        
        // 检查监测点是否在配置的地区内
        if (!isPointInConfiguredRegion(point)) {
            throw new BusinessException(400, "该监测点不在配置的地区范围内");
        }
        
        return point;
    }

    /**
     * 添加重点关注区域
     */
    public MonitoringPoint add(MonitoringPoint point) {
        // 检查监测点是否在配置的地区内
        if (!isPointInConfiguredRegion(point)) {
            throw new BusinessException(400, "只能添加配置地区内的监测点");
        }
        
        point.setStatus("available");
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
        
        // 检查更新后的监测点是否在配置的地区内
        if (!isPointInConfiguredRegion(point)) {
            throw new BusinessException(400, "只能更新配置地区内的监测点");
        }

        point.setUpdatedAt(LocalDateTime.now());
        monitoringPointMapper.updateById(point);
        return point;
    }

    /**
     * 删除重点关注区域
     */
    public void delete(String id) {
        // 检查要删除的监测点是否在配置的地区内
        getById(id);
        // 执行硬删除
        monitoringPointMapper.deleteById(id);
    }

    /**
     * 获取当前选中的监测点
     */
    public MonitoringPoint getSelected() {
        List<MonitoringPoint> selectedPoints = monitoringPointMapper.selectList(
                new LambdaQueryWrapper<MonitoringPoint>()
                        .eq(MonitoringPoint::getIsSelected, true)
                        .orderByDesc(MonitoringPoint::getUpdatedAt));
        
        // 过滤出在配置地区内的选中监测点
        List<MonitoringPoint> regionSelectedPoints = selectedPoints.stream()
                .filter(this::isPointInConfiguredRegion)
                .collect(Collectors.toList());
        
        if (!regionSelectedPoints.isEmpty()) {
            // 返回地区内的第一个选中监测点
            return regionSelectedPoints.get(0);
        } else {
            // 如果没有在地区内的选中监测点，返回地区内的第一个监测点
            List<MonitoringPoint> regionPoints = getAll();
            if (!regionPoints.isEmpty()) {
                // 自动选中第一个监测点
                MonitoringPoint firstPoint = regionPoints.get(0);
                updateSelected(firstPoint.getId());
                return firstPoint;
            } else {
                // 如果没有监测点，返回null
                return null;
            }
        }
    }

    /**
     * 更新选中的监测点
     */
    public void updateSelected(String pointId) {
        // 检查要选中的监测点是否在配置的地区内
        MonitoringPoint pointToSelect = getById(pointId);
        if (!isPointInConfiguredRegion(pointToSelect)) {
            throw new BusinessException(400, "只能选中配置地区内的监测点");
        }
        
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
        pointToSelect.setIsSelected(true);
        pointToSelect.setUpdatedAt(LocalDateTime.now());
        monitoringPointMapper.updateById(pointToSelect);
    }
}
