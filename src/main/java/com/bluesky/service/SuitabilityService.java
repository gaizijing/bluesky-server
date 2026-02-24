package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.CoreIndicator;
import com.bluesky.entity.SuitabilityAnalysis;
import com.bluesky.entity.VerticalProfile;
import com.bluesky.mapper.CoreIndicatorMapper;
import com.bluesky.mapper.SuitabilityAnalysisMapper;
import com.bluesky.mapper.VerticalProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 适飞分析服务
 * 负责适飞状态、核心气象要素、垂直剖面数据
 */
@Service
@RequiredArgsConstructor
public class SuitabilityService {

    private final SuitabilityAnalysisMapper suitabilityAnalysisMapper;
    private final CoreIndicatorMapper coreIndicatorMapper;
    private final VerticalProfileMapper verticalProfileMapper;

    // ==================== 适飞分析 ====================

    /**
     * 获取适飞状态 - 按时间维度/气象因素返回热力图数据
     * factor: 综合/风/风切变/颠簸指数/湍流/降水/能见度
     */
    public Map<String, Object> getSuitabilityStatus(String pointId, String factor, Integer totalHours) {
        LambdaQueryWrapper<SuitabilityAnalysis> wrapper = new LambdaQueryWrapper<SuitabilityAnalysis>()
                .eq(SuitabilityAnalysis::getPointId, pointId)
                .eq(factor != null && !factor.isEmpty(), SuitabilityAnalysis::getFactor, factor)
                .ge(SuitabilityAnalysis::getAnalysisTime,
                        LocalDateTime.now().minusHours(totalHours != null ? totalHours : 24))
                .orderByAsc(SuitabilityAnalysis::getAnalysisTime);

        List<SuitabilityAnalysis> list = suitabilityAnalysisMapper.selectList(wrapper);

        // 按因素分组
        Map<String, List<SuitabilityAnalysis>> grouped = new LinkedHashMap<>();
        for (SuitabilityAnalysis item : list) {
            grouped.computeIfAbsent(item.getFactor(), k -> new ArrayList<>()).add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("pointId", pointId);
        result.put("totalHours", totalHours != null ? totalHours : 24);
        result.put("factors", grouped);
        return result;
    }

    /**
     * 获取适飞热力图数据 - 按区域空间维度
     */
    public Map<String, Object> getSuitabilityHeatmap(String timePoint, String factor) {
        LambdaQueryWrapper<SuitabilityAnalysis> wrapper = new LambdaQueryWrapper<SuitabilityAnalysis>()
                .eq(factor != null && !factor.isEmpty(), SuitabilityAnalysis::getFactor, factor)
                .orderByDesc(SuitabilityAnalysis::getAnalysisTime)
                .last("LIMIT 200");

        List<SuitabilityAnalysis> list = suitabilityAnalysisMapper.selectList(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("timePoint", timePoint);
        result.put("factor", factor);
        result.put("data", list);
        return result;
    }

    // ==================== 核心气象要素 ====================

    /**
     * 获取核心气象要素监测数据
     */
    public Map<String, Object> getCoreIndicators(String pointId) {
        List<CoreIndicator> indicators = coreIndicatorMapper.selectList(
                new LambdaQueryWrapper<CoreIndicator>()
                        .eq(pointId != null, CoreIndicator::getPointId, pointId)
                        .orderByDesc(CoreIndicator::getDataTime)
                        .last("LIMIT 50"));

        // 取每种要素的最新记录
        Map<String, CoreIndicator> latestMap = new LinkedHashMap<>();
        for (CoreIndicator item : indicators) {
            latestMap.putIfAbsent(item.getIndicatorId(), item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("pointId", pointId);
        result.put("indicators", new ArrayList<>(latestMap.values()));
        return result;
    }

    // ==================== 垂直剖面 ====================

    /**
     * 获取垂直剖面数据 (各高度层气象要素分布)
     * timeType: current/1h/3h/6h
     */
    public Map<String, Object> getVerticalProfile(String pointId, String timeType) {
        LocalDateTime targetTime = switch (timeType != null ? timeType : "current") {
            case "1h" -> LocalDateTime.now().minusHours(1);
            case "3h" -> LocalDateTime.now().minusHours(3);
            case "6h" -> LocalDateTime.now().minusHours(6);
            default -> LocalDateTime.now().minusMinutes(30);
        };

        List<VerticalProfile> profiles = verticalProfileMapper.selectList(
                new LambdaQueryWrapper<VerticalProfile>()
                        .eq(VerticalProfile::getPointId, pointId)
                        .ge(VerticalProfile::getDataTime, targetTime)
                        .orderByAsc(VerticalProfile::getHeight));

        // 安全阈值（示例）
        Map<String, Object> safetyThresholds = new HashMap<>();
        safetyThresholds.put("maxWindSpeed", 15.0);
        safetyThresholds.put("minVisibility", 1.0);
        safetyThresholds.put("maxHumidity", 95);

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("pointId", pointId);
        result.put("timeType", timeType);
        result.put("heightLayers", profiles);
        result.put("safetyThresholds", safetyThresholds);
        return result;
    }
}
