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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 适飞分析服务 - 重写版
 * 实现真正的适飞分析计算：气象数据 + 阈值设置 = 适飞性结果
 */
@Service
@RequiredArgsConstructor
public class SuitabilityService {

    private final SuitabilityAnalysisMapper suitabilityAnalysisMapper;
    private final VerticalProfileMapper verticalProfileMapper;
    private final AircraftLimitMapper aircraftLimitMapper;
    private final WeatherService weatherService;

    // ==================== 适飞分析（重写） ====================

    /**
     * 获取适飞状态 - 实时计算适飞性
     * 流程：1.获取气象数据（当前和预测） 2.获取阈值 3.计算适飞性 4.保存结果 5.返回
     */
    @Transactional
    public Map<String, Object> getSuitabilityStatus(String pointId, String factor, Integer totalHours) {
        // 1. 检查数据库是否有1小时内的适飞分析数据
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<SuitabilityAnalysis> recentAnalysis = suitabilityAnalysisMapper.selectList(
            new LambdaQueryWrapper<SuitabilityAnalysis>()
                .eq(SuitabilityAnalysis::getPointId, pointId)
                .ge(SuitabilityAnalysis::getCreatedAt, oneHourAgo)
                .orderByDesc(SuitabilityAnalysis::getCreatedAt)
        );
        
        // 如果有最近数据，直接使用
        if (!recentAnalysis.isEmpty()) {
            // 构建时间序列数据从数据库
            List<TimePointSuitability> timeSeries = buildTimeSeriesFromDatabase(recentAnalysis, totalHours, pointId);
            if (!timeSeries.isEmpty()) {
                // 计算当前结果
                SuitabilityResult currentResult = calculateCurrentSuitabilityFromDatabase(timeSeries);
                return formatSuitabilityResult(pointId, totalHours, timeSeries, currentResult, factor);
            }
        }
        
        // 2. 获取实时气象数据和预测气象数据
        Map<String, Object> weatherResult = weatherService.getRealtimeWeather(pointId);
        WeatherRealtime weatherData = extractWeatherData(weatherResult);
        
        // 3. 获取预测气象数据
        Map<String, Object> forecastResult = weatherService.getWeatherForecastTrend(pointId);
        List<WeatherForecast> forecastData = getForecastDataFromResult(forecastResult, pointId, totalHours);
        
        // 4. 获取阈值配置（使用默认飞行器）
        AircraftLimit thresholds = getDefaultAircraftLimits();
        
        // 5. 计算适飞性（当前时间点和未来时间点）
        List<TimePointSuitability> timeSeries = calculateSuitabilityTimeSeries(weatherData, forecastData, thresholds, totalHours, pointId);
        
        // 6. 保存计算结果到数据库
        saveSuitabilityAnalysis(pointId, timeSeries);
        
        // 7. 格式化返回结果（兼容前端格式）
        SuitabilityResult currentResult = calculateCurrentSuitability(weatherData, thresholds);
        return formatSuitabilityResult(pointId, totalHours, timeSeries, currentResult, factor);
    }

    /**
     * 从数据库构建时间序列数据
     */
    private List<TimePointSuitability> buildTimeSeriesFromDatabase(List<SuitabilityAnalysis> analysisList, int totalHours, String pointId) {
        Map<LocalDateTime, Map<String, SuitabilityFactor>> timePointMap = new TreeMap<>();
        
        // 解析数据库数据
        for (SuitabilityAnalysis analysis : analysisList) {
            LocalDateTime timePoint = analysis.getAnalysisTime();
            String factorName = analysis.getFactor();
            
            timePointMap.computeIfAbsent(timePoint, k -> new HashMap<>());
            
            SuitabilityFactor factor = new SuitabilityFactor(
                factorName,
                analysis.getIsSuitable(),
                analysis.getAbnormalValue().doubleValue()
            );
            
            timePointMap.get(timePoint).put(factorName, factor);
        }
        
        // 构建时间序列
        List<TimePointSuitability> timeSeries = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int timeInterval = 15; // 15分钟间隔    
        int totalPoints = totalHours * 4 + 1; // 每小时4个点，加上当前时间点
        
        for (int i = 0; i < totalPoints; i++) {
            LocalDateTime timePoint = now.plusMinutes(i * timeInterval);
            
            // 找到最接近的时间点数据
            LocalDateTime closestTime = null;
            for (LocalDateTime t : timePointMap.keySet()) {
                if (t.isAfter(timePoint.minusMinutes(15)) && t.isBefore(timePoint.plusMinutes(15))) {
                    closestTime = t;
                    break;
                }
            }
            
            if (closestTime != null) {
                TimePointSuitability timePointData = new TimePointSuitability();
                timePointData.setTimePoint(timePoint);
                timePointData.setPointId(pointId);
                
                List<SuitabilityFactor> factors = new ArrayList<>(timePointMap.get(closestTime).values());
                timePointData.setFactors(factors);
                
                // 计算综合适飞指数
                int suitableCount = (int) factors.stream().filter(SuitabilityFactor::isSuitable).count();
                int totalCount = factors.size();
                double pointSuitability = totalCount > 0 ? 
                    (double) suitableCount / totalCount * 100 : 0.0;
                
                timePointData.setOverallSuitability(pointSuitability);
                timeSeries.add(timePointData);
            }
        }
        
        return timeSeries;
    }

