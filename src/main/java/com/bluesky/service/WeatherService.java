package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.config.RegionConfig;
import com.bluesky.entity.*;
import com.bluesky.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * 气象数据服务
 * 负责实时天气、风向趋势、微尺度天气等数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final WeatherRealtimeMapper weatherRealtimeMapper;
    private final MicroscaleWeatherMapper microscaleWeatherMapper;
    private final WeatherForecastMapper weatherForecastMapper;
    private final AircraftLimitMapper aircraftLimitMapper;
    private final MonitoringPointService monitoringPointService;
    private final RegionConfig regionConfig;

    // ==================== 实时气象 ====================

    /**
     * 获取重点关注区域实时气象数据
     * 先查数据库，如果没有则调用和风天气API
     */
    public Map<String, Object> getRealtimeWeather(String pointId) {
        // 1. 先查数据库 指定id的最新的实时数据1条
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
            // 5. API调用失败，返回数据库中的旧数据
            if (latest != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("updateTime", LocalDateTime.now().toString());
                result.put("data", latest);
                return result;
            }

        }
        return null;
    }

    /**
     * 获取微尺度天气数据(热力图)
     */
    public Map<String, Object> getMicroscaleWeather(String region, String timeRange) {
        LambdaQueryWrapper<MicroscaleWeather> wrapper = new LambdaQueryWrapper<MicroscaleWeather>()
                .orderByDesc(MicroscaleWeather::getDataTime)
                .last("LIMIT 1000");

        if (region != null && !region.isEmpty()) {
            wrapper.eq(MicroscaleWeather::getPointId, region);
        }

        List<MicroscaleWeather> list = microscaleWeatherMapper.selectList(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("region", region);
        result.put("data", list);
        return result;
    }

    /**
     * 通用热力图生成方法
     *
     * @param bounds   边界框坐标，格式：[minLng,minLat,maxLng,maxLat]
     * @param pointIds 监测点ID列表，null表示区域级
     * @return 热力图数据
     */
    private Map<String, Object> generateCommonHeatmapData(String bounds, List<String> pointIds) {
        // 解析边界框
        double[] bbox = parseBoundingBox(bounds);

        // 获取阈值配置
        // 生成热力图数据
        List<Map<String, Object>> points = new ArrayList<>();
        // 获取数据库中的真实气象数据点
        if (pointIds != null && !pointIds.isEmpty()) {
            // 查询指定监测点的数据
            for (String pointId : pointIds) {
                // 获取监测点信息，使用该监测点的边界框
                MonitoringPoint p = monitoringPointService.getById(pointId);
                if (p == null || p.getBboxMinLng() == null || p.getBboxMaxLng() == null) {
                    continue;
                }

                double pointMinLng = p.getBboxMinLng().doubleValue();
                double pointMinLat = p.getBboxMinLat().doubleValue();
                double pointMaxLng = p.getBboxMaxLng().doubleValue();
                double pointMaxLat = p.getBboxMaxLat().doubleValue();

                List<MicroscaleWeather> weatherList = getWeatherPointsFromDatabase(pointId, bbox);
                for (MicroscaleWeather weather : weatherList) {
                    // 计算经纬度坐标（基于网格坐标和该监测点的边界框）
                    int gridSize = weather.getGridSize() != null ? weather.getGridSize() : 10;
                    double gridX = weather.getGridX() != null ? weather.getGridX().doubleValue() : 0;
                    double gridY = weather.getGridY() != null ? weather.getGridY().doubleValue() : 0;

                    double lng = pointMinLng + (pointMaxLng - pointMinLng) * (gridX / (double) (gridSize - 1));
                    double lat = pointMinLat + (pointMaxLat - pointMinLat) * (gridY / (double) (gridSize - 1));

                    // 构建点数据
                    Map<String, Object> point = new HashMap<>();
                    point.put("lon", lng);
                    point.put("lat", lat);
                    point.put("value", weather.getRiskLevel() * 25); // 转换为 0-100 范围
                    point.put("x", gridX);
                    point.put("y", gridY);
                    point.put("riskLevel", getRiskLevel(weather.getRiskLevel() * 25));
                    point.put("windSpeed", weather.getWindSpeed());
                    point.put("windShear", weather.getWindShear());
                    point.put("turbulence", weather.getTurbulence());

                    points.add(point);
                }

            }

        } else {
            // 区域级查询，查询所有监测点的数据
            List<MonitoringPoint> allPoints = monitoringPointService.getAll();
            for (MonitoringPoint p : allPoints) {
                if (p.getBboxMinLng() == null || p.getBboxMaxLng() == null) {
                    continue;
                }

                double pointMinLng = p.getBboxMinLng().doubleValue();
                double pointMinLat = p.getBboxMinLat().doubleValue();
                double pointMaxLng = p.getBboxMaxLng().doubleValue();
                double pointMaxLat = p.getBboxMaxLat().doubleValue();

                List<MicroscaleWeather> weatherList = getWeatherPointsFromDatabase(p.getId(), bbox);
                for (MicroscaleWeather weather : weatherList) {
                    // 计算经纬度坐标（基于网格坐标和该监测点的边界框）
                    int gridSize = weather.getGridSize() != null ? weather.getGridSize() : 10;
                    double gridX = weather.getGridX() != null ? weather.getGridX().doubleValue() : 0;
                    double gridY = weather.getGridY() != null ? weather.getGridY().doubleValue() : 0;

                    double lng = pointMinLng + (pointMaxLng - pointMinLng) * (gridX / (double) (gridSize - 1));
                    double lat = pointMinLat + (pointMaxLat - pointMinLat) * (gridY / (double) (gridSize - 1));

                    // 构建点数据
                    Map<String, Object> point = new HashMap<>();
                    point.put("lon", lng);
                    point.put("lat", lat);
                    point.put("value", weather.getRiskLevel() * 25); // 转换为 0-100 范围
                    point.put("x", gridX);
                    point.put("y", gridY);
                    point.put("riskLevel", getRiskLevel(weather.getRiskLevel() * 25));
                    point.put("windSpeed", weather.getWindSpeed());
                    point.put("windShear", weather.getWindShear());
                    point.put("turbulence", weather.getTurbulence());

                    points.add(point);
                }

            }
        }

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("points", points);
        result.put("bounds", bounds);
        result.put("pointCount", points.size());
        result.put("metadata", Map.of(
                "dataType", "geo_heatmap",
                "unit", "风险指数(0-100)",
                "calculationMethod", "geo_weather_based_risk",
                "generatedAt", LocalDateTime.now().toString()));

        return result;
    }

    /**
     * 获取地理空间热力图数据（地图用）
     *
     * @param bounds     边界框坐标，格式：[minLng,minLat,maxLng,maxLat]
     * @param time       时间，ISO格式
     * @param resolution 分辨率：low/medium/high
     * @param pointId    监测点ID
     */
    public Map<String, Object> getWeatherHeatmapGeo(String bounds, String time, String pointId) {
        // 生成地理空间风险分布数据（给Cesium地图用）
        List<String> pointIds = Collections.singletonList(pointId);
        Map<String, Object> heatmapData = generateCommonHeatmapData(bounds, pointIds);

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("bounds", bounds);
        result.put("time", time != null ? time : LocalDateTime.now().toString());
        result.put("pointId", pointId);
        result.put("data", heatmapData);
        result.put("dataType", "geo_heatmap");

        return result;
    }

    // ==================== 工具方法 ====================

    /**
     * 调用和风天气API
     */
    private Map<String, Object> callQWeatherAPI(double longitude, double latitude) {
        RestTemplate restTemplate = new RestTemplate();
        try {
            // 构建和风天气 API 请求
            String url = String.format(
                    "https://m73yfr9h37.re.qweatherapi.com/v7/weather/now?location=%f,%f",
                    longitude, latitude);

            // 创建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-QW-Api-Key", "7226910f80e3434aa26b1b55938b6f58");
            headers.set("Accept-Encoding", "gzip");

            // 创建请求实体
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 调用 API 获取原始响应
            ResponseEntity<byte[]> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);

            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                byte[] responseBodyBytes = responseEntity.getBody();
                if (responseBodyBytes != null) {
                    // 检查是否是Gzip压缩数据
                    boolean isGzipped = false;
                    if (responseBodyBytes.length >= 2) {
                        int magic = ((responseBodyBytes[0] & 0xff) << 8) | (responseBodyBytes[1] & 0xff);
                        isGzipped = (magic == GZIPInputStream.GZIP_MAGIC);
                    }

                    // 解压数据
                    String responseBody;
                   // if (isGzipped) {
                        try (ByteArrayInputStream bais = new ByteArrayInputStream(responseBodyBytes);
                                GZIPInputStream gis = new GZIPInputStream(bais);
                                InputStreamReader isr = new InputStreamReader(gis, StandardCharsets.UTF_8);
                                BufferedReader br = new BufferedReader(isr)) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) {
                                sb.append(line);
                            }
                            responseBody = sb.toString();
                        }
                  //  } else {
                  //      responseBody = new String(responseBodyBytes, StandardCharsets.UTF_8);
                   // }

                    // 解析 JSON 响应
                    ObjectMapper objectMapper = new ObjectMapper();
                    ObjectNode jsonResponse = objectMapper.readValue(responseBody, ObjectNode.class);

                    // 检查响应状态
                    String code = jsonResponse.get("code").asText();
                    if ("200".equals(code)) {
                        ObjectNode now = (ObjectNode) jsonResponse.get("now");
                        Map<String, Object> weatherData = new HashMap<>();

                        // 提取数据
                        weatherData.put("temp", now.get("temp").asText());
                        weatherData.put("feelsLike", now.get("feelsLike").asText());
                        weatherData.put("icon", now.get("icon").asText());
                        weatherData.put("text", now.get("text").asText());
                        weatherData.put("wind360", now.get("wind360").asText());
                        weatherData.put("windDir", now.get("windDir").asText());
                        weatherData.put("windScale", now.get("windScale").asText());
                        weatherData.put("windSpeed", now.get("windSpeed").asText());
                        weatherData.put("humidity", now.get("humidity").asText());
                        weatherData.put("precip", now.get("precip").asText());
                        weatherData.put("pressure", now.get("pressure").asText());
                        weatherData.put("vis", now.get("vis").asText());
                        weatherData.put("cloud", now.get("cloud").asText());
                        weatherData.put("dew", now.get("dew").asText());

                        // 添加风切变等级和稳定度指数（模拟数据）
                        weatherData.put("windShearLevel", "low");
                        weatherData.put("stabilityIndex", "C");

                        return weatherData;
                    }
                }
            }
        } catch (Exception e) {
            log.error("调用和风天气 API 失败: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * 生成基于气象数据和阈值配置的风险热力图数据
     */
    private Map<String, Object> generateMockHeatmapData(String pointId, String timeRange, String resolution,
            String bounds, Boolean forRouteAnalysis) {

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
                        "stabilityIndex", weatherData.getStabilityIndex()),
                "thresholds", Map.of(
                        "maxWindSpeed", thresholds.getMaxWindSpeed(),
                        "minVisibility", thresholds.getMinVisibility(),
                        "maxPrecipitation", thresholds.getMaxPrecipitation(),
                        "maxHumidity", thresholds.getMaxHumidity()),
                "generatedAt", LocalDateTime.now().toString()));

        return result;

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
                        .last("LIMIT 1"));

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
        double windShearRisk = "high".equals(windShearLevel) ? 0.9 : "medium".equals(windShearLevel) ? 0.5 : 0.1;
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
    private Map<String, Object> generateAreaHeatmapData(String pointId, String timeRange, String resolution,
            String bounds, Boolean forRouteAnalysis) {

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
                "generatedAt", LocalDateTime.now().toString()));

        return result;
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

                return new double[] { minLng, minLat, maxLng, maxLat };
            }
        } catch (Exception e) {
            // 解析失败
        }
        return null;
    }

    /**
     * 获取区域内的气象数据（使用真实数据库数据）
     */
    private Map<String, Object> getAreaWeatherData(double[] bbox) {
        // 从数据库获取真实气象数据
        Map<String, Object> weatherData = new HashMap<>();

        // 查询微尺度气象数据
        List<MicroscaleWeather> weatherList = microscaleWeatherMapper.selectList(
                new LambdaQueryWrapper<MicroscaleWeather>()
                        .orderByDesc(MicroscaleWeather::getDataTime)
                        .last("LIMIT 100"));

        if (!weatherList.isEmpty()) {
            // 基于数据库数据计算平均气象数据
            Map<String, Object> baseWeather = calculateAverageWeather(weatherList);
            weatherData.put("base", baseWeather);
            weatherData.put("dataSource", "database");
        }

        weatherData.put("bbox", bbox);
        return weatherData;
    }

    /**
     * 计算平均气象数据
     */
    private Map<String, Object> calculateAverageWeather(List<MicroscaleWeather> weatherList) {
        double totalWindSpeed = 0;
        double totalWindShear = 0;
        double totalTurbulence = 0;
        int totalRiskLevel = 0;

        for (MicroscaleWeather weather : weatherList) {
            totalWindSpeed += weather.getWindSpeed() != null ? weather.getWindSpeed().doubleValue() : 0;
            totalWindShear += weather.getWindShear() != null ? weather.getWindShear().doubleValue() : 0;
            totalTurbulence += weather.getTurbulence() != null ? weather.getTurbulence().doubleValue() : 0;
            totalRiskLevel += weather.getRiskLevel();
        }

        int count = weatherList.size();
        Map<String, Object> baseWeather = new HashMap<>();

        // 转换为合适的单位
        baseWeather.put("windSpeed", (totalWindSpeed / count) * 3.6); // 转换为 km/h
        baseWeather.put("visibility", 10.0 - (totalTurbulence / count) * 2); // 湍流越大，能见度越低
        baseWeather.put("precipitation", (totalRiskLevel / count) * 0.5); // 风险等级越高，降水可能越大
        baseWeather.put("humidity", 60 + (int) ((totalWindShear / count) * 20)); // 风切变越大，湿度可能越高

        // 根据数据设置风切变等级
        double avgWindShear = totalWindShear / count;
        if (avgWindShear > 0.8) {
            baseWeather.put("windShearLevel", "high");
        } else if (avgWindShear > 0.4) {
            baseWeather.put("windShearLevel", "medium");
        } else {
            baseWeather.put("windShearLevel", "low");
        }

        // 根据数据设置稳定度指数
        double avgTurbulence = totalTurbulence / count;
        if (avgTurbulence > 0.6) {
            baseWeather.put("stabilityIndex", "D");
        } else if (avgTurbulence > 0.3) {
            baseWeather.put("stabilityIndex", "C");
        } else {
            baseWeather.put("stabilityIndex", "B");
        }

        return baseWeather;
    }

    /**
     * 根据分辨率获取网格大小
     */
    private int getGridSizeFromResolution(String resolution) {
        switch (resolution) {
            case "low":
                return 8;
            case "medium":
                return 12;
            case "high":
                return 16;
            default:
                return 12;
        }
    }

    /**
     * 根据时间范围获取时间点数量
     */
    private int getTimeCountFromRange(String timeRange) {
        switch (timeRange) {
            case "1h":
                return 7;
            case "3h":
                return 7;
            case "6h":
                return 13;
            case "12h":
                return 25;
            default:
                return 7;
        }
    }

    /**
     * 获取天气预测趋势数据（用于折线图）
     * 调用 Open-Meteo API 获取 15 分钟间隔的天气预测数据
     */
    public Map<String, Object> getWeatherForecastTrend(String pointId) {
        try {
            // 根据 pointId 获取监测点坐标
            MonitoringPoint point = monitoringPointService.getById(pointId);
            if (point == null) {
                throw new IllegalArgumentException("监测点不存在: " + pointId);
            }

            // 检查坐标是否有效
            if (point.getLatitude() == null || point.getLongitude() == null) {
                throw new IllegalArgumentException("监测点坐标数据不完整: " + pointId);
            }

            // 1. 检查数据库中是否有今天的预报数据
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.atTime(23, 59, 59);

            List<WeatherForecast> todayForecasts = weatherForecastMapper.selectList(
                    new LambdaQueryWrapper<WeatherForecast>()
                            .eq(WeatherForecast::getPointId, pointId)
                            .ge(WeatherForecast::getForecastTime, startOfDay)
                            .le(WeatherForecast::getForecastTime, endOfDay)
                            .orderByAsc(WeatherForecast::getForecastTime));

            Map<String, Object> weatherData;

            // 2. 如果数据库有今天的预报数据，直接使用
            if (!todayForecasts.isEmpty()) {
                weatherData = convertForecastsToResponse(todayForecasts);
            } else {
                double latitude = point.getLatitude().doubleValue();
                double longitude = point.getLongitude().doubleValue();

                // 3. 数据库没有今天的预报数据，调用 Open-Meteo API
                // 构建 Open-Meteo API 请求参数
                String url = "https://api.open-meteo.com/v1/forecast";
                String params = String.format(
                        "?latitude=%.2f&longitude=%.2f&minutely_15=temperature_2m,wind_speed_10m,visibility,precipitation,weather_code&forecast_days=1",
                        latitude, longitude);

                // 调用 API
                weatherData = callOpenMeteoAPI(url + params);

                // 4. 保存到数据库
                saveForecastDataToDatabase(pointId, weatherData);
            }

            // 5. 过滤数据，只返回从当前时间开始的3小时，每30分钟一个数据点
            Map<String, Object> filteredData = filterNext3HoursData(weatherData);

            Map<String, Object> result = new HashMap<>();
            result.put("updateTime", LocalDateTime.now().toString());
            result.put("pointId", pointId);
            result.put("data", filteredData);
            return result;

        } catch (Exception e) {
            log.error("获取天气预测趋势失败", e);
            return null;
        }
    }

    /**
     * 过滤出从当前时间开始的3小时数据，每30分钟一个数据点
     */
    private Map<String, Object> filterNext3HoursData(Map<String, Object> weatherData) {
        Map<String, Object> filteredData = new HashMap<>();

        // 获取所有数据列表
        List<String> allTimes = (List<String>) weatherData.get("time");
        List<Double> allTemperatures = (List<Double>) weatherData.get("temperature_2m");
        List<Double> allPrecipitation = (List<Double>) weatherData.get("precipitation");
        List<Double> allWindSpeed = (List<Double>) weatherData.get("wind_speed_10m");
        List<Integer> allVisibility = (List<Integer>) weatherData.get("visibility");
        List<Integer> allWeatherCode = (List<Integer>) weatherData.get("weather_code");
        List<String> allWeatherText = (List<String>) weatherData.get("weather_text");

        if (allTimes == null || allTimes.isEmpty()) {
            return filteredData;
        }

        // 过滤后的数据列表
        List<String> filteredTimes = new ArrayList<>();
        List<Double> filteredTemperatures = new ArrayList<>();
        List<Double> filteredPrecipitation = new ArrayList<>();
        List<Double> filteredWindSpeed = new ArrayList<>();
        List<Integer> filteredVisibility = new ArrayList<>();
        List<Integer> filteredWeatherCode = new ArrayList<>();
        List<String> filteredWeatherText = new ArrayList<>();

        // 获取当前时间
        LocalTime now = LocalTime.now();
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();

        // 计算当前时间的分钟数（从0点开始）
        int currentMinutes = currentHour * 60 + currentMinute;
        // 计算3小时后的分钟数
        int endMinutes = currentMinutes + 3 * 60;

        // 遍历所有时间点
        for (int i = 0; i < allTimes.size(); i++) {
            String timeStr = allTimes.get(i);
            String[] parts = timeStr.split(":");
            if (parts.length != 2) {
                continue;
            }

            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            int minutesFromMidnight = hour * 60 + minute;

            // 检查是否在当前时间到3小时后之间
            if (minutesFromMidnight >= currentMinutes && minutesFromMidnight <= endMinutes) {
                // 检查是否是30分钟的倍数（0, 30分钟）
                if (minute == 0 || minute == 30) {
                    filteredTimes.add(timeStr);
                    if (allTemperatures != null && i < allTemperatures.size()) {
                        filteredTemperatures.add(allTemperatures.get(i));
                    }
                    if (allPrecipitation != null && i < allPrecipitation.size()) {
                        filteredPrecipitation.add(allPrecipitation.get(i));
                    }
                    if (allWindSpeed != null && i < allWindSpeed.size()) {
                        filteredWindSpeed.add(allWindSpeed.get(i));
                    }
                    if (allVisibility != null && i < allVisibility.size()) {
                        filteredVisibility.add(allVisibility.get(i));
                    }
                    if (allWeatherCode != null && i < allWeatherCode.size()) {
                        filteredWeatherCode.add(allWeatherCode.get(i));
                    }
                    if (allWeatherText != null && i < allWeatherText.size()) {
                        filteredWeatherText.add(allWeatherText.get(i));
                    }

                }
            }
        }

        // 确保至少有6个数据点
        if (filteredTimes.size() < 6 && !allTimes.isEmpty()) {
            // 如果数据不足，从所有数据中选择最近的6个30分钟间隔的点
            for (int i = 0; i < allTimes.size() && filteredTimes.size() < 6; i++) {
                String timeStr = allTimes.get(i);
                String[] parts = timeStr.split(":");
                if (parts.length != 2) {
                    continue;
                }

                int minute = Integer.parseInt(parts[1]);
                if (minute == 0 || minute == 30) {
                    // 检查是否已经添加
                    if (!filteredTimes.contains(timeStr)) {
                        filteredTimes.add(timeStr);
                        if (allTemperatures != null && i < allTemperatures.size()) {
                            filteredTemperatures.add(allTemperatures.get(i));
                        }
                        if (allPrecipitation != null && i < allPrecipitation.size()) {
                            filteredPrecipitation.add(allPrecipitation.get(i));
                        }
                        if (allWindSpeed != null && i < allWindSpeed.size()) {
                            filteredWindSpeed.add(allWindSpeed.get(i));
                        }
                        if (allVisibility != null && i < allVisibility.size()) {
                            filteredVisibility.add(allVisibility.get(i));
                        }
                        if (allWeatherCode != null && i < allWeatherCode.size()) {
                            filteredWeatherCode.add(allWeatherCode.get(i));
                        }
                        if (allWeatherText != null && i < allWeatherText.size()) {
                            filteredWeatherText.add(allWeatherText.get(i));
                        }

                    }
                }
            }
        }

        // 填充过滤后的数据
        filteredData.put("time", filteredTimes);
        filteredData.put("temperature_2m", filteredTemperatures);
        filteredData.put("precipitation", filteredPrecipitation);
        filteredData.put("wind_speed_10m", filteredWindSpeed);
        filteredData.put("visibility", filteredVisibility);
        filteredData.put("weather_code", filteredWeatherCode);
        filteredData.put("weather_text", filteredWeatherText);

        return filteredData;
    }

    /**
     * 调用 Open-Meteo API
     */
    private Map<String, Object> callOpenMeteoAPI(String apiUrl) {
        RestTemplate restTemplate = new RestTemplate();
        try {
            // 调用 Open-Meteo API
            ResponseEntity<String> responseEntity = restTemplate.getForEntity(apiUrl, String.class);
            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                String responseBody = responseEntity.getBody();
                if (responseBody != null) {
                    // 解析 JSON 响应
                    ObjectMapper objectMapper = new ObjectMapper();
                    ObjectNode jsonResponse = objectMapper.readValue(responseBody, ObjectNode.class);
                    Map<String, Object> minutely15 = new HashMap<>();

                    // 处理时间数据
                    ObjectNode minutely15Object = (ObjectNode) jsonResponse.get("minutely_15");
                    ArrayNode timeArray = (ArrayNode) minutely15Object.get("time");
                    List<String> times = new ArrayList<>();
                    for (int i = 0; i < timeArray.size(); i++) {
                        String timeStr = timeArray.get(i).asText();
                        // 解析 ISO 8601 时间格式
                        LocalDateTime time = LocalDateTime.parse(timeStr);
                        times.add(String.format("%02d:%02d", time.getHour(), time.getMinute()));
                    }

                    // 处理温度数据
                    ArrayNode tempArray = (ArrayNode) minutely15Object.get("temperature_2m");
                    List<Double> temperature = new ArrayList<>();
                    for (int i = 0; i < tempArray.size(); i++) {
                        temperature.add(tempArray.get(i).asDouble());
                    }

                    // 处理降水量数据
                    ArrayNode precipArray = (ArrayNode) minutely15Object.get("precipitation");
                    List<Double> precipitation = new ArrayList<>();
                    for (int i = 0; i < precipArray.size(); i++) {
                        precipitation.add(precipArray.get(i).asDouble());
                    }

                    // 处理风速数据
                    ArrayNode windArray = (ArrayNode) minutely15Object.get("wind_speed_10m");
                    List<Double> windSpeed = new ArrayList<>();
                    for (int i = 0; i < windArray.size(); i++) {
                        windSpeed.add(windArray.get(i).asDouble());
                    }

                    // 处理能见度数据（从米转换为公里）
                    ArrayNode visArray = (ArrayNode) minutely15Object.get("visibility");
                    List<Integer> visibility = new ArrayList<>();
                    for (int i = 0; i < visArray.size(); i++) {
                        visibility.add((int) Math.round(visArray.get(i).asDouble() / 1000.0));
                    }

                    // 处理天气代码
                    ArrayNode weatherArray = (ArrayNode) minutely15Object.get("weather_code");
                    List<Integer> weatherCode = new ArrayList<>();
                    List<String> weatherText = new ArrayList<>();
                    for (int i = 0; i < weatherArray.size(); i++) {
                        int code = weatherArray.get(i).asInt();
                        weatherCode.add(code);
                        Map<String, String> weatherInfo = getWeatherDescription(code);
                        weatherText.add(weatherInfo.get("text"));
                    }

                    minutely15.put("time", times);
                    minutely15.put("temperature_2m", temperature);
                    minutely15.put("precipitation", precipitation);
                    minutely15.put("wind_speed_10m", windSpeed);
                    minutely15.put("visibility", visibility);
                    minutely15.put("weather_code", weatherCode);
                    minutely15.put("weather_text", weatherText);

                    return minutely15;
                }
            }
        } catch (Exception e) {
            log.error("调用 Open-Meteo API 失败: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * 将数据库中的预报数据转换为响应格式
     */
    private Map<String, Object> convertForecastsToResponse(List<WeatherForecast> forecasts) {
        Map<String, Object> minutely15 = new HashMap<>();

        List<String> times = new ArrayList<>();
        List<Double> temperature = new ArrayList<>();
        List<Double> precipitation = new ArrayList<>();
        List<Double> windSpeed = new ArrayList<>();
        List<Integer> visibility = new ArrayList<>();
        List<Integer> weatherCode = new ArrayList<>();
        List<String> weatherText = new ArrayList<>();

        for (WeatherForecast forecast : forecasts) {
            LocalDateTime forecastTime = forecast.getForecastTime();
            times.add(String.format("%02d:%02d", forecastTime.getHour(), forecastTime.getMinute()));
            temperature.add(forecast.getTemperature() != null ? forecast.getTemperature().doubleValue() : 0.0);
            precipitation.add(forecast.getPrecipitation() != null ? forecast.getPrecipitation().doubleValue() : 0.0);
            windSpeed.add(forecast.getWindSpeed() != null ? forecast.getWindSpeed().doubleValue() : 0.0);
            visibility.add(forecast.getVisibility() != null ? forecast.getVisibility().intValue() : 0);
            weatherCode.add(forecast.getWeatherCode());
            weatherText.add(forecast.getWeatherText() != null ? forecast.getWeatherText() : "未知");
        }

        minutely15.put("time", times);
        minutely15.put("temperature_2m", temperature);
        minutely15.put("precipitation", precipitation);
        minutely15.put("wind_speed_10m", windSpeed);
        minutely15.put("visibility", visibility);
        minutely15.put("weather_code", weatherCode);
        minutely15.put("weather_text", weatherText);

        return minutely15;
    }

    /**
     * 保存预报数据到数据库
     */
    private void saveForecastDataToDatabase(String pointId, Map<String, Object> weatherData) {
        List<String> timeStrings = (List<String>) weatherData.get("time");
        List<Double> temperature = (List<Double>) weatherData.get("temperature_2m");
        List<Double> precipitation = (List<Double>) weatherData.get("precipitation");
        List<Double> windSpeed = (List<Double>) weatherData.get("wind_speed_10m");
        List<Integer> visibility = (List<Integer>) weatherData.get("visibility");
        List<Integer> weatherCode = (List<Integer>) weatherData.get("weather_code");

        if (timeStrings == null || temperature == null || precipitation == null ||
                windSpeed == null || visibility == null || weatherCode == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        for (int i = 0; i < timeStrings.size(); i++) {
            WeatherForecast forecast = new WeatherForecast();
            forecast.setPointId(pointId);

            // 将时间字符串（HH:mm）转换为LocalDateTime
            String timeStr = timeStrings.get(i);
            String[] parts = timeStr.split(":");
            if (parts.length == 2) {
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                LocalDateTime forecastTime = LocalDateTime.of(today, LocalTime.of(hour, minute));
                forecast.setForecastTime(forecastTime);
            } else {
                forecast.setForecastTime(now.plusMinutes(i * 15));
            }

            forecast.setTemperature(BigDecimal.valueOf(temperature.get(i)));
            forecast.setPrecipitation(BigDecimal.valueOf(precipitation.get(i)));
            forecast.setWindSpeed(BigDecimal.valueOf(windSpeed.get(i)));
            forecast.setVisibility(BigDecimal.valueOf(visibility.get(i)));
            forecast.setWeatherCode(weatherCode.get(i));

            // 映射天气描述和图标
            Map<String, String> weatherInfo = getWeatherDescription(weatherCode.get(i));
            forecast.setWeatherText(weatherInfo.get("text"));

            forecast.setDataSource("open-meteo");
            forecast.setDataQuality(90);
            forecast.setCreatedAt(now);

            weatherForecastMapper.insert(forecast);
        }
    }

    /**
     * 根据 WMO code 获取天气描述和图标
     */
    private Map<String, String> getWeatherDescription(int weatherCode) {
        Map<String, String> weatherInfo = new HashMap<>();

        switch (weatherCode) {
            case 0: // 晴天
                weatherInfo.put("text", "晴天");
                break;
            case 1: // 大部分晴天
            case 2: // 部分晴天
            case 3: // 阴天
                weatherInfo.put("text", "多云");
                break;
            case 45: // 雾
            case 48: // 霾
                weatherInfo.put("text", "雾");
                break;
            case 51: // 小雨
            case 53: // 中雨
            case 55: // 大雨
                weatherInfo.put("text", "雨");
                break;
            case 61: // 小雨
            case 63: // 中雨
            case 65: // 大雨
                weatherInfo.put("text", "雨");
                break;
            case 71: // 小雪
            case 73: // 中雪
            case 75: // 大雪
                weatherInfo.put("text", "雪");
                break;
            case 80: // 阵雨
            case 81: // 中阵雨
            case 82: // 大阵雨
                weatherInfo.put("text", "阵雨");
                break;
            default:
                weatherInfo.put("text", "未知");
        }

        return weatherInfo;
    }

    /**
     * 生成时间标签
     */
    private List<String> generateTimeLabels(String timeRange, int timeCount) {
        return generateTimeLabels(timeRange, timeCount, LocalDateTime.now());
    }

    /**
     * 生成时间标签（带基准时间）
     */
    private List<String> generateTimeLabels(String timeRange, int timeCount, LocalDateTime baseTime) {
        List<String> times = new ArrayList<>();

        int interval = 30; // 默认30分钟间隔
        if ("1h".equals(timeRange))
            interval = 10;

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
     * 从数据库获取气象数据点
     */
    private List<MicroscaleWeather> getWeatherPointsFromDatabase(String pointId, double[] bbox) {
        // 查询数据库中的微尺度气象数据
        return microscaleWeatherMapper.selectList(
                new LambdaQueryWrapper<MicroscaleWeather>()
                        .eq(MicroscaleWeather::getPointId, pointId)
                        .or()
                        .orderByDesc(MicroscaleWeather::getDataTime));
    }

    /**
     * 通过插值获取指定位置的气象数据
     */
    private Map<String, Object> interpolateWeatherData(double lng, double lat, List<MicroscaleWeather> weatherPoints,
            double[] bbox) {
        if (weatherPoints.isEmpty()) {
            // 如果没有数据，使用默认值
            Map<String, Object> baseWeather = new HashMap<>();
            baseWeather.put("windSpeed", 10.0);
            baseWeather.put("visibility", 10.0);
            baseWeather.put("precipitation", 0.0);
            baseWeather.put("humidity", 70);
            baseWeather.put("windShearLevel", "low");
            baseWeather.put("stabilityIndex", "B");
            return baseWeather;
        }

        // 计算每个数据点的权重（基于距离的反平方）
        double totalWeight = 0;
        double weightedWindSpeed = 0;
        double weightedWindShear = 0;
        double weightedTurbulence = 0;
        double weightedRiskLevel = 0;

        for (MicroscaleWeather weather : weatherPoints) {
            // 为每个数据点分配一个模拟的经纬度位置
            // 基于pointId和数据索引生成合理的位置
            double pointLng = getSimulatedLongitude(weather.getPointId(), weatherPoints.indexOf(weather), bbox);
            double pointLat = getSimulatedLatitude(weather.getPointId(), weatherPoints.indexOf(weather), bbox);

            // 计算距离
            double distance = Math.sqrt(Math.pow(lng - pointLng, 2) + Math.pow(lat - pointLat, 2));

            // 避免除零错误
            if (distance < 0.0001) {
                distance = 0.0001;
            }

            // 计算权重（距离的反平方）
            double weight = 1.0 / (distance * distance);
            totalWeight += weight;

            // 加权累加
            weightedWindSpeed += (weather.getWindSpeed() != null ? weather.getWindSpeed().doubleValue() : 0) * weight;
            weightedWindShear += (weather.getWindShear() != null ? weather.getWindShear().doubleValue() : 0) * weight;
            weightedTurbulence += (weather.getTurbulence() != null ? weather.getTurbulence().doubleValue() : 0)
                    * weight;
            weightedRiskLevel += weather.getRiskLevel() * weight;
        }

        // 计算加权平均值
        double avgWindSpeed = weightedWindSpeed / totalWeight;
        double avgWindShear = weightedWindShear / totalWeight;
        double avgTurbulence = weightedTurbulence / totalWeight;
        double avgRiskLevel = weightedRiskLevel / totalWeight;

        // 构建气象数据
        Map<String, Object> baseWeather = new HashMap<>();
        baseWeather.put("windSpeed", avgWindSpeed * 3.6); // 转换为 km/h
        baseWeather.put("visibility", 10.0 - (avgTurbulence * 2)); // 湍流越大，能见度越低
        baseWeather.put("precipitation", avgRiskLevel * 0.5); // 风险等级越高，降水可能越大
        baseWeather.put("humidity", 60 + (int) (avgWindShear * 20)); // 风切变越大，湿度可能越高

        // 设置风切变等级
        if (avgWindShear > 0.8) {
            baseWeather.put("windShearLevel", "high");
        } else if (avgWindShear > 0.4) {
            baseWeather.put("windShearLevel", "medium");
        } else {
            baseWeather.put("windShearLevel", "low");
        }

        // 设置稳定度指数
        if (avgTurbulence > 0.6) {
            baseWeather.put("stabilityIndex", "D");
        } else if (avgTurbulence > 0.3) {
            baseWeather.put("stabilityIndex", "C");
        } else {
            baseWeather.put("stabilityIndex", "B");
        }

        return baseWeather;
    }

    /**
     * 为气象数据点生成模拟的经度
     */
    private double getSimulatedLongitude(String pointId, int index, double[] bbox) {
        double minLng = bbox[0];
        double maxLng = bbox[2];
        double range = maxLng - minLng;

        // 基于pointId和索引生成不同的位置
        switch (pointId) {
            case "point-ninghe-center":
                return minLng + range * (0.5 + Math.sin(index * 0.7) * 0.3);
            case "point-ninghe-airport":
                return minLng + range * (0.7 + Math.cos(index * 0.5) * 0.2);
            case "point-ninghe-operation":
                return minLng + range * (0.3 + Math.sin(index * 0.9) * 0.25);
            default:
                return minLng + range * (0.1 + index % 9) / 9.0;
        }
    }

    /**
     * 为气象数据点生成模拟的纬度
     */
    private double getSimulatedLatitude(String pointId, int index, double[] bbox) {
        double minLat = bbox[1];
        double maxLat = bbox[3];
        double range = maxLat - minLat;

        // 基于pointId和索引生成不同的位置
        switch (pointId) {
            case "point-ninghe-center":
                return minLat + range * (0.5 + Math.cos(index * 0.8) * 0.3);
            case "point-ninghe-airport":
                return minLat + range * (0.7 + Math.sin(index * 0.6) * 0.2);
            case "point-ninghe-operation":
                return minLat + range * (0.3 + Math.cos(index * 0.7) * 0.25);
            default:
                return minLat + range * (0.1 + (index / 3) % 9) / 9.0;
        }
    }

    /**
     * 生成图表热力图数据（时间-高度矩阵）
     */
    private Map<String, Object> generateChartHeatmapData(String pointId, String timeRange, String resolution,
            Boolean forRouteAnalysis) {
        // 这就是原来的 generateMockHeatmapData 逻辑，用于ECharts图表
        return generateMockHeatmapData(pointId, timeRange, resolution, null, forRouteAnalysis);
    }

    /**
     * 生成地理空间热力图数据（经纬度风险点）
     */
    private Map<String, Object> generateGeoHeatmapData(String bounds, String time, String resolution, String pointId) {

        // 解析边界框
        double[] bbox = parseBoundingBox(bounds);

        double minLng = bbox[0];
        double minLat = bbox[1];
        double maxLng = bbox[2];
        double maxLat = bbox[3];

        // 根据分辨率确定网格大小
        int gridSize = getGridSizeFromResolution(resolution);

        // 获取阈值配置
        AircraftLimit thresholds = getDefaultAircraftLimits();

        // 获取数据库中的真实气象数据点
        List<MicroscaleWeather> weatherPoints = getWeatherPointsFromDatabase(pointId, bbox);

        // 生成网格点数据
        List<Map<String, Object>> points = new ArrayList<>();

        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                // 计算网格点坐标
                double lng = minLng + (maxLng - minLng) * (i / (double) (gridSize - 1));
                double lat = minLat + (maxLat - minLat) * (j / (double) (gridSize - 1));

                // 通过插值获取该点的气象数据
                Map<String, Object> baseWeather = interpolateWeatherData(lng, lat, weatherPoints, bbox);

                // 计算基础风险
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
                "resolution", resolution));

        return result;

    }

    /**
     * 获取风险等级
     */
    private String getRiskLevel(int riskValue) {
        if (riskValue >= 80)
            return "high";
        if (riskValue >= 60)
            return "medium";
        if (riskValue >= 40)
            return "low";
        return "very_low";
    }

    /**
     * 获取全市范围热力图数据
     *
     * @return 热力图数据
     */
    public Map<String, Object> getCitywideHeatmap() {

        // 获取当前区域的边界
        RegionConfig.Bounds boundsConfig = regionConfig.getBounds();
        String citywideBounds = String.format("[%s,%s,%s,%s]",
                boundsConfig.getWest(), boundsConfig.getSouth(),
                boundsConfig.getEast(), boundsConfig.getNorth());

        // 使用通用热力图生成方法，传入null作为pointIds表示区域级查询
        Map<String, Object> heatmapData = generateCommonHeatmapData(citywideBounds, null);

        // 提取points数据
        List<Map<String, Object>> points = (List<Map<String, Object>>) heatmapData.get("points");

        // 解析边界框
        double[] bbox = parseBoundingBox(citywideBounds);
        Map<String, Object> bounds = new HashMap<>();
        if (bbox != null && bbox.length >= 4) {
            bounds.put("minLon", bbox[0]);
            bounds.put("minLat", bbox[1]);
            bounds.put("maxLon", bbox[2]);
            bounds.put("maxLat", bbox[3]);
        } else {
            bounds.put("minLon", 120.0);
            bounds.put("minLat", 36.0);
            bounds.put("maxLon", 121.0);
            bounds.put("maxLat", 37.0);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("points", points);
        result.put("bounds", bounds);

        result.put("totalPoints", points != null ? points.size() : 0);
        result.put("dataType", "citywide_heatmap");

        return result;

    }

    /**
     * 生成模拟的全市范围热力图数据（降级方案）
     */
    private Map<String, Object> generateMockCitywideHeatmapData(Integer totalHours, String resolution,
            String baseTime) {
        log.warn("使用模拟热力图数据（降级方案），参数: totalHours={}, resolution={}, baseTime={}",
                totalHours, resolution, baseTime);

        // 解析基准时间
        LocalDateTime baseDateTime;
        if (baseTime != null && !baseTime.isEmpty()) {
            try {
                baseDateTime = LocalDateTime.parse(baseTime.replace("Z", ""));
            } catch (Exception e) {
                baseDateTime = LocalDateTime.now();
            }
        } else {
            baseDateTime = LocalDateTime.now();
        }

        // 青岛市边界
        double minLon = 120.0;
        double minLat = 36.0;
        double maxLon = 121.0;
        double maxLat = 37.0;

        // 根据分辨率确定网格密度
        int gridSize;
        if ("low".equals(resolution)) {
            gridSize = 10;
        } else if ("high".equals(resolution)) {
            gridSize = 30;
        } else {
            gridSize = 20; // medium
        }

        // 生成随机风险点
        List<Map<String, Object>> points = new ArrayList<>();
        Random random = new Random(baseDateTime.hashCode()); // 使用基准时间作为随机种子，确保相同时间返回相同数据

        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                double lon = minLon + (maxLon - minLon) * (i / (double) (gridSize - 1));
                double lat = minLat + (maxLat - minLat) * (j / (double) (gridSize - 1));

                // 基于时间生成风险值（模拟随时间变化）
                // 早上风险较低，下午风险较高
                int hour = baseDateTime.getHour();
                double timeFactor = 0.5 + 0.5 * Math.sin(Math.PI * hour / 12.0);

                // 添加一些空间变化（模拟高风险区域）
                double spatialFactor = 1.0;
                if (lon > 120.4 && lon < 120.7 && lat > 36.4 && lat < 36.7) {
                    spatialFactor = 1.8; // 市区风险较高
                } else if (lon > 120.7 && lat > 36.7) {
                    spatialFactor = 0.6; // 海边风险较低
                }

                // 添加随机波动
                double randomFactor = 0.8 + 0.4 * random.nextDouble();

                // 计算最终风险值 (0-100)
                int riskValue = (int) Math.round(timeFactor * spatialFactor * randomFactor * 50);
                riskValue = Math.min(100, Math.max(0, riskValue));

                Map<String, Object> point = new HashMap<>();
                point.put("lon", lon);
                point.put("lat", lat);
                point.put("value", riskValue);
                points.add(point);
            }
        }

        Map<String, Object> bounds = new HashMap<>();
        bounds.put("minLon", minLon);
        bounds.put("minLat", minLat);
        bounds.put("maxLon", maxLon);
        bounds.put("maxLat", maxLat);

        Map<String, Object> result = new HashMap<>();
        result.put("points", points);
        result.put("bounds", bounds);
        result.put("timestamp", baseDateTime.toString());
        result.put("resolution", resolution);
        result.put("totalPoints", points.size());
        result.put("baseTime", baseDateTime.toString());
        result.put("isMockData", true); // 标记为模拟数据

        return result;
    }

}
