package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.config.RegionConfig;
import com.bluesky.entity.RegionConfigEntity;
import com.bluesky.event.RegionConfigEvent;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.RegionConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 地区配置服务
 *
 * @author BlueSky Team
 */
@Service
@RequiredArgsConstructor
public class RegionConfigService {

    private final RegionConfigMapper regionConfigMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 获取所有地区配置
     */
    public List<RegionConfigEntity> getAll() {
        return regionConfigMapper.selectList(null);
    }

    /**
     * 获取默认地区配置
     */
    public RegionConfigEntity getDefaultConfig() {
        RegionConfigEntity config = regionConfigMapper.getDefaultConfig();
        if (config == null) {
            throw new BusinessException(404, "默认地区配置不存在");
        }
        return config;
    }

    /**
     * 根据ID获取地区配置
     */
    public RegionConfigEntity getById(Long id) {
        RegionConfigEntity config = regionConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(404, "地区配置不存在");
        }
        return config;
    }

    /**
     * 添加地区配置
     */
    public RegionConfigEntity add(RegionConfigEntity config) {
        // 如果设置为默认配置，先取消其他配置的默认状态
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            List<RegionConfigEntity> existingConfigs = regionConfigMapper.selectList(
                    new LambdaQueryWrapper<RegionConfigEntity>()
                            .eq(RegionConfigEntity::getIsDefault, true)
            );
            for (RegionConfigEntity existingConfig : existingConfigs) {
                existingConfig.setIsDefault(false);
                existingConfig.setUpdatedAt(LocalDateTime.now());
                regionConfigMapper.updateById(existingConfig);
            }
        }

        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        regionConfigMapper.insert(config);
        
        // 发布地区配置更新事件
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            eventPublisher.publishEvent(new RegionConfigEvent(this, config));
        }
        
        return config;
    }

    /**
     * 更新地区配置
     */
    public RegionConfigEntity update(RegionConfigEntity config) {
        // 检查配置是否存在
        getById(config.getId());

        // 如果设置为默认配置，先取消其他配置的默认状态
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            List<RegionConfigEntity> existingConfigs = regionConfigMapper.selectList(
                    new LambdaQueryWrapper<RegionConfigEntity>()
                            .eq(RegionConfigEntity::getIsDefault, true)
                            .ne(RegionConfigEntity::getId, config.getId())
            );
            for (RegionConfigEntity existingConfig : existingConfigs) {
                existingConfig.setIsDefault(false);
                existingConfig.setUpdatedAt(LocalDateTime.now());
                regionConfigMapper.updateById(existingConfig);
            }
        }

        config.setUpdatedAt(LocalDateTime.now());
        regionConfigMapper.updateById(config);
        
        // 发布地区配置更新事件
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            eventPublisher.publishEvent(new RegionConfigEvent(this, config));
        }
        
        return config;
    }

    /**
     * 删除地区配置
     */
    public void delete(Long id) {
        // 检查配置是否存在
        RegionConfigEntity config = getById(id);

        // 不允许删除默认配置
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            throw new BusinessException(400, "不能删除默认地区配置");
        }

        regionConfigMapper.deleteById(id);
    }

    /**
     * 将数据库配置转换为RegionConfig
     */
    public RegionConfig convertToRegionConfig(RegionConfigEntity entity) {
        RegionConfig config = new RegionConfig();
        config.setDefaultName(entity.getName());

        RegionConfig.Bounds bounds = new RegionConfig.Bounds();
        bounds.setWest(entity.getWest());
        bounds.setEast(entity.getEast());
        bounds.setSouth(entity.getSouth());
        bounds.setNorth(entity.getNorth());

        config.setBounds(bounds);
        return config;
    }
}
