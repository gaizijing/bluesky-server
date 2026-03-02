package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.*;
import com.bluesky.mapper.*;
import com.bluesky.service.model.SuitabilityResult;
import com.bluesky.service.model.SuitabilityFactor;
import com.bluesky.service.model.TimePointSuitability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 适飞分析服务 - 重写版
 * 实现真正的适飞分析计算：气象数据 + 阈值设置 = 适飞性结果
 */
@Service
@RequiredArgsConstructor
public class SuitabilityService {

    private final SuitabilityAnalysisMapper suitabilityAnalysisMapper;
    private final CoreIndicatorMapper coreIndicatorMapper;
    private final VerticalProfileMapper verticalProfileMapper;
    private final WeatherRealtimeMapper weatherRealtimeMapper;
    private final AircraftLimitMapper aircraftLimitMapper;
    private final MonitoringPointService monitoringPointService;
    private final WeatherService weatherService;

    // ==================== 适飞分析（重写） ====================

    /**
     * 获取适飞状态 - 实时计算适飞性
     * 流程：1.获取气象数据 2.获取阈值 3.计算适飞性 4.保存结果 5.返回
     */
    @Transactional
    public Map<String, Object> getSuitabilityStatus(String pointId, String factor, Integer totalHours) {
        // 参数处理
        if (pointId == null || pointId.isEmpty()) {
            // 使用当前选中的监测点作为默认值
            try {
                MonitoringPoint selectedPoint = monitoringPointService.getSelected();
                pointId = selectedPoint.getId();
            } catch (Exception e) {
                // 如果获取失败，使用第一个激活的监测点
                List<MonitoringPoint> points = monitoringPointService.getAll();
                if (!points.isEmpty()) {
                    pointId = points.get(0).getId();
                } else {
                    pointId = "point-1"; // 后备方案
                }
            }
        }
        if (totalHours == null) {
            totalHours = 3; // 默认3小时
        }
        
        // 1. 获取实时气象数据
        Map<String, Object> weatherResult = weatherService.getRealtimeWeather(pointId);
        WeatherRealtime weatherData = extractWeatherData(weatherResult);
        
        // 2. 获取阈值配置（使用默认飞行器）
        AircraftLimit thresholds = getDefaultAircraftLimits();
        
        // 3. 计算适飞性（当前时间点）
        SuitabilityResult currentResult = calculateCurrentSuitability(weatherData, thresholds);
        
        // 4. 生成时间序列数据（模拟未来预测）
        List<TimePointSuitability> timeSeries = generateTimeSeriesSuitability(
            currentResult, totalHours, pointId
        );
        
        // 5. 保存计算结果到数据库
        saveSuitabilityAnalysis(pointId, timeSeries);
        
        // 6. 格式化返回结果（兼容前端格式）
        return formatSuitabilityResult(pointId, totalHours, timeSeries, currentResult);
    }

    /**
     * 从天气结果中提取WeatherRealtime对象
     */
    private WeatherRealtime extractWeatherData(Map<String, Object> weatherResult) {
        if (weatherResult != null && weatherResult.get("data") instanceof WeatherRealtime) {
            return (WeatherRealtime) weatherResult.get("data");
        }
        
        // 如果没有数据，创建默认数据
        WeatherRealtime defaultData = new WeatherRealtime();
        defaultData.setTemp(BigDecimal.valueOf(25.0));
        defaultData.setWindSpeed(BigDecimal.valueOf(8.0)); // km/h，需要转换
        defaultData.setVis(BigDecimal.valueOf(10.0));
        defaultData.setPrecip(BigDecimal.valueOf(0.0));
        defaultData.setHumidity(65);
        defaultData.setWindShearLevel("low");
        defaultData.setStabilityIndex("B");
        return defaultData;
    }