    /**
     * 从数据库时间序列计算当前适飞结果
     */
    private SuitabilityResult calculateCurrentSuitabilityFromDatabase(List<TimePointSuitability> timeSeries) {
        if (timeSeries.isEmpty()) {
            return new SuitabilityResult();
        }
        
        TimePointSuitability firstPoint = timeSeries.get(0);
        SuitabilityResult result = new SuitabilityResult();
        result.setCalculationTime(LocalDateTime.now());
        result.setFactors(firstPoint.getFactors());
        result.setOverallSuitability(firstPoint.getOverallSuitability());
        result.setRecommendation(getRecommendation(firstPoint.getOverallSuitability()));
        
        return result;
    }

    /**
     * 计算适飞性时间序列
     */
    private List<TimePointSuitability> calculateSuitabilityTimeSeries(
        WeatherRealtime weatherData, List<WeatherForecast> forecastData, 
        AircraftLimit thresholds, int totalHours, String pointId) {
        
        List<TimePointSuitability> timeSeries = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int timeInterval = 15; // 15分钟间隔    
        int totalPoints = totalHours * 4 + 1; // 每小时4个点，不包含当前时间点        
        // 计算当前时间点
        TimePointSuitability currentPoint = new TimePointSuitability();
        currentPoint.setTimePoint(now);
        currentPoint.setPointId(pointId);
        
        SuitabilityResult currentResult = calculateCurrentSuitability(weatherData, thresholds);
        currentPoint.setFactors(currentResult.getFactors());
        currentPoint.setOverallSuitability(currentResult.getOverallSuitability());
        timeSeries.add(currentPoint);
        
        // 计算未来时间点
        for (int i = 1; i < totalPoints; i++) {
            LocalDateTime timePoint = now.plusMinutes(i * timeInterval);
            
            // 找到对应的预测数据
            WeatherForecast forecast = findForecastByTime(forecastData, timePoint);
            if (forecast != null) {
                TimePointSuitability futurePoint = new TimePointSuitability();
                futurePoint.setTimePoint(timePoint);
                futurePoint.setPointId(pointId);
                
                List<SuitabilityFactor> factors = calculateForecastSuitability(forecast, thresholds);
                futurePoint.setFactors(factors);
                
                // 计算综合适飞指数
                int suitableCount = (int) factors.stream().filter(SuitabilityFactor::isSuitable).count();
                int totalCount = factors.size();
                double pointSuitability = totalCount > 0 ? 
                    (double) suitableCount / totalCount * 100 : 0.0;
                
                futurePoint.setOverallSuitability(pointSuitability);
                timeSeries.add(futurePoint);
            }
        }
        
        return timeSeries;
    }

    /**
     * 从预测结果中提取WeatherForecast数据
     */
    private List<WeatherForecast> getForecastDataFromResult(Map<String, Object> forecastResult, String pointId, int totalHours) {
        List<WeatherForecast> forecastData = new ArrayList<>();
        
        if (forecastResult != null && forecastResult.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) forecastResult.get("data");
            
            List<String> times = (List<String>) data.get("time");
            List<Double> windSpeeds = (List<Double>) data.get("wind_speed_10m");
            List<Integer> visibility = (List<Integer>) data.get("visibility");
            List<Double> precipitation = (List<Double>) data.get("precipitation");
            
            if (times != null && !times.isEmpty()) {
                for (int i = 0; i < times.size(); i++) {
                    WeatherForecast forecast = new WeatherForecast();
                    forecast.setPointId(pointId);
                    
                    // 处理时间字符串，添加当前日期并使用正确的格式解析
                    String timeStr = times.get(i);
                    LocalDate today = LocalDate.now();
                    String dateTimeStr = today + " " + timeStr;
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    forecast.setForecastTime(LocalDateTime.parse(dateTimeStr, formatter));
                    
                    if (windSpeeds != null && i < windSpeeds.size()) {
                        forecast.setWindSpeed(BigDecimal.valueOf(windSpeeds.get(i)));
                    }
                    
                    if (visibility != null && i < visibility.size()) {
                        forecast.setVisibility(BigDecimal.valueOf(visibility.get(i) / 1000.0)); // 转换为km
                    }
                    
                    if (precipitation != null && i < precipitation.size()) {
                        forecast.setPrecipitation(BigDecimal.valueOf(precipitation.get(i)));
                    }
                    
                    forecastData.add(forecast);
                }
            }
        }
        
