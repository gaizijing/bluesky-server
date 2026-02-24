package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.*;
import com.bluesky.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 气象数据服务
 * 负责实时天气、风向趋势、风场、微尺度天气等数据
 */
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final WeatherRealtimeMapper weatherRealtimeMapper;
    private final WindTrendMapper windTrendMapper;
    private final WindFieldMapper windFieldMapper;
    private final MicroscaleWeatherMapper microscaleWeatherMapper;

    // ==================== 实时气象 ====================

    /**
     * 获取重点关注区域实时气象数据
     */
    public Map<String, Object> getRealtimeWeather(String pointId) {
        WeatherRealtime latest = weatherRealtimeMapper.selectOne(
                new LambdaQueryWrapper<WeatherRealtime>()
                        .eq(WeatherRealtime::getPointId, pointId)
                        .orderByDesc(WeatherRealtime::getObsTime)
                        .last("LIMIT 1"));

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("data", latest);
        return result;
    }

    /**
     * 获取风向趋势数据(用于折线图)
     */
    public Map<String, Object> getWindTrend(String pointId, String timeRange) {
        LocalDateTime[] range = parseTimeRange(timeRange);
        List<WindTrend> trends = windTrendMapper.selectList(
                new LambdaQueryWrapper<WindTrend>()
                        .eq(WindTrend::getPointId, pointId)
                        .between(WindTrend::getDataTime, range[0], range[1])
                        .orderByAsc(WindTrend::getDataTime));

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("pointId", pointId);
        result.put("data", trends);
        return result;
    }

    /**
     * 获取3D风场数据(用于Cesium粒子渲染)
     */
    public Map<String, Object> getWindField(String timeRange, Integer height) {
        LambdaQueryWrapper<WindField> wrapper = new LambdaQueryWrapper<WindField>()
                .orderByDesc(WindField::getDataTime)
                .last("LIMIT 500");

        if (height != null) {
            wrapper.eq(WindField::getHeight, height);
        }

        List<WindField> fields = windFieldMapper.selectList(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("height", height);
        result.put("data", fields);
        return result;
    }

    /**
     * 获取微尺度天气数据(热力图)
     */
    public Map<String, Object> getMicroscaleWeather(String region, String timeRange) {
        LambdaQueryWrapper<MicroscaleWeather> wrapper = new LambdaQueryWrapper<MicroscaleWeather>()
                .orderByDesc(MicroscaleWeather::getDataTime)
                .last("LIMIT 1000");

        if (region != null && !region.isEmpty()) {
            wrapper.eq(MicroscaleWeather::getRegion, region);
        }

        List<MicroscaleWeather> list = microscaleWeatherMapper.selectList(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("region", region);
        result.put("data", list);
        return result;
    }

    // ==================== 工具方法 ====================
    private LocalDateTime[] parseTimeRange(String timeRange) {
        if (timeRange == null || !timeRange.contains(",")) {
            return new LocalDateTime[] {
                    LocalDateTime.now().minusHours(24),
                    LocalDateTime.now()
            };
        }
        String[] parts = timeRange.split(",");
        return new LocalDateTime[] {
                LocalDateTime.parse(parts[0].trim().replace(" ", "T")),
                LocalDateTime.parse(parts[1].trim().replace(" ", "T"))
        };
    }
}