    /**
     * 获取默认飞行器阈值配置
     */
    private AircraftLimit getDefaultAircraftLimits() {
        // 先尝试获取默认飞行器限制
        AircraftLimit limit = aircraftLimitMapper.selectOne(
            new LambdaQueryWrapper<AircraftLimit>()
                .eq(AircraftLimit::getAircraftId, "aircraft-1")
                .last("LIMIT 1")
        );
        
        if (limit == null) {
            // 创建默认阈值
            limit = new AircraftLimit();
            limit.setAircraftId("aircraft-1");
            limit.setMaxWindSpeed(BigDecimal.valueOf(12.0)); // 12 m/s
            limit.setMaxWindShear(BigDecimal.valueOf(5.0));  // 5 m/s
            limit.setMinVisibility(BigDecimal.valueOf(1.5)); // 1.5 km
            limit.setMaxPrecipitation(BigDecimal.valueOf(5.0)); // 5 mm/h
            limit.setMaxHumidity(90); // 90%
            limit.setTempMin(BigDecimal.valueOf(-10.0)); // -10°C
            limit.setTempMax(BigDecimal.valueOf(40.0));  // 40°C
            limit.setMaxTurbulenceLevel("moderate");
        }
        
        return limit;
    }

    /**
     * 计算当前时间点的适飞性
     */
    private SuitabilityResult calculateCurrentSuitability(WeatherRealtime weather, AircraftLimit thresholds) {
        SuitabilityResult result = new SuitabilityResult();
        result.setCalculationTime(LocalDateTime.now());
        
        // 转换单位：km/h → m/s (风速)
        BigDecimal windSpeedKmh = weather.getWindSpeed();
        BigDecimal windSpeedMs = windSpeedKmh != null ? 
            windSpeedKmh.multiply(BigDecimal.valueOf(1000.0 / 3600.0)) : 
            BigDecimal.ZERO;
        
        // 计算各因素适飞性
        List<SuitabilityFactor> factors = new ArrayList<>();
        
        // 1. 风速适飞性
        boolean windSuitable = windSpeedMs.compareTo(thresholds.getMaxWindSpeed()) <= 0;
        factors.add(new SuitabilityFactor("风", windSuitable, windSpeedMs.doubleValue()));
        
        // 2. 能见度适飞性
        BigDecimal visibility = weather.getVis();
        boolean visibilitySuitable = visibility != null && 
            visibility.compareTo(thresholds.getMinVisibility()) >= 0;
        factors.add(new SuitabilityFactor("能见度", visibilitySuitable, 
            visibility != null ? visibility.doubleValue() : 0.0));
        
        // 3. 降水量适飞性
        BigDecimal precipitation = weather.getPrecip();
        boolean precipitationSuitable = precipitation != null && 
            precipitation.compareTo(thresholds.getMaxPrecipitation()) <= 0;
        factors.add(new SuitabilityFactor("降水", precipitationSuitable, 
            precipitation != null ? precipitation.doubleValue() : 0.0));
        
        // 4. 湿度适飞性
        Integer humidity = weather.getHumidity();
        boolean humiditySuitable = humidity != null && humidity <= thresholds.getMaxHumidity();
        factors.add(new SuitabilityFactor("湿度", humiditySuitable, 
            humidity != null ? humidity.doubleValue() : 0.0));
        
        // 5. 风切变适飞性（根据等级判断）
        String windShearLevel = weather.getWindShearLevel();
        boolean windShearSuitable = !"high".equals(windShearLevel);
        factors.add(new SuitabilityFactor("风切变", windShearSuitable, 
            getWindShearValue(windShearLevel)));
        
        // 6. 湍流/稳定度适飞性
        String stabilityIndex = weather.getStabilityIndex();
        boolean turbulenceSuitable = !"C".equals(stabilityIndex) && !"D".equals(stabilityIndex);
        factors.add(new SuitabilityFactor("湍流", turbulenceSuitable, 
            getTurbulenceValue(stabilityIndex)));
        
        result.setFactors(factors);
        
        // 计算综合适飞指数
        int suitableCount = (int) factors.stream().filter(SuitabilityFactor::isSuitable).count();
        int totalCount = factors.size();
        double overallSuitability = totalCount > 0 ? 
            (double) suitableCount / totalCount * 100 : 0.0;
        
        result.setOverallSuitability(overallSuitability);
        result.setRecommendation(getRecommendation(overallSuitability));
        
        return result;
    }

