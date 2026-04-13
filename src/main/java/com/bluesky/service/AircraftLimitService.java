package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.AircraftLimit;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.AircraftLimitMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 飞行器气象阈值服务
 */
@Service
@RequiredArgsConstructor
public class AircraftLimitService {

    private final AircraftLimitMapper aircraftLimitMapper;

    /**
     * 获取所有阈值配置
     */
    public List<AircraftLimit> getAll() {
        return aircraftLimitMapper.selectList(
                new LambdaQueryWrapper<AircraftLimit>()
                        .orderByDesc(AircraftLimit::getUpdatedAt)
        );
    }

    /**
     * 根据ID获取阈值配置
     */
    public AircraftLimit getById(Long id) {
        AircraftLimit limit = aircraftLimitMapper.selectById(id);
        if (limit == null) {
            throw new BusinessException(404, "阈值配置不存在");
        }
        return limit;
    }

    /**
     * 根据飞行器ID获取阈值配置
     */
    public AircraftLimit getByAircraftId(String aircraftId) {
        return aircraftLimitMapper.selectOne(
                new LambdaQueryWrapper<AircraftLimit>()
                        .eq(AircraftLimit::getAircraftId, aircraftId)
                        .last("LIMIT 1")
        );
    }

    /**
     * 添加阈值配置
     */
    public AircraftLimit add(AircraftLimit limit) {
        // 检查是否已存在相同飞行器的配置
        AircraftLimit existing = getByAircraftId(limit.getAircraftId());
        if (existing != null) {
            throw new BusinessException(400, "该飞行器的阈值配置已存在");
        }

        limit.setCreatedAt(LocalDateTime.now());
        limit.setUpdatedAt(LocalDateTime.now());
        aircraftLimitMapper.insert(limit);
        return limit;
    }

    /**
     * 更新阈值配置
     */
    public AircraftLimit update(AircraftLimit limit) {
        AircraftLimit existing = getById(limit.getId());
        if (existing == null) {
            throw new BusinessException(404, "阈值配置不存在");
        }

        // 如果飞行器ID变更，检查新ID是否已存在
        if (!existing.getAircraftId().equals(limit.getAircraftId())) {
            AircraftLimit duplicate = getByAircraftId(limit.getAircraftId());
            if (duplicate != null && !duplicate.getId().equals(limit.getId())) {
                throw new BusinessException(400, "新飞行器ID的阈值配置已存在");
            }
        }

        limit.setUpdatedAt(LocalDateTime.now());
        aircraftLimitMapper.updateById(limit);
        return limit;
    }

    /**
     * 删除阈值配置
     */
    public void delete(Long id) {
        AircraftLimit existing = getById(id);
        if (existing == null) {
            throw new BusinessException(404, "阈值配置不存在");
        }
        aircraftLimitMapper.deleteById(id);
    }

    /**
     * 获取默认阈值配置（与飞行器无关）
     */
    public AircraftLimit getDefaultLimits() {
        // 查找默认配置（使用特殊的aircraftId来标识默认配置）
        AircraftLimit limit = getByAircraftId("default");
        if (limit == null) {
            // 如果没有默认配置，创建一个
            limit = new AircraftLimit();
            limit.setAircraftId("default");
            limit.setMaxWindSpeed(new java.math.BigDecimal("12.0"));
            limit.setMaxWindShear(new java.math.BigDecimal("5.0"));
            limit.setMinVisibility(new java.math.BigDecimal("1.5"));
            limit.setMaxPrecipitation(new java.math.BigDecimal("5.0"));
            limit.setMinCloudBase(300);
            limit.setTempMin(new java.math.BigDecimal("-20.0"));
            limit.setTempMax(new java.math.BigDecimal("40.0"));
            limit.setMaxHumidity(90);
            limit.setMaxTurbulenceLevel("medium");
            limit.setCreatedAt(LocalDateTime.now());
            limit.setUpdatedAt(LocalDateTime.now());
            aircraftLimitMapper.insert(limit);
        }
        return limit;
    }

    /**
     * 更新默认阈值配置
     */
    public AircraftLimit updateDefaultLimits(AircraftLimit limit) {
        // 查找默认配置
        AircraftLimit existing = getByAircraftId("default");
        if (existing == null) {
            // 如果不存在，创建一个
            limit.setAircraftId("default");
            limit.setCreatedAt(LocalDateTime.now());
            limit.setUpdatedAt(LocalDateTime.now());
            aircraftLimitMapper.insert(limit);
        } else {
            // 如果存在，更新它
            limit.setId(existing.getId());
            limit.setAircraftId("default");
            limit.setUpdatedAt(LocalDateTime.now());
            aircraftLimitMapper.updateById(limit);
        }
        return limit;
    }
}