        return forecastData;
    }

    /**
     * 根据时间找到对应的预测数据
     */
    private WeatherForecast findForecastByTime(List<WeatherForecast> forecastData, LocalDateTime timePoint) {
        for (WeatherForecast forecast : forecastData) {
            LocalDateTime forecastTime = forecast.getForecastTime();
            if (forecastTime.isAfter(timePoint.minusMinutes(30)) && forecastTime.isBefore(timePoint.plusMinutes(30))) {
                return forecast;
            }
        }
        return null;
    }

    /**
     * 计算预测数据的适飞性
     */
    private List<SuitabilityFactor> calculateForecastSuitability(WeatherForecast forecast, AircraftLimit thresholds) {
        List<SuitabilityFactor> factors = new ArrayList<>();
        
        // 1. 风速适飞性
        BigDecimal windSpeed = forecast.getWindSpeed();
        boolean windSuitable = windSpeed != null && windSpeed.compareTo(thresholds.getMaxWindSpeed()) <= 0;
        factors.add(new SuitabilityFactor("风", windSuitable, windSpeed != null ? windSpeed.doubleValue() : 0.0));
        
        // 2. 能见度适飞性
        BigDecimal visibility = forecast.getVisibility();
        boolean visibilitySuitable = visibility != null && 
            visibility.compareTo(thresholds.getMinVisibility()) >= 0;
        factors.add(new SuitabilityFactor("能见度", visibilitySuitable, 
            visibility != null ? visibility.doubleValue() : 0.0));
        
        // 3. 降水量适飞性
        BigDecimal precipitation = forecast.getPrecipitation();
        boolean precipitationSuitable = precipitation != null && 
            precipitation.compareTo(thresholds.getMaxPrecipitation()) <= 0;
        factors.add(new SuitabilityFactor("降水", precipitationSuitable, 
            precipitation != null ? precipitation.doubleValue() : 0.0));
        
        // 4. 湿度适飞性（使用默认值，因为预报数据中可能没有）
        boolean humiditySuitable = true; // 默认为适飞
        factors.add(new SuitabilityFactor("湿度", humiditySuitable, 0.0));
        
        // 5. 风切变适飞性（使用默认值，因为预报数据中可能没有）
        boolean windShearSuitable = true; // 默认为适飞
        factors.add(new SuitabilityFactor("风切变", windShearSuitable, 2.0)); // 默认为低等级
        
        // 6. 湍流/稳定度适飞性（使用默认值，因为预报数据中可能没有）
        boolean turbulenceSuitable = true; // 默认为适飞
        factors.add(new SuitabilityFactor("湍流", turbulenceSuitable, 0.3)); // 默认为稳定
        
        return factors;
    }

    /**
     * 从天气结果中提取WeatherRealtime对象
     */
    private WeatherRealtime extractWeatherData(Map<String, Object> weatherResult) {
        if (weatherResult != null && weatherResult.get("data") instanceof WeatherRealtime) {
            return (WeatherRealtime) weatherResult.get("data");
        }
        
        return null;
    }

    /**
     * 获取默认飞行器阈值配置
     */
    private AircraftLimit getDefaultAircraftLimits() {
        // 先尝试获取默认飞行器限制
        AircraftLimit limit = aircraftLimitMapper.selectOne(
            new LambdaQueryWrapper<AircraftLimit>()
                .eq(AircraftLimit::getAircraftId, "default")
                .last("LIMIT 1")
        );
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
                analysis.setTimeInterval(15); // 15分钟间隔
                analysis.setTotalHours(timeSeries.size() * 15 / 60); // 计算总小时数
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
        List<TimePointSuitability> timeSeries, SuitabilityResult currentResult, String factor) {
        
        Map<String, Object> result = new LinkedHashMap<>();
        
        // 构建前端需要的数据结构
        List<String> factors = new ArrayList<>();
        List<List<Integer>> statusData = new ArrayList<>();
        List<List<Double>> valueData = new ArrayList<>();
        
        // 按因素组织数据
        if (!timeSeries.isEmpty() && !timeSeries.get(0).getFactors().isEmpty()) {
            TimePointSuitability firstPoint = timeSeries.get(0);
            
            for (SuitabilityFactor factorItem : firstPoint.getFactors()) {
                // 如果指定了因素，只返回该因素
                if (factor != null && !factor.isEmpty() && !factorItem.getName().equals(factor)) {
                    continue;
                }
                
                factors.add(factorItem.getName());
                
                // 构建该因素的statusData和valueData
                List<Integer> statusList = new ArrayList<>();
                List<Double> valueList = new ArrayList<>();
                
                for (TimePointSuitability timePoint : timeSeries) {
                    // 找到对应因素的数据
                    SuitabilityFactor timeFactor = timePoint.getFactors().stream()
                        .filter(f -> f.getName().equals(factorItem.getName()))
                        .findFirst()
                        .orElse(null);
                    
                    if (timeFactor != null) {
                        statusList.add(timeFactor.isSuitable() ? 1 : 0);
                        valueList.add(Math.round(timeFactor.getValue() * 100.0) / 100.0);
                    }
                }
                
                statusData.add(statusList);
                valueData.add(valueList);
            }
        }
        
        result.put("factors", factors);
        result.put("statusData", statusData);
        result.put("valueData", valueData);
        
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