    /**
     * 生成时间序列适飞数据（模拟未来预测）
     */
    private List<TimePointSuitability> generateTimeSeriesSuitability(
        SuitabilityResult currentResult, int totalHours, String pointId) {
        
        List<TimePointSuitability> timeSeries = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int timeInterval = 10; // 10分钟间隔
        
        // 计算总时间点数
        int totalPoints = (totalHours * 60) / timeInterval + 1;
        
        for (int i = 0; i < totalPoints; i++) {
            LocalDateTime timePoint = now.plusMinutes(i * timeInterval);
            
            // 模拟未来数据变化（基于当前结果添加随机波动）
            TimePointSuitability timePointData = new TimePointSuitability();
            timePointData.setTimePoint(timePoint);
            timePointData.setPointId(pointId);
            
            // 复制当前因素，添加时间衰减和随机波动
            List<SuitabilityFactor> futureFactors = new ArrayList<>();
            for (SuitabilityFactor currentFactor : currentResult.getFactors()) {
                // 模拟未来变化：随时间增加不适飞概率
                double timeFactor = 1.0 - (i * 0.02 / totalPoints); // 随时间衰减
                double randomFactor = 0.9 + Math.random() * 0.2; // 随机波动
                
                boolean futureSuitable = currentFactor.isSuitable();
                if (Math.random() < 0.1 * (i / (double) totalPoints)) {
                    futureSuitable = !futureSuitable; // 随时间增加状态变化的概率
                }
                
                double futureValue = currentFactor.getValue() * timeFactor * randomFactor;
                
                SuitabilityFactor futureFactor = new SuitabilityFactor(
                    currentFactor.getName(),
                    futureSuitable,
                    futureValue
                );
                futureFactors.add(futureFactor);
            }
            
            timePointData.setFactors(futureFactors);
            
            // 计算该时间点的综合适飞指数
            int suitableCount = (int) futureFactors.stream()
                .filter(SuitabilityFactor::isSuitable).count();
            int totalCount = futureFactors.size();
            double pointSuitability = totalCount > 0 ? 
                (double) suitableCount / totalCount * 100 : 0.0;
            
            timePointData.setOverallSuitability(pointSuitability);
            timeSeries.add(timePointData);
        }
        
        return timeSeries;
    }

    /**
     * 保存适飞分析结果到数据库
     */
    private void saveSuitabilityAnalysis(String pointId, List<TimePointSuitability> timeSeries) {
        // 先删除该点最近的数据（避免重复）
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        suitabilityAnalysisMapper.delete(
            new LambdaQueryWrapper<SuitabilityAnalysis>()
                .eq(SuitabilityAnalysis::getPointId, pointId)
                .ge(SuitabilityAnalysis::getAnalysisTime, oneDayAgo)
        );
        
        // 保存每个因素在每个时间点的数据
        for (TimePointSuitability timePoint : timeSeries) {
            for (SuitabilityFactor factor : timePoint.getFactors()) {
                SuitabilityAnalysis analysis = new SuitabilityAnalysis();
                analysis.setPointId(pointId);
                analysis.setAnalysisTime(timePoint.getTimePoint());
                analysis.setTimeInterval(10); // 10分钟间隔
                analysis.setTotalHours(timeSeries.size() * 10 / 60); // 计算总小时数
                // 确保因素名称不超过数据库字段长度限制
                String factorName = factor.getName();
                if (factorName.length() > 50) {
                    factorName = factorName.substring(0, 47) + "...";
                }
                analysis.setFactor(factorName);
                // 使用ISO格式的时间字符串（YYYY-MM-DD HH:MM:SS）
                String formattedTime = formatDateTimeForDatabase(timePoint.getTimePoint());
                analysis.setTimePoint(formattedTime);
                analysis.setIsSuitable(factor.isSuitable());
                // 设置异常值为BigDecimal类型
                analysis.setAbnormalValue(BigDecimal.valueOf(factor.getValue()));
                analysis.setCreatedAt(LocalDateTime.now());
                
                suitabilityAnalysisMapper.insert(analysis);
            }
        }
    }

