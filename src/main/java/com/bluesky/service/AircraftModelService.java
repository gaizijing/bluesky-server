package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.AircraftModel;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.AircraftModelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 飞行器模型服务
 */
@Service
@RequiredArgsConstructor
public class AircraftModelService {

    private final AircraftModelMapper aircraftModelMapper;

    /**
     * 获取所有飞行器模型
     */
    public List<AircraftModel> getAll() {
        return aircraftModelMapper.selectList(
                new LambdaQueryWrapper<AircraftModel>()
                        .orderByDesc(AircraftModel::getUpdatedAt)
        );
    }

    /**
     * 根据ID获取飞行器模型
     */
    public AircraftModel getById(String id) {
        AircraftModel model = aircraftModelMapper.selectById(id);
        if (model == null) {
            throw new BusinessException(404, "飞行器模型不存在");
        }
        return model;
    }

    /**
     * 添加飞行器模型
     */
    public AircraftModel add(AircraftModel model) {
        // 检查是否已存在相同ID的模型
        AircraftModel existing = aircraftModelMapper.selectById(model.getId());
        if (existing != null) {
            throw new BusinessException(400, "该飞行器模型ID已存在");
        }

        model.setCreatedAt(LocalDateTime.now());
        model.setUpdatedAt(LocalDateTime.now());
        aircraftModelMapper.insert(model);
        return model;
    }

    /**
     * 更新飞行器模型
     */
    public AircraftModel update(AircraftModel model) {
        AircraftModel existing = getById(model.getId());
        if (existing == null) {
            throw new BusinessException(404, "飞行器模型不存在");
        }

        model.setUpdatedAt(LocalDateTime.now());
        aircraftModelMapper.updateById(model);
        return model;
    }

    /**
     * 删除飞行器模型
     */
    public void delete(String id) {
        AircraftModel existing = getById(id);
        if (existing == null) {
            throw new BusinessException(404, "飞行器模型不存在");
        }
        aircraftModelMapper.deleteById(id);
    }

    /**
     * 获取启用的飞行器模型
     */
    public List<AircraftModel> getActiveModels() {
        return aircraftModelMapper.selectList(
                new LambdaQueryWrapper<AircraftModel>()
                        .eq(AircraftModel::getIsActive, true)
                        .orderByDesc(AircraftModel::getUpdatedAt)
        );
    }
}
