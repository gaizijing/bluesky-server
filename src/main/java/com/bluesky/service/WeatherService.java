package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.*;
import com.bluesky.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private final AircraftLimitMapper aircraftLimitMapper;
    private final MonitoringPointService monitoringPointService;

    // ==================== 实时气象 ====================

    /**
     * 获取重点关注区域实时气象数据
     * 先查数据库，如果没有则调用和风天气API
     */
    public Map<String, Object> getRealtimeWeather(String pointId) {
        // 1. 先查数据库
        WeatherRealtime latest = weatherRealtimeMapper.selectOne(
                new LambdaQueryWrapper<WeatherRealtime>()
                        .eq(WeatherRealtime::getPointId, pointId)
                        .orderByDesc(WeatherRealtime::getObsTime)
                        .last("LIMIT 1"));

        // 2. 如果数据库有数据且是最近1小时内的，直接返回
        if (latest != null && latest.getObsTime() != null) {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            if (latest.getObsTime().isAfter(oneHourAgo)) {
                Map<String, Object> result = new HashMap<>();
                result.put("updateTime", LocalDateTime.now().toString());
                result.put("data", latest);
                return result;
            }
        }

        // 3. 数据库没有或数据过期，调用和风天气API
        try {
            // 根据pointId从monitoring_points表查询坐标
            MonitoringPoint point = monitoringPointService.getById(pointId);
            double longitude = point.getLongitude().doubleValue();
            double latitude = point.getLatitude().doubleValue();
            
            // 调用和风天气API
            Map<String, Object> weatherData = callQWeatherAPI(longitude, latitude);
            
            // 4. 保存到数据库
            WeatherRealtime newRecord = new WeatherRealtime();
            newRecord.setPointId(pointId);
            newRecord.setObsTime(LocalDateTime.now());
            newRecord.setTemp(new BigDecimal(weatherData.get("temp").toString()));
            newRecord.setFeelsLike(new BigDecimal(weatherData.get("feelsLike").toString()));
            newRecord.setIcon(weatherData.get("icon").toString());
            newRecord.setText(weatherData.get("text").toString());
            newRecord.setWind360(Integer.parseInt(weatherData.get("wind360").toString()));
            newRecord.setWindDir(weatherData.get("windDir").toString());
            newRecord.setWindScale((weatherData.get("windScale").toString()));
            newRecord.setWindSpeed(new BigDecimal(weatherData.get("windSpeed").toString()));
            newRecord.setHumidity(Integer.parseInt(weatherData.get("humidity").toString()));
            newRecord.setPrecip(new BigDecimal(weatherData.get("precip").toString()));
            newRecord.setPressure(new BigDecimal(weatherData.get("pressure").toString()));
            newRecord.setVis(new BigDecimal(weatherData.get("vis").toString()));
            newRecord.setCloud(Integer.parseInt(weatherData.get("cloud").toString()));
            newRecord.setDew(new BigDecimal(weatherData.get("dew").toString()));
            newRecord.setWindShearLevel(weatherData.get("windShearLevel").toString());
            newRecord.setStabilityIndex(weatherData.get("stabilityIndex").toString());
            // 添加数据来源和质量字段
            newRecord.setDataSource("qweather");
            newRecord.setDataQuality(85);
            newRecord.setCreatedAt(LocalDateTime.now());
            
            weatherRealtimeMapper.insert(newRecord);
            
            Map<String, Object> result = new HashMap<>();
            result.put("updateTime", LocalDateTime.now().toString());
            result.put("data", newRecord);
            return result;
            
        } catch (Exception e) {
            // 5. API调用失败，返回数据库中的旧数据或模拟数据
            if (latest != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("updateTime", LocalDateTime.now().toString());
                result.put("data", latest);
                return result;
            }
            
            // 6. 连数据库都没有数据，返回模拟数据
            return generateMockWeatherData(pointId);
        }
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

    /**
     * 获取天气预测热力图数据（图表用）
     */
    public Map<String, Object> getWeatherHeatmapChart(String pointId, String timeRange, String resolution, Boolean forRouteAnalysis) {
        // 生成时间-高度矩阵数据（给ECharts图表用）
        Map<String, Object> heatmapData = generateChartHeatmapData(pointId, timeRange, resolution, forRouteAnalysis);
        
        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("pointId", pointId);
        result.put("timeRange", timeRange);
        result.put("resolution", resolution);
        result.put("forRouteAnalysis", forRouteAnalysis != null ? forRouteAnalysis : false);
        result.put("data", heatmapData);
        result.put("dataType", "chart_heatmap");
        
        return result;
    }

    /**
     * 获取地理空间热力图数据（地图用）
     * @param bounds 边界框坐标，格式：[minLng,minLat,maxLng,maxLat]
     * @param time 时间，ISO格式
     * @param resolution 分辨率：low/medium/high
     * @param pointId 监测点ID
     */
    public Map<String, Object> getWeatherHeatmapGeo(String bounds, String time, String resolution, String pointId) {
        // 生成地理空间风险分布数据（给Cesium地图用）
        Map<String, Object> heatmapData = generateGeoHeatmapData(bounds, time, resolution, pointId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("bounds", bounds);
        result.put("time", time != null ? time : LocalDateTime.now().toString());
        result.put("resolution", resolution);
        result.put("pointId", pointId);
        result.put("data", heatmapData);
        result.put("dataType", "geo_heatmap");
        
        return result;
    }

    /**
     * 获取天气预测热力图数据（兼容旧接口）
     */
    public Map<String, Object> getWeatherHeatmap(String pointId, String timeRange, String resolution, String bounds, Boolean forRouteAnalysis) {
        // 解析参数
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
        
        // 如果提供了bounds参数，生成区域范围的热力图
        if (bounds != null && !bounds.isEmpty()) {
            Map<String, Object> heatmapData = generateAreaHeatmapData(pointId, timeRange, resolution, bounds, forRouteAnalysis);
            
            Map<String, Object> result = new HashMap<>();
            result.put("updateTime", LocalDateTime.now().toString());
            result.put("pointId", pointId);
            result.put("timeRange", timeRange);
            result.put("resolution", resolution);
            result.put("bounds", bounds);
            result.put("forRouteAnalysis", forRouteAnalysis != null ? forRouteAnalysis : false);
            result.put("data", heatmapData);
            result.put("dataType", "area_heatmap");
            
            return result;
        } else {
            // 如果没有bounds，生成单点热力图
            Map<String, Object> heatmapData = generateMockHeatmapData(pointId, timeRange, resolution, bounds, forRouteAnalysis);
            
            Map<String, Object> result = new HashMap<>();
            result.put("updateTime", LocalDateTime.now().toString());
            result.put("pointId", pointId);
            result.put("timeRange", timeRange);
            result.put("resolution", resolution);
            result.put("forRouteAnalysis", forRouteAnalysis != null ? forRouteAnalysis : false);
            result.put("data", heatmapData);
            result.put("dataType", "point_heatmap");
            
            return result;
        }
    }

    /**
     * 批量获取天气热力图数据
     */
    public Map<String, Object> getBatchWeatherHeatmap(String areaIds, String timeRange, String resolution) {
        String[] ids = areaIds.split(",");
        
        Map<String, Object> batchData = new HashMap<>();
        for (String areaId : ids) {
            Map<String, Object> heatmapData = generateMockHeatmapData(
                areaId.trim(), 
                timeRange, 
                resolution, 
                null, 
                true // 批量获取通常用于航路分析
            );
            batchData.put(areaId.trim(), heatmapData);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("areaIds", ids);
        result.put("timeRange", timeRange);
        result.put("resolution", resolution);
        result.put("data", batchData);
        
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

    /**
     * 调用和风天气API
     */
    private Map<String, Object> callQWeatherAPI(double longitude, double latitude) {
        // 这里应该调用和风天气API
        // 实际项目中需要配置API Key和请求逻辑
        // 暂时返回模拟数据
        
        Map<String, Object> weatherData = new HashMap<>();
        weatherData.put("temp", "25");
        weatherData.put("feelsLike", "24");
        weatherData.put("icon", "100");
        weatherData.put("text", "晴");
        weatherData.put("wind360", "45");
        weatherData.put("windDir", "东北风");
        weatherData.put("windScale", "3");
        weatherData.put("windSpeed", "12");
        weatherData.put("humidity", "68");
        weatherData.put("precip", "0.0");
        weatherData.put("pressure", "1013");
        weatherData.put("vis", "10");
        weatherData.put("cloud", "25");
        weatherData.put("dew", "18");
        weatherData.put("windShearLevel", "low");
        weatherData.put("stabilityIndex", "C");
        
        return weatherData;
    }

    /**
     * 生成模拟天气数据
     */
    private Map<String, Object> generateMockWeatherData(String pointId) {
        WeatherRealtime mock = new WeatherRealtime();
        mock.setPointId(pointId);
        mock.setObsTime(LocalDateTime.now());
        mock.setTemp(BigDecimal.valueOf(25.0));
        mock.setFeelsLike(BigDecimal.valueOf(24.0));
        mock.setIcon("100");
        mock.setText("晴");
        mock.setWind360(45);
        mock.setWindDir("东北风");
        mock.setWindScale(String.valueOf(3));
        mock.setWindSpeed(BigDecimal.valueOf(12.0));
        mock.setHumidity(68);
        mock.setPrecip(BigDecimal.valueOf(0.0));
        mock.setPressure(BigDecimal.valueOf(1013.0));
        mock.setVis(BigDecimal.valueOf(10));
        mock.setCloud(25);
        mock.setDew(BigDecimal.valueOf(18.0));
        mock.setWindShearLevel("low");
        mock.setStabilityIndex("C");
        // 添加数据来源和质量字段
        mock.setDataSource("mock");
        mock.setDataQuality(70);
        mock.setCreatedAt(LocalDateTime.now());

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("data", mock);
        return result;
    }

    /**
     * 生成基于气象数据和阈值配置的风险热力图数据
     */
    private Map<String, Object> generateMockHeatmapData(String pointId, String timeRange, String resolution, String bounds, Boolean forRouteAnalysis) {
        try {
            // 1. 获取实时气象数据
            Map<String, Object> weatherResult = getRealtimeWeather(pointId);
            WeatherRealtime weatherData = extractWeatherData(weatherResult);
            
            // 2. 获取阈值配置
            AircraftLimit thresholds = getDefaultAircraftLimits();
            
            // 3. 计算基础风险值（基于气象数据和阈值）
            double baseRisk = calculateBaseRiskFromWeather(weatherData, thresholds);
            
            // 4. 根据时间范围确定时间点数量
            int timeCount = 7; // 默认7个时间点
            int timeInterval = 30; // 默认30分钟间隔
            
            if ("1h".equals(timeRange)) {
                timeCount = 7; // 1小时，10分钟间隔
                timeInterval = 10;
            } else if ("3h".equals(timeRange)) {
                timeCount = 7; // 3小时，30分钟间隔
                timeInterval = 30;
            } else if ("6h".equals(timeRange)) {
                timeCount = 13; // 6小时，30分钟间隔
                timeInterval = 30;
            } else if ("12h".equals(timeRange)) {
                timeCount = 25; // 12小时，30分钟间隔
                timeInterval = 30;
            }
            
            // 5. 根据分辨率确定高度层数量
            int heightCount = 8; // 默认8个高度层
            int heightInterval = 50; // 默认50米间隔
            
            if ("low".equals(resolution)) {
                heightCount = 5;
                heightInterval = 100;
            } else if ("medium".equals(resolution)) {
                heightCount = 8;
                heightInterval = 50;
            } else if ("high".equals(resolution)) {
                heightCount = 16;
                heightInterval = 25;
            }
            
            // 6. 生成时间标签
            List<String> times = new ArrayList<>();
            LocalDateTime baseTime = LocalDateTime.now();
            for (int i = 0; i < timeCount; i++) {
                LocalDateTime time = baseTime.plusMinutes(i * timeInterval);
                times.add(String.format("%02d:%02d", time.getHour(), time.getMinute()));
            }
            
            // 7. 生成高度标签
            List<Integer> heights = new ArrayList<>();
            for (int i = 0; i < heightCount; i++) {
                heights.add(i * heightInterval);
            }
            
            // 8. 生成风险热力图数据（基于气象数据的时空变化）
            List<List<Integer>> data = new ArrayList<>();
            
            for (int h = 0; h < heightCount; h++) {
                List<Integer> row = new ArrayList<>();
                // 高度因子：高度越高，风险通常越低
                double heightFactor = calculateHeightFactor(h, heightCount, weatherData);
                
                for (int t = 0; t < timeCount; t++) {
                    // 时间因子：随时间变化的风险
                    double timeFactor = calculateTimeFactor(t, timeCount, baseTime);
                    
                    // 气象变化因子：基于当前气象数据的随机波动
                    double weatherFactor = calculateWeatherFactor(weatherData, h, t);
                    
                    // 计算综合风险值（0-100）
                    int riskValue = calculateRiskValue(baseRisk, heightFactor, timeFactor, weatherFactor, 
                                                      forRouteAnalysis != null && forRouteAnalysis);
                    
                    row.add(riskValue);
                }
                data.add(row);
            }
            
            // 9. 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("times", times);
            result.put("heights", heights);
            result.put("data", data);
            result.put("metadata", Map.of(
                "pointId", pointId,
                "timeRange", timeRange,
                "resolution", resolution,
                "forRouteAnalysis", forRouteAnalysis != null ? forRouteAnalysis : false,
                "dataType", "flight_risk_heatmap",
                "unit", "风险指数(0-100)",
                "calculationMethod", "weather_based_risk_assessment",
                "weatherData", Map.of(
                    "windSpeed", weatherData.getWindSpeed(),
                    "visibility", weatherData.getVis(),
                    "precipitation", weatherData.getPrecip(),
                    "humidity", weatherData.getHumidity(),
                    "windShearLevel", weatherData.getWindShearLevel(),
                    "stabilityIndex", weatherData.getStabilityIndex()
                ),
                "thresholds", Map.of(
                    "maxWindSpeed", thresholds.getMaxWindSpeed(),
                    "minVisibility", thresholds.getMinVisibility(),
                    "maxPrecipitation", thresholds.getMaxPrecipitation(),
                    "maxHumidity", thresholds.getMaxHumidity()
                ),
                "generatedAt", LocalDateTime.now().toString()
            ));
            
            return result;
            
        } catch (Exception e) {
            // 如果出现异常，返回基本的模拟数据
            return generateBasicHeatmapData(pointId, timeRange, resolution, forRouteAnalysis);
        }
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
        defaultData.setWindSpeed(BigDecimal.valueOf(8.0)); // km/h
        defaultData.setVis(BigDecimal.valueOf(10.0)); // km
        defaultData.setPrecip(BigDecimal.valueOf(0.0)); // mm
        defaultData.setHumidity(65); // %
        defaultData.setWindShearLevel("low");
        defaultData.setStabilityIndex("B");
        return defaultData;
    }
    
    /**
     * 获取默认飞行器阈值配置
     */
    private AircraftLimit getDefaultAircraftLimits() {
        AircraftLimit limit = aircraftLimitMapper.selectOne(
            new LambdaQueryWrapper<AircraftLimit>()
                .eq(AircraftLimit::getAircraftId, "aircraft-1")
                .last("LIMIT 1")
        );
        
        if (limit == null) {
            limit = new AircraftLimit();
            limit.setMaxWindSpeed(BigDecimal.valueOf(12.0));
            limit.setMinVisibility(BigDecimal.valueOf(1.5));
            limit.setMaxPrecipitation(BigDecimal.valueOf(5.0));
            limit.setMaxHumidity(90);
        }
        
        return limit;
    }
    
    /**
     * 基于气象数据和阈值计算基础风险值
     */
    private double calculateBaseRiskFromWeather(WeatherRealtime weather, AircraftLimit thresholds) {
        double totalRisk = 0.0;
        int factorCount = 0;
        
        // 1. 风速风险
        BigDecimal windSpeedKmh = weather.getWindSpeed();
        if (windSpeedKmh != null && thresholds.getMaxWindSpeed() != null) {
            BigDecimal windSpeedMs = windSpeedKmh.multiply(BigDecimal.valueOf(1000.0 / 3600.0));
            double windRisk = windSpeedMs.compareTo(thresholds.getMaxWindSpeed()) > 0 ? 0.8 : 0.3;
            totalRisk += windRisk;
            factorCount++;
        }
        
        // 2. 能见度风险
        BigDecimal visibility = weather.getVis();
        if (visibility != null && thresholds.getMinVisibility() != null) {
            double visibilityRisk = visibility.compareTo(thresholds.getMinVisibility()) < 0 ? 0.7 : 0.2;
            totalRisk += visibilityRisk;
            factorCount++;
        }
        
        // 3. 降水量风险
        BigDecimal precipitation = weather.getPrecip();
        if (precipitation != null && thresholds.getMaxPrecipitation() != null) {
            double precipitationRisk = precipitation.compareTo(thresholds.getMaxPrecipitation()) > 0 ? 0.6 : 0.1;
            totalRisk += precipitationRisk;
            factorCount++;
        }
        
        // 4. 湿度风险
        Integer humidity = weather.getHumidity();
        if (humidity != null && thresholds.getMaxHumidity() != null) {
            double humidityRisk = humidity > thresholds.getMaxHumidity() ? 0.5 : 0.1;
            totalRisk += humidityRisk;
            factorCount++;
        }
        
        // 5. 风切变风险
        String windShearLevel = weather.getWindShearLevel();
        double windShearRisk = "high".equals(windShearLevel) ? 0.9 : 
                              "medium".equals(windShearLevel) ? 0.5 : 0.1;
        totalRisk += windShearRisk;
        factorCount++;
        
        // 6. 稳定度/湍流风险
        String stabilityIndex = weather.getStabilityIndex();
        double turbulenceRisk = "C".equals(stabilityIndex) || "D".equals(stabilityIndex) ? 0.7 : 0.2;
        totalRisk += turbulenceRisk;
        factorCount++;
        
        // 计算平均风险
        return factorCount > 0 ? totalRisk / factorCount : 0.5;
    }
    
    /**
     * 计算高度因子
     */
    private double calculateHeightFactor(int heightIndex, int totalHeights, WeatherRealtime weather) {
        // 基础：高度越高风险越低
        double baseFactor = 1.0 - (heightIndex / (double) totalHeights) * 0.4;
        
        // 根据稳定度调整：不稳定天气下，高度影响更大
        String stability = weather.getStabilityIndex();
        if ("C".equals(stability) || "D".equals(stability)) {
            baseFactor *= 0.8; // 不稳定天气，高空风险增加
        }
        
        return Math.max(0.3, Math.min(1.2, baseFactor));
    }
    
    /**
     * 计算时间因子
     */
    private double calculateTimeFactor(int timeIndex, int totalTimes, LocalDateTime baseTime) {
        // 基础时间波动
        double timeWave = 0.8 + Math.sin(timeIndex * 0.5) * 0.2;
        
        // 考虑一天中的时间（白天/夜晚）
        int hour = baseTime.plusMinutes(timeIndex * 30).getHour();
        double dayNightFactor = (hour >= 6 && hour <= 18) ? 1.0 : 1.1; // 夜晚风险稍高
        
        return timeWave * dayNightFactor;
    }
    
    /**
     * 计算气象因子
     */
    private double calculateWeatherFactor(WeatherRealtime weather, int heightIndex, int timeIndex) {
        double factor = 0.9 + Math.random() * 0.2; // 基础随机波动
        
        // 根据风切变等级调整
        String windShear = weather.getWindShearLevel();
        if ("high".equals(windShear)) {
            factor *= 1.3; // 高风切变增加波动
        }
        
        // 根据稳定度调整
        String stability = weather.getStabilityIndex();
        if ("C".equals(stability) || "D".equals(stability)) {
            factor *= 1.2; // 不稳定天气增加波动
        }
        
        return Math.max(0.7, Math.min(1.5, factor));
    }
    
    /**
     * 计算综合风险值
     */
    private int calculateRiskValue(double baseRisk, double heightFactor, double timeFactor, 
                                  double weatherFactor, boolean forRouteAnalysis) {
        // 基础计算
        double risk = baseRisk * heightFactor * timeFactor * weatherFactor;
        
        // 航路分析模式调整
        if (forRouteAnalysis) {
            risk *= 0.8; // 航路分析通常风险较低
            // 添加空间模式
            double patternFactor = Math.sin(heightFactor * 0.8 + timeFactor * 0.4) * 0.3 + 0.7;
            risk *= patternFactor;
        }
        
        // 转换为0-100的整数
        int riskValue = (int) Math.round(risk * 100);
        
        // 确保在合理范围内
        return Math.max(10, Math.min(95, riskValue));
    }
    
    /**
     * 生成区域范围的热力图数据
     */
    private Map<String, Object> generateAreaHeatmapData(String pointId, String timeRange, String resolution, String bounds, Boolean forRouteAnalysis) {
        try {
            // 解析边界框
            double[] bbox = parseBoundingBox(bounds);
            if (bbox == null) {
                return generateMockHeatmapData(pointId, timeRange, resolution, bounds, forRouteAnalysis);
            }
            
            double minLng = bbox[0];
            double minLat = bbox[1];
            double maxLng = bbox[2];
            double maxLat = bbox[3];
            
            // 获取区域内的气象数据
            Map<String, Object> weatherData = getAreaWeatherData(bbox);
            
            // 获取阈值配置
            AircraftLimit thresholds = getDefaultAircraftLimits();
            
            // 根据分辨率确定网格大小
            int gridSize = getGridSizeFromResolution(resolution);
            
            // 根据时间范围确定时间点数量
            int timeCount = getTimeCountFromRange(timeRange);
            
            // 生成时间标签
            List<String> times = generateTimeLabels(timeRange, timeCount);
            
            // 生成网格坐标和风险数据
            List<Map<String, Object>> gridData = new ArrayList<>();
            
            for (int i = 0; i < gridSize; i++) {
                for (int j = 0; j < gridSize; j++) {
                    // 计算网格点坐标
                    double lng = minLng + (maxLng - minLng) * (i / (double) (gridSize - 1));
                    double lat = minLat + (maxLat - minLat) * (j / (double) (gridSize - 1));
                    
                    // 计算该位置的风险值（基于气象数据和空间变化）
                    Map<String, Object> riskData = calculateGridRiskData(lng, lat, weatherData, thresholds, timeCount);
                    
                    Map<String, Object> gridPoint = new HashMap<>();
                    gridPoint.put("lng", lng);
                    gridPoint.put("lat", lat);
                    gridPoint.put("x", i);
                    gridPoint.put("y", j);
                    gridPoint.put("riskData", riskData);
                    
                    gridData.add(gridPoint);
                }
            }
            
            // 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("bounds", bounds);
            result.put("gridSize", gridSize);
            result.put("times", times);
            result.put("gridData", gridData);
            result.put("metadata", Map.of(
                "pointId", pointId,
                "timeRange", timeRange,
                "resolution", resolution,
                "forRouteAnalysis", forRouteAnalysis != null ? forRouteAnalysis : false,
                "dataType", "area_risk_heatmap",
                "unit", "风险指数(0-100)",
                "calculationMethod", "area_weather_based_risk_assessment",
                "gridType", "regular_grid",
                "gridCount", gridSize * gridSize,
                "generatedAt", LocalDateTime.now().toString()
            ));
            
            return result;
            
        } catch (Exception e) {
            // 如果出现异常，返回基本的区域热力图数据
            return generateBasicAreaHeatmapData(pointId, timeRange, resolution, bounds, forRouteAnalysis);
        }
    }
    
    /**
     * 解析边界框字符串
     */
    private double[] parseBoundingBox(String bounds) {
        try {
            // 移除方括号和空格
            String cleanBounds = bounds.replaceAll("[\\[\\]\\s]", "");
            String[] parts = cleanBounds.split(",");
            
            if (parts.length >= 4) {
                double minLng = Double.parseDouble(parts[0]);
                double minLat = Double.parseDouble(parts[1]);
                double maxLng = Double.parseDouble(parts[2]);
                double maxLat = Double.parseDouble(parts[3]);
                
                return new double[]{minLng, minLat, maxLng, maxLat};
            }
        } catch (Exception e) {
            // 解析失败
        }
        return null;
    }
    
    /**
     * 获取区域内的气象数据（模拟）
     */
    private Map<String, Object> getAreaWeatherData(double[] bbox) {
        // 这里应该查询数据库获取区域内的气象数据
        // 目前使用模拟数据
        
        Map<String, Object> weatherData = new HashMap<>();
        
        // 基础气象数据
        Map<String, Object> baseWeather = new HashMap<>();
        baseWeather.put("windSpeed", 8.0 + Math.random() * 4.0); // 8-12 km/h
        baseWeather.put("visibility", 8.0 + Math.random() * 4.0); // 8-12 km
        baseWeather.put("precipitation", Math.random() * 2.0); // 0-2 mm
        baseWeather.put("humidity", 60 + (int)(Math.random() * 20)); // 60-80%
        baseWeather.put("windShearLevel", "low");
        baseWeather.put("stabilityIndex", "B");
        
        weatherData.put("base", baseWeather);
        weatherData.put("bbox", bbox);
        weatherData.put("dataSource", "simulated");
        
        return weatherData;
    }
    
    /**
     * 根据分辨率获取网格大小
     */
    private int getGridSizeFromResolution(String resolution) {
        switch (resolution) {
            case "low": return 8;
            case "medium": return 12;
            case "high": return 16;
            default: return 12;
        }
    }
    
    /**
     * 根据时间范围获取时间点数量
     */
    private int getTimeCountFromRange(String timeRange) {
        switch (timeRange) {
            case "1h": return 7;
            case "3h": return 7;
            case "6h": return 13;
            case "12h": return 25;
            default: return 7;
        }
    }
    
    /**
     * 生成时间标签
     */
    private List<String> generateTimeLabels(String timeRange, int timeCount) {
        List<String> times = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now();
        
        int interval = 30; // 默认30分钟间隔
        if ("1h".equals(timeRange)) interval = 10;
        
        for (int i = 0; i < timeCount; i++) {
            LocalDateTime time = baseTime.plusMinutes(i * interval);
            times.add(String.format("%02d:%02d", time.getHour(), time.getMinute()));
        }
        
        return times;
    }
    
    /**
     * 计算网格点的风险数据
     */
    private Map<String, Object> calculateGridRiskData(double lng, double lat, Map<String, Object> weatherData, 
                                                     AircraftLimit thresholds, int timeCount) {
        Map<String, Object> baseWeather = (Map<String, Object>) weatherData.get("base");
        
        // 计算基础风险
        double baseRisk = calculateBaseRiskFromWeatherData(baseWeather, thresholds);
        
        // 添加空间变化（基于经纬度）
        double spatialFactor = calculateSpatialFactor(lng, lat);
        
        // 生成时间序列风险值
        List<Integer> timeSeries = new ArrayList<>();
        for (int t = 0; t < timeCount; t++) {
            double timeFactor = 0.8 + Math.sin(t * 0.5) * 0.2;
            double weatherFactor = 0.9 + Math.random() * 0.2;
            
            double risk = baseRisk * spatialFactor * timeFactor * weatherFactor;
            int riskValue = (int) Math.round(risk * 100);
            riskValue = Math.max(20, Math.min(95, riskValue));
            
            timeSeries.add(riskValue);
        }
        
        Map<String, Object> riskData = new HashMap<>();
        riskData.put("baseRisk", baseRisk);
        riskData.put("spatialFactor", spatialFactor);
        riskData.put("timeSeries", timeSeries);
        riskData.put("currentRisk", timeSeries.get(0)); // 当前时间的风险值
        
        return riskData;
    }
    
    /**
     * 从气象数据计算基础风险
     */
    private double calculateBaseRiskFromWeatherData(Map<String, Object> weatherData, AircraftLimit thresholds) {
        double totalRisk = 0.0;
        int factorCount = 0;
        
        // 风速风险
        double windSpeed = (double) weatherData.get("windSpeed");
        if (thresholds.getMaxWindSpeed() != null) {
            double windSpeedMs = windSpeed * (1000.0 / 3600.0);
            double windRisk = windSpeedMs > thresholds.getMaxWindSpeed().doubleValue() ? 0.8 : 0.3;
            totalRisk += windRisk;
            factorCount++;
        }
        
        // 能见度风险
        double visibility = (double) weatherData.get("visibility");
        if (thresholds.getMinVisibility() != null) {
            double visibilityRisk = visibility < thresholds.getMinVisibility().doubleValue() ? 0.7 : 0.2;
            totalRisk += visibilityRisk;
            factorCount++;
        }
        
        // 其他因素...
        totalRisk += 0.3; // 其他因素的平均风险
        factorCount++;
        
        return factorCount > 0 ? totalRisk / factorCount : 0.5;
    }
    
    /**
     * 计算空间因子（基于经纬度）
     */
    private double calculateSpatialFactor(double lng, double lat) {
        // 模拟空间变化：沿海地区风险较高，内陆风险较低
        // 这里使用简单的正弦波模拟空间变化
        double factor = 0.9 + Math.sin(lng * 10) * 0.1 + Math.cos(lat * 10) * 0.1;
        return Math.max(0.7, Math.min(1.3, factor));
    }
    
    /**
     * 生成基本的区域热力图数据（降级方案）
     */
    private Map<String, Object> generateBasicAreaHeatmapData(String pointId, String timeRange, String resolution, String bounds, Boolean forRouteAnalysis) {
        // 简化的区域热力图数据
        int gridSize = 8;
        int timeCount = 7;
        
        List<String> times = generateTimeLabels(timeRange, timeCount);
        List<Map<String, Object>> gridData = new ArrayList<>();
        
        double[] bbox = parseBoundingBox(bounds);
        if (bbox == null) {
            // 如果bounds解析失败，尝试根据pointId从数据库查询
            if (pointId != null && !pointId.isEmpty()) {
                try {
                    MonitoringPoint point = monitoringPointService.getById(pointId);
                    bbox = new double[]{
                        point.getBboxMinLng().doubleValue(),
                        point.getBboxMinLat().doubleValue(),
                        point.getBboxMaxLng().doubleValue(),
                        point.getBboxMaxLat().doubleValue()
                    };
                } catch (Exception e) {
                    // 如果查询失败，使用默认边界
                    bbox = new double[]{120.0, 36.0, 121.0, 37.0};
                }
            } else {
                bbox = new double[]{120.0, 36.0, 121.0, 37.0}; // 默认边界
            }
        }
        
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                double lng = bbox[0] + (bbox[2] - bbox[0]) * (i / (double) (gridSize - 1));
                double lat = bbox[1] + (bbox[3] - bbox[1]) * (j / (double) (gridSize - 1));
                
                List<Integer> timeSeries = new ArrayList<>();
                for (int t = 0; t < timeCount; t++) {
                    int riskValue = 50 + (int)(Math.sin(i * 0.5 + j * 0.3 + t * 0.2) * 20);
                    timeSeries.add(Math.max(20, Math.min(80, riskValue)));
                }
                
                Map<String, Object> gridPoint = new HashMap<>();
                gridPoint.put("lng", lng);
                gridPoint.put("lat", lat);
                gridPoint.put("x", i);
                gridPoint.put("y", j);
                gridPoint.put("riskData", Map.of(
                    "timeSeries", timeSeries,
                    "currentRisk", timeSeries.get(0)
                ));
                
                gridData.add(gridPoint);
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("bounds", Arrays.toString(bbox));
        result.put("gridSize", gridSize);
        result.put("times", times);
        result.put("gridData", gridData);
        result.put("metadata", Map.of(
            "pointId", pointId,
            "timeRange", timeRange,
            "resolution", resolution,
            "forRouteAnalysis", forRouteAnalysis != null ? forRouteAnalysis : false,
            "dataType", "basic_area_heatmap",
            "unit", "风险指数(0-100)",
            "calculationMethod", "basic_simulation",
            "generatedAt", LocalDateTime.now().toString()
        ));
        
        return result;
    }

    /**
     * 生成图表热力图数据（时间-高度矩阵）
     */
    private Map<String, Object> generateChartHeatmapData(String pointId, String timeRange, String resolution, Boolean forRouteAnalysis) {
        // 这就是原来的 generateMockHeatmapData 逻辑，用于ECharts图表
        return generateMockHeatmapData(pointId, timeRange, resolution, null, forRouteAnalysis);
    }

    /**
     * 生成地理空间热力图数据（经纬度风险点）
     */
    private Map<String, Object> generateGeoHeatmapData(String bounds, String time, String resolution, String pointId) {
        try {
            // 解析边界框
            double[] bbox = parseBoundingBox(bounds);
            if (bbox == null) {
                return generateBasicGeoHeatmapData(bounds, time, resolution, pointId);
            }
            
            double minLng = bbox[0];
            double minLat = bbox[1];
            double maxLng = bbox[2];
            double maxLat = bbox[3];
            
            // 根据分辨率确定网格大小
            int gridSize = getGridSizeFromResolution(resolution);
            
            // 获取区域气象数据
            Map<String, Object> weatherData = getAreaWeatherData(bbox);
            
            // 获取阈值配置
            AircraftLimit thresholds = getDefaultAircraftLimits();
            
            // 生成网格点数据
            List<Map<String, Object>> points = new ArrayList<>();
            
            for (int i = 0; i < gridSize; i++) {
                for (int j = 0; j < gridSize; j++) {
                    // 计算网格点坐标
                    double lng = minLng + (maxLng - minLng) * (i / (double) (gridSize - 1));
                    double lat = minLat + (maxLat - minLat) * (j / (double) (gridSize - 1));
                    
                    // 计算该位置的风险值
                    Map<String, Object> baseWeather = (Map<String, Object>) weatherData.get("base");
                    double baseRisk = calculateBaseRiskFromWeatherData(baseWeather, thresholds);
                    
                    // 添加空间变化
                    double spatialFactor = calculateSpatialFactor(lng, lat);
                    
                    // 计算最终风险值 (0-100)
                    int riskValue = (int) Math.round(baseRisk * spatialFactor * 100);
                    riskValue = Math.max(20, Math.min(95, riskValue));
                    
                    // 构建点数据
                    Map<String, Object> point = new HashMap<>();
                    point.put("lon", lng);
                    point.put("lat", lat);
                    point.put("value", riskValue);
                    point.put("x", i);
                    point.put("y", j);
                    point.put("riskLevel", getRiskLevel(riskValue));
                    
                    points.add(point);
                }
            }
            
            // 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("points", points);
            result.put("bounds", bounds);
            result.put("gridSize", gridSize);
            result.put("pointCount", points.size());
            result.put("metadata", Map.of(
                "dataType", "geo_heatmap",
                "unit", "风险指数(0-100)",
                "calculationMethod", "geo_weather_based_risk",
                "generatedAt", LocalDateTime.now().toString(),
                "resolution", resolution
            ));
            
            return result;
            
        } catch (Exception e) {
            // 降级方案
            return generateBasicGeoHeatmapData(bounds, time, resolution, pointId);
        }
    }

    /**
     * 获取风险等级
     */
    private String getRiskLevel(int riskValue) {
        if (riskValue >= 80) return "high";
        if (riskValue >= 60) return "medium";
        if (riskValue >= 40) return "low";
        return "very_low";
    }

    /**
     * 生成基本的地理空间热力图数据（降级方案）
     */
    private Map<String, Object> generateBasicGeoHeatmapData(String bounds, String time, String resolution, String pointId) {
        // 简化的地理空间热力图数据
        double[] bbox = parseBoundingBox(bounds);
        if (bbox == null) {
            // 如果bounds解析失败，尝试根据pointId从数据库查询
            if (pointId != null && !pointId.isEmpty()) {
                try {
                    MonitoringPoint point = monitoringPointService.getById(pointId);
                    bbox = new double[]{
                        point.getBboxMinLng().doubleValue(),
                        point.getBboxMinLat().doubleValue(),
                        point.getBboxMaxLng().doubleValue(),
                        point.getBboxMaxLat().doubleValue()
                    };
                } catch (Exception e) {
                    // 如果查询失败，使用默认边界
                    bbox = new double[]{120.0, 36.0, 121.0, 37.0};
                }
            } else {
                bbox = new double[]{120.0, 36.0, 121.0, 37.0};
            }
        }
        
        int gridSize = 8;
        List<Map<String, Object>> points = new ArrayList<>();
        
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                double lng = bbox[0] + (bbox[2] - bbox[0]) * (i / (double) (gridSize - 1));
                double lat = bbox[1] + (bbox[3] - bbox[1]) * (j / (double) (gridSize - 1));
                
                int riskValue = 50 + (int)(Math.sin(i * 0.5 + j * 0.3) * 20);
                riskValue = Math.max(20, Math.min(80, riskValue));
                
                Map<String, Object> point = new HashMap<>();
                point.put("lon", lng);
                point.put("lat", lat);
                point.put("value", riskValue);
                point.put("x", i);
                point.put("y", j);
                point.put("riskLevel", getRiskLevel(riskValue));
                
                points.add(point);
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("points", points);
        result.put("bounds", Arrays.toString(bbox));
        result.put("gridSize", gridSize);
        result.put("pointCount", points.size());
        result.put("metadata", Map.of(
            "dataType", "basic_geo_heatmap",
            "unit", "风险指数(0-100)",
            "calculationMethod", "basic_simulation",
            "generatedAt", LocalDateTime.now().toString()
        ));
        
        return result;
    }

    /**
     * 生成基本的热力图数据（降级方案）
     */
    private Map<String, Object> generateBasicHeatmapData(String pointId, String timeRange, String resolution, Boolean forRouteAnalysis) {
        // 简化的热力图数据生成逻辑
        int timeCount = 7;
        int heightCount = 8;
        
        List<String> times = new ArrayList<>();
        for (int i = 0; i < timeCount; i++) {
            times.add(String.format("%02d:00", (i * 30) / 60));
        }
        
        List<Integer> heights = new ArrayList<>();
        for (int i = 0; i < heightCount; i++) {
            heights.add(i * 50);
        }
        
        List<List<Integer>> data = new ArrayList<>();
        for (int h = 0; h < heightCount; h++) {
            List<Integer> row = new ArrayList<>();
            for (int t = 0; t < timeCount; t++) {
                int riskValue = 50 + (int)(Math.sin(h * 0.5 + t * 0.3) * 20);
                row.add(Math.max(20, Math.min(80, riskValue)));
            }
            data.add(row);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("times", times);
        result.put("heights", heights);
        result.put("data", data);
        result.put("metadata", Map.of(
            "pointId", pointId,
            "timeRange", timeRange,
            "resolution", resolution,
            "forRouteAnalysis", forRouteAnalysis != null ? forRouteAnalysis : false,
            "dataType", "basic_flight_risk_heatmap",
            "unit", "风险指数(0-100)",
            "calculationMethod", "basic_simulation",
            "generatedAt", LocalDateTime.now().toString()
        ));
        
        return result;
    }
}