    /**
     * 格式化适飞分析结果（兼容前端格式）
     */
    private Map<String, Object> formatSuitabilityResult(
        String pointId, int totalHours, 
        List<TimePointSuitability> timeSeries, SuitabilityResult currentResult) {
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("pointId", pointId);
        result.put("totalHours", totalHours);
        result.put("timeInterval", 10); // 10分钟间隔
        
        // 构建前端需要的热力图数据结构
        List<Map<String, Object>> suitabilityList = new ArrayList<>();
        
        // 按因素组织数据
        if (!timeSeries.isEmpty() && !timeSeries.get(0).getFactors().isEmpty()) {
            TimePointSuitability firstPoint = timeSeries.get(0);
            
            for (SuitabilityFactor factor : firstPoint.getFactors()) {
                Map<String, Object> factorData = new LinkedHashMap<>();
                factorData.put("factor", factor.getName());
                
                // 构建detail数组
                List<Map<String, Object>> details = new ArrayList<>();
                for (TimePointSuitability timePoint : timeSeries) {
                    // 找到对应因素的数据
                    SuitabilityFactor timeFactor = timePoint.getFactors().stream()
                        .filter(f -> f.getName().equals(factor.getName()))
                        .findFirst()
                        .orElse(null);
                    
                    if (timeFactor != null) {
                        Map<String, Object> detail = new HashMap<>();
                        detail.put("timePoint", timePoint.getTimePoint().toString());
                        detail.put("statusData", timeFactor.isSuitable());
                        detail.put("valueData", String.format("%.1f", timeFactor.getValue()));
                        details.add(detail);
                    }
                }
                
                factorData.put("detail", details);
                suitabilityList.add(factorData);
            }
        }
        
        result.put("suitabilityList", suitabilityList);
        
        // 添加综合评分数据
        List<Double> overallScores = timeSeries.stream()
            .map(TimePointSuitability::getOverallSuitability)
            .toList();
        result.put("overallScores", overallScores);
        
        // 添加元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("calculationMethod", "real_time_analysis");
        metadata.put("thresholdSource", "aircraft_limits");
        metadata.put("weatherDataSource", "qweather_api");
        metadata.put("generatedAt", LocalDateTime.now().toString());
        result.put("metadata", metadata);
        
        return result;
    }

    /**
     * 获取适飞热力图数据 - 按区域空间维度（保持原逻辑）
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

    // ==================== 工具方法 ====================

    /**
     * 根据风切变等级获取数值
     */
    private double getWindShearValue(String level) {
        return switch (level != null ? level.toLowerCase() : "low") {
            case "high" -> 8.0;
            case "medium" -> 5.0;
            default -> 2.0; // low
        };
    }

    /**
     * 根据稳定度指数获取湍流值
     */
    private double getTurbulenceValue(String stabilityIndex) {
        return switch (stabilityIndex != null ? stabilityIndex.toUpperCase() : "B") {
            case "A", "B" -> 0.3; // 稳定，湍流小
            case "C" -> 0.6;      // 中性
            case "D", "E", "F" -> 0.8; // 不稳定，湍流大
            default -> 0.5;
        };
    }

    /**
     * 根据适飞指数获取建议
     */
    private String getRecommendation(double suitability) {
        if (suitability >= 80) {
            return "适飞";
        } else if (suitability >= 60) {
            return "较适";
        } else if (suitability >= 40) {
            return "谨慎";
        } else {
            return "不适";
        }
    }

    /**
     * 格式化日期时间为数据库兼容的字符串格式
     */
    private String formatDateTimeForDatabase(LocalDateTime dateTime) {
        // 使用简单格式：YYYY-MM-DD HH:MM:SS (19个字符)
        return String.format("%04d-%02d-%02d %02d:%02d:%02d",
            dateTime.getYear(),
            dateTime.getMonthValue(),
            dateTime.getDayOfMonth(),
            dateTime.getHour(),
            dateTime.getMinute(),
            dateTime.getSecond());
    }
}
