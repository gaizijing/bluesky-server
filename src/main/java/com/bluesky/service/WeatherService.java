package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.LandingPoint;
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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import com.bluesky.util.TimeBucketUtil;

/**
 * 气象数据服务
 * 负责实时天气、风向趋势、微尺度天气等数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final WeatherRealtimeMapper weatherRealtimeMapper;
    private final RiskFieldCacheMapper riskFieldCacheMapper;
    private static final int DEFAULT_RISK_HEIGHT_M = 100;
    private final WeatherForecastMapper weatherForecastMapper;
    private final AircraftLimitMapper aircraftLimitMapper;
    private final LandingPointService landingPointService;
    private final RegionService regionService;
    private final RegionBoundaryService regionBoundaryService;
    private static final int CITYWIDE_MAX_SOURCE_POINTS = 15000;
    private static final int IDW_NEIGHBOR_LIMIT = 20;
    private static final double IDW_POWER = 2.0d;
    private static final int CITYWIDE_SYNTHETIC_THRESHOLD = 1200;
    private static final int CITYWIDE_GAUSSIAN_NEIGHBOR_LIMIT = 30;
    private static final int CITYWIDE_FAST_GRID_SIZE = 70;
    private static final int CITYWIDE_FAST_MAX_POINTS = 12000;
    private static final long FORECAST_CACHE_TTL_MS = 10 * 60 * 1000L;

    private record ForecastSample(LocalDateTime time, double windSpeedMs, double visibilityKm,
                                  double precipMmH, double temperatureC, int weatherCode) {}

    private static final class ForecastSeriesCache {
        private final long fetchedAtMs;
        private final List<ForecastSample> samples;

        private ForecastSeriesCache(long fetchedAtMs, List<ForecastSample> samples) {
            this.fetchedAtMs = fetchedAtMs;
            this.samples = samples;
        }
    }

    private final ConcurrentHashMap<String, ForecastSeriesCache> forecastSeriesCache = new ConcurrentHashMap<>();

    private static final class IdwSamplePoint {
        private final double lng;
        private final double lat;
        private final double value;

        private IdwSamplePoint(double lng, double lat, double value) {
            this.lng = lng;
            this.lat = lat;
            this.value = value;
        }
    }

    // ==================== 鐎圭偞妞傚鏃囪杽 ====================

    /**
     * 获取重点关注区域实时气象数据
     * 先查数据库，如果没有则调用和风天气API
     */
    public Map<String, Object> getRealtimeWeather(String pointId) {
        WeatherRealtime latest = safeSelectLatestRealtime(pointId);

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
            LandingPoint point = landingPointService.getEntity(pointId);
            double longitude = point.getLongitude().doubleValue();
            double latitude = point.getLatitude().doubleValue();

            // 调用和风天气API
            Map<String, Object> weatherData = callQWeatherAPI(longitude, latitude);
            if (weatherData == null) {
                log.warn("和风天气API调用失败，尝试返回数据库中的旧数据");
                if (latest != null) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("updateTime", LocalDateTime.now().toString());
                    result.put("data", latest);
                    result.put("warning", "无法获取最新气象数据，当前显示的是历史数据。请检查网络连接后重试。");
                    result.put("dataSource", "database_cache");
                    return result;
                }

                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("updateTime", LocalDateTime.now().toString());
                errorResult.put("error", true);
                errorResult.put("message", "请检查网络连接");
                errorResult.put("detail", "无法连接到气象数据服务，可能原因：1.网络连接异常 2.气象服务暂时不可用 3.API配置错误");
                errorResult.put("suggestion", "请检查您的网络连接，或稍后重试。如果问题持续存在，请联系系统管理员。");
                return errorResult;
            }
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

            try {
                weatherRealtimeMapper.insert(newRecord);
            } catch (Exception persistEx) {
                log.warn("weather_realtime 不可用，跳过持久化: {}", persistEx.getMessage());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("updateTime", LocalDateTime.now().toString());
            result.put("data", newRecord);
            return result;

        } catch (Exception e) {
            log.error("获取实时气象数据异常: {}", e.getMessage(), e);

            if (latest != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("updateTime", LocalDateTime.now().toString());
                result.put("data", latest);
                result.put("warning", "获取最新数据时发生错误，当前显示的是历史数据。请检查网络连接后重试。");
                result.put("dataSource", "database_cache");
                result.put("error", e.getMessage());
                return result;
            }

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("updateTime", LocalDateTime.now().toString());
            errorResult.put("error", true);
            errorResult.put("message", "请检查网络连接");
            errorResult.put("detail", "获取气象数据时发生异常：" + e.getMessage());
            errorResult.put("suggestion", "请检查您的网络连接，或稍后重试。如果问题持续存在，请联系系统管理员。");
            return errorResult;
        }
    }

    /**
     * 适飞计算用气象因子（V2 无 weather_realtime 时走和风坐标查询）
     */
    public Map<String, Object> buildFlyabilityWeatherMap(String pointId) {
        return buildFlyabilityWeatherMap(pointId, TimeBucketUtil.currentBucketLocal());
    }

    public Map<String, Object> buildFlyabilityWeatherMap(String pointId, LocalDateTime bucketTime) {
        LandingPoint point = landingPointService.getEntity(pointId);
        return buildFlyabilityWeatherMap(
                point.getLongitude().doubleValue(), point.getLatitude().doubleValue(), bucketTime);
    }

    public Map<String, Object> buildFlyabilityWeatherMap(double lng, double lat) {
        return buildFlyabilityWeatherMap(lng, lat, TimeBucketUtil.currentBucketLocal());
    }

    /** 按 15min 时间桶取气象：当前及过去桶走实时，未来桶走 Open-Meteo 预报 */
    public Map<String, Object> buildFlyabilityWeatherMap(double lng, double lat, LocalDateTime bucketTime) {
        LocalDateTime bucket = TimeBucketUtil.toBucketLocal(
                bucketTime.atZone(TimeBucketUtil.ZONE).toOffsetDateTime());
        if (!bucket.isAfter(TimeBucketUtil.currentBucketLocal())) {
            return toFlyabilityFactorMap(getWeatherByCoordinates(lng, lat));
        }
        return buildFlyabilityWeatherFromForecast(lng, lat, bucket);
    }

    private Map<String, Object> buildFlyabilityWeatherFromForecast(double lng, double lat, LocalDateTime bucket) {
        ForecastSample sample = findForecastSample(lng, lat, bucket);
        if (sample == null) {
            log.warn("预报未命中 bucket={} at {},{}，回退实时气象", bucket, lng, lat);
            return toFlyabilityFactorMap(getWeatherByCoordinates(lng, lat));
        }
        Map<String, Object> flat = new LinkedHashMap<>();
        flat.put("windSpeed", sample.windSpeedMs());
        flat.put("visibility", sample.visibilityKm());
        flat.put("precipitation", sample.precipMmH());
        flat.put("temperature", sample.temperatureC());
        flat.put("cloudBase", estimateCloudBaseM(sample.weatherCode()));
        enrichDerivedWeatherFactors(flat);
        return flat;
    }

    private double estimateCloudBaseM(int weatherCode) {
        if (weatherCode >= 45 && weatherCode <= 48) {
            return 120d;
        }
        if (weatherCode >= 51 && weatherCode <= 67) {
            return 250d;
        }
        if (weatherCode >= 71 && weatherCode <= 77) {
            return 200d;
        }
        if (weatherCode >= 80 && weatherCode <= 99) {
            return 180d;
        }
        return 500d;
    }

    private ForecastSample findForecastSample(double lng, double lat, LocalDateTime bucket) {
        List<ForecastSample> series = loadForecastSeries(lng, lat);
        if (series.isEmpty()) {
            return null;
        }
        ForecastSample exact = null;
        ForecastSample nearest = null;
        long nearestMinutes = Long.MAX_VALUE;
        for (ForecastSample sample : series) {
            if (sample.time().equals(bucket)) {
                exact = sample;
                break;
            }
            long diff = Math.abs(ChronoUnit.MINUTES.between(sample.time(), bucket));
            if (diff < nearestMinutes) {
                nearestMinutes = diff;
                nearest = sample;
            }
        }
        if (exact != null) {
            return exact;
        }
        return nearestMinutes <= TimeBucketUtil.BUCKET_MINUTES ? nearest : null;
    }

    private List<ForecastSample> loadForecastSeries(double lng, double lat) {
        String key = String.format(Locale.US, "%.4f,%.4f", lng, lat);
        long nowMs = System.currentTimeMillis();
        ForecastSeriesCache cached = forecastSeriesCache.get(key);
        if (cached != null && nowMs - cached.fetchedAtMs < FORECAST_CACHE_TTL_MS) {
            return cached.samples;
        }
        List<ForecastSample> samples = fetchOpenMeteoForecastSeries(lng, lat);
        forecastSeriesCache.put(key, new ForecastSeriesCache(nowMs, samples));
        return samples;
    }

    private List<ForecastSample> fetchOpenMeteoForecastSeries(double lng, double lat) {
        Map<String, Object> raw = callOpenMeteoAPI(buildOpenMeteoForecastUri(lat, lng));
        if (raw == null) {
            return List.of();
        }
        List<String> times = castList(raw.get("time_iso"));
        if (times == null || times.isEmpty()) {
            times = castList(raw.get("time"));
        }
        if (times == null || times.isEmpty()) {
            return List.of();
        }
        List<Double> temperatures = castList(raw.get("temperature_2m"));
        List<Double> windSpeeds = castList(raw.get("wind_speed_10m"));
        List<Integer> visibilities = castList(raw.get("visibility"));
        List<Double> precipitations = castList(raw.get("precipitation"));
        List<Integer> weatherCodes = castList(raw.get("weather_code"));

        List<ForecastSample> samples = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            LocalDateTime time = parseForecastTime(times.get(i));
            if (time == null) {
                continue;
            }
            double windMs = valueAt(windSpeeds, i, 0d) / 3.6d;
            double visibilityKm = valueAt(visibilities, i, 0);
            samples.add(new ForecastSample(
                    time,
                    windMs,
                    visibilityKm,
                    valueAt(precipitations, i, 0d),
                    valueAt(temperatures, i, 0d),
                    valueAt(weatherCodes, i, 0)));
        }
        return samples;
    }

    private LocalDateTime parseForecastTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            if (raw.length() <= 5 && raw.contains(":")) {
                LocalTime time = LocalTime.parse(raw.length() == 5 ? raw : ("0" + raw));
                return LocalDate.now(TimeBucketUtil.ZONE).atTime(time);
            }
            return LocalDateTime.parse(raw);
        } catch (Exception ex) {
            log.warn("无法解析预报时间: {}", raw);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castList(Object value) {
        if (value instanceof List<?> list) {
            return (List<T>) list;
        }
        return null;
    }

    private double valueAt(List<Double> values, int index, double fallback) {
        if (values == null || index >= values.size() || values.get(index) == null) {
            return fallback;
        }
        return values.get(index);
    }

    private int valueAt(List<Integer> values, int index, int fallback) {
        if (values == null || index >= values.size() || values.get(index) == null) {
            return fallback;
        }
        return values.get(index);
    }

    /** V2：和风坐标结果 → 统一气象点字段（m/s、km 等） */
    public Map<String, Object> flattenCoordinatesWeather(Map<String, Object> apiResult) {
        if (apiResult == null || Boolean.TRUE.equals(apiResult.get("error"))) {
            return Map.of();
        }
        return toFlyabilityFactorMap(apiResult);
    }

    private WeatherRealtime safeSelectLatestRealtime(String pointId) {
        try {
            return weatherRealtimeMapper.selectOne(
                    new LambdaQueryWrapper<WeatherRealtime>()
                            .eq(WeatherRealtime::getPointId, pointId)
                            .orderByDesc(WeatherRealtime::getObsTime)
                            .last("LIMIT 1"));
        } catch (Exception ex) {
            log.warn("weather_realtime 表不可用，改走实时 API: {}", ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractWeatherDataMap(Object dataObj) {
        if (dataObj instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        if (dataObj instanceof WeatherRealtime wr) {
            Map<String, Object> out = new LinkedHashMap<>();
            if (wr.getTemp() != null) out.put("temp", wr.getTemp().toString());
            if (wr.getFeelsLike() != null) out.put("feelsLike", wr.getFeelsLike().toString());
            if (wr.getIcon() != null) out.put("icon", wr.getIcon().toString());
            if (wr.getText() != null) out.put("text", wr.getText());
            if (wr.getWind360() != null) out.put("wind360", wr.getWind360().toString());
            if (wr.getWindDir() != null) out.put("windDir", wr.getWindDir());
            if (wr.getWindScale() != null) out.put("windScale", wr.getWindScale());
            if (wr.getWindSpeed() != null) out.put("windSpeed", wr.getWindSpeed().toString());
            if (wr.getHumidity() != null) out.put("humidity", wr.getHumidity().toString());
            if (wr.getPrecip() != null) out.put("precip", wr.getPrecip().toString());
            if (wr.getPressure() != null) out.put("pressure", wr.getPressure().toString());
            if (wr.getVis() != null) out.put("vis", wr.getVis().toString());
            if (wr.getCloud() != null) out.put("cloud", wr.getCloud().toString());
            if (wr.getDew() != null) out.put("dew", wr.getDew().toString());
            if (wr.getWindShearLevel() != null) out.put("windShearLevel", wr.getWindShearLevel());
            if (wr.getStabilityIndex() != null) out.put("stabilityIndex", wr.getStabilityIndex());
            return out;
        }
        return Map.of();
    }

    private Map<String, Object> toFlyabilityFactorMap(Map<String, Object> apiResult) {
        Map<String, Object> flat = new LinkedHashMap<>();
        if (apiResult == null || Boolean.TRUE.equals(apiResult.get("error"))) {
            return flat;
        }
        Map<String, Object> d = extractWeatherDataMap(apiResult.get("data"));
        double windKmh = parseDouble(d.get("windSpeed"));
        flat.put("windSpeed", windKmh > 0 ? windKmh / 3.6 : 0d);
        // 风向用 wind360（0–360°）；windDir 为和风文字描述（如「东北风」），不可 parse 成数字
        flat.put("windDirection", parseDouble(d.get("wind360")));
        flat.put("windDirText", d.get("windDir"));
        flat.put("visibility", parseDouble(d.get("vis")));
        flat.put("precipitation", parseDouble(d.get("precip")));
        flat.put("temperature", parseDouble(d.get("temp")));
        flat.put("humidity", parseDouble(d.get("humidity")));
        flat.put("cloudBase", 500d);
        enrichDerivedWeatherFactors(flat, d);
        return flat;
    }

    /** 补充风切变、颠簸指数、湍流（和风 API 或预报字段 → 适飞计算用数值） */
    private void enrichDerivedWeatherFactors(Map<String, Object> flat) {
        enrichDerivedWeatherFactors(flat, flat);
    }

    private void enrichDerivedWeatherFactors(Map<String, Object> flat, Map<String, Object> source) {
        double windMs = doubleVal(flat.get("windSpeed"));
        double visKm = doubleVal(flat.get("visibility"));
        flat.put("windShearMs", estimateWindShearMs(windMs, source.get("windShearLevel")));

        double turbIdx = parseDouble(source.get("turbulenceIndex"));
        if (turbIdx <= 0d && (windMs > 0d || visKm > 0d)) {
            turbIdx = calculateTurbulenceIndex(windMs * 3.6, visKm);
        }
        flat.put("turbulenceIndex", turbIdx);
        flat.put("turbulence", estimateTurbulence(turbIdx, windMs));
    }

    private double estimateWindShearMs(double windSpeedMs, Object levelObj) {
        if (levelObj != null) {
            String level = String.valueOf(levelObj).trim().toLowerCase(Locale.ROOT);
            if ("high".equals(level)) return 5d;
            if ("medium".equals(level)) return 3d;
            if ("low".equals(level)) return 1d;
        }
        return Math.min(5d, Math.max(0.5d, windSpeedMs * 0.2d));
    }

    private double estimateTurbulence(double turbulenceIndex, double windSpeedMs) {
        double windComponent = Math.min(1d, windSpeedMs / 15d) * 0.3d;
        double value = turbulenceIndex * 0.7d + windComponent;
        return Math.round(Math.min(1d, Math.max(0d, value)) * 100d) / 100d;
    }

    private double doubleVal(Object value) {
        return parseDouble(value);
    }

    private double parseDouble(Object value) {
        if (value == null) return 0d;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0d;
        }
    }

    /**
     * 获取微尺度天气数据(热力图)
     */
    public Map<String, Object> getMicroscaleWeather(String region, String timeRange) {
        LocalDateTime targetTime = parseRequestTime(timeRange);
        String regionId = region != null && !region.isBlank()
                ? region
                : regionService.getDefault().getRegionId();
        var regionEntity = regionService.getEntity(regionId);
        var envelope = regionBoundaryService.resolveEnvelope(regionEntity);
        List<RiskFieldCache> cells = loadRiskFieldCells(
                regionId,
                targetTime,
                DEFAULT_RISK_HEIGHT_M,
                envelope.west(),
                envelope.south(),
                envelope.east(),
                envelope.north());

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("region", regionId);
        result.put("data", cells);
        return result;
    }

    /**
     * 通用热力图生成方法
     *
     * @param bounds   边界框坐标，格式：[minLng,minLat,maxLng,maxLat]
     * @param pointIds 监测点ID列表，null表示区域级
     * @return 热力图数据
     */
    private List<Map<String, Object>> generateCommonHeatmapData(String bounds, List<String> pointIds, LocalDateTime targetTime) {
        double[] bbox = parseBoundingBox(bounds);
        if (bbox == null) {
            return Collections.emptyList();
        }

        List<LandingPoint> monitorPoints = new ArrayList<>();
        if (pointIds != null && !pointIds.isEmpty()) {
            for (String pointId : pointIds) {
                if (pointId == null || pointId.isEmpty()) {
                    continue;
                }
                LandingPoint point = landingPointService.getEntity(pointId);
                if (point != null) {
                    monitorPoints.add(point);
                }
            }
        } else {
            monitorPoints.addAll(landingPointService.listAllEntities());
        }

        List<Map<String, Object>> points = new ArrayList<>();
        for (LandingPoint point : monitorPoints) {
            points.addAll(buildHeatmapPointsForMonitor(point, targetTime));
        }
        return points;
    }

    private List<Map<String, Object>> buildHeatmapPointsForMonitor(LandingPoint monitor, LocalDateTime targetTime) {
        if (monitor == null || monitor.getBboxMinLng() == null || monitor.getBboxMinLat() == null
                || monitor.getBboxMaxLng() == null || monitor.getBboxMaxLat() == null) {
            return Collections.emptyList();
        }

        double pointMinLng = monitor.getBboxMinLng().doubleValue();
        double pointMinLat = monitor.getBboxMinLat().doubleValue();
        double pointMaxLng = monitor.getBboxMaxLng().doubleValue();
        double pointMaxLat = monitor.getBboxMaxLat().doubleValue();

        String regionId = monitor.getRegionId();
        if (regionId == null || regionId.isBlank()) {
            return Collections.emptyList();
        }

        List<RiskFieldCache> cells = loadRiskFieldCells(
                regionId,
                targetTime,
                DEFAULT_RISK_HEIGHT_M,
                pointMinLng,
                pointMinLat,
                pointMaxLng,
                pointMaxLat);

        List<Map<String, Object>> points = new ArrayList<>(cells.size());
        for (RiskFieldCache cell : cells) {
            if (cell.getLng() == null || cell.getLat() == null || cell.getValue() == null) {
                continue;
            }
            Map<String, Object> point = new HashMap<>();
            point.put("lnglat", Arrays.asList(cell.getLng(), cell.getLat()));
            point.put("value", cell.getValue());
            if (cell.getReason() != null && !cell.getReason().isBlank()) {
                point.put("reason", cell.getReason());
            }
            if (cell.getLevel() != null) {
                point.put("level", cell.getLevel());
            }
            point.put("bboxMinLng", pointMinLng);
            point.put("bboxMinLat", pointMinLat);
            point.put("bboxMaxLng", pointMaxLng);
            point.put("bboxMaxLat", pointMaxLat);
            points.add(point);
        }
        return points;
    }

    private List<RiskFieldCache> loadRiskFieldCells(String regionId, LocalDateTime targetTime, int heightM,
                                                    Double west, Double south, Double east, Double north) {
        if (regionId == null || regionId.isBlank()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<RiskFieldCache> latestWrapper = new LambdaQueryWrapper<RiskFieldCache>()
                .eq(RiskFieldCache::getRegionId, regionId)
                .eq(RiskFieldCache::getHeightM, heightM);
        if (targetTime != null) {
            latestWrapper.le(RiskFieldCache::getBucketTime, targetTime);
        }
        latestWrapper.orderByDesc(RiskFieldCache::getBucketTime).last("LIMIT 1");

        RiskFieldCache latest = riskFieldCacheMapper.selectOne(latestWrapper);
        if (latest == null || latest.getBucketTime() == null) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<RiskFieldCache> wrapper = new LambdaQueryWrapper<RiskFieldCache>()
                .eq(RiskFieldCache::getRegionId, regionId)
                .eq(RiskFieldCache::getBucketTime, latest.getBucketTime())
                .eq(RiskFieldCache::getHeightM, heightM);
        if (west != null && east != null && south != null && north != null) {
            wrapper.ge(RiskFieldCache::getLng, west).le(RiskFieldCache::getLng, east)
                    .ge(RiskFieldCache::getLat, south).le(RiskFieldCache::getLat, north);
        }
        return riskFieldCacheMapper.selectList(wrapper);
    }

    /**
     * Build area-mode heatmap data.
     */
    public Map<String, Object> getWeatherHeatmapGeo(String bounds, String time, String pointId) {
        LocalDateTime targetTime = parseRequestTime(time);
        List<String> pointIds = pointId != null && !pointId.isBlank()
                ? Collections.singletonList(pointId)
                : null;
        List<Map<String, Object>> points = generateCommonHeatmapData(bounds, pointIds, targetTime);

        Map<String, Object> result = new HashMap<>();
        result.put("data", points);
        return result;
    }

    // ==================== 瀹搞儱鍙块弬瑙勭《 ====================

    /**
     * 根据经纬度获取实时气象数据
     * 直接调用和风天气API获取指定坐标的气象信息
     *
     * @param longitude 经度
     * @param latitude  纬度
     * @return 气象数据Map，包含风速、风向、能见度、温度等信息
     */
    public Map<String, Object> getWeatherByCoordinates(double longitude, double latitude) {
        // 验证经纬度有效性
        if (longitude < -180 || longitude > 180) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", true);
            errorResult.put("message", "无效的经度值，范围应在 -180 到 180 之间");
            return errorResult;
        }
        if (latitude < -90 || latitude > 90) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", true);
            errorResult.put("message", "无效的纬度值，范围应在 -90 到 90 之间");
            return errorResult;
        }

        // 调用和风天气API
        Map<String, Object> weatherData = callQWeatherAPI(longitude, latitude);
        if (weatherData == null) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", true);
            errorResult.put("message", "无法获取气象数据，请稍后重试");
            errorResult.put("detail", "气象服务暂时不可用");
            return errorResult;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("location", Arrays.asList(longitude, latitude));
        result.put("data", weatherData);
        return result;
    }

    /**
     * 批量按经纬度获取实时气象（顺序与请求一致）。
     * 对经度、纬度四舍五入到小数 4 位做去重，相同网格点只调用一次和风接口，降低 QPS。
     */
    public Map<String, Object> getWeatherByCoordinatesBatch(List<com.bluesky.dto.WeatherBatchRequest.Coordinate> coordinates) {
        List<Map<String, Object>> series = new ArrayList<>();
        // 插入顺序即请求顺序，便于 LRU；key 为 4 位小数网格去重
        Map<String, Map<String, Object>> cache = new HashMap<>();

        for (com.bluesky.dto.WeatherBatchRequest.Coordinate c : coordinates) {
            if (c == null || c.getLng() == null || c.getLat() == null) {
                Map<String, Object> bad = new HashMap<>();
                bad.put("error", true);
                bad.put("message", "坐标不能为空");
                series.add(bad);
                continue;
            }
            double lng = c.getLng();
            double lat = c.getLat();
            String key = String.format(java.util.Locale.US, "%.4f,%.4f", lng, lat);

            Map<String, Object> cell = cache.get(key);
            if (cell == null) {
                cell = getWeatherByCoordinates(lng, lat);
                cache.put(key, cell);
            }

            Map<String, Object> item = new HashMap<>();
            item.put("lng", lng);
            item.put("lat", lat);
            if (Boolean.TRUE.equals(cell.get("error"))) {
                item.put("error", true);
                item.put("message", cell.get("message"));
            } else {
                item.put("data", cell.get("data"));
            }
            series.add(item);
        }

        Map<String, Object> out = new HashMap<>();
        out.put("updateTime", LocalDateTime.now().toString());
        out.put("count", series.size());
        out.put("uniqueQueries", cache.size());
        out.put("series", series);
        return out;
    }

    /**
     * 调用和风天气API
     */
    private Map<String, Object> callQWeatherAPI(double longitude, double latitude) {
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                RestTemplate restTemplate = createRestTemplateWithTimeout();

                String url = String.format(
                    "https://m73yfr9h37.re.qweatherapi.com/v7/weather/now?location=%f,%f",
                        longitude, latitude);

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-QW-Api-Key", "7226910f80e3434aa26b1b55938b6f58");
                headers.set("Accept-Encoding", "gzip");

                HttpEntity<String> entity = new HttpEntity<>(headers);

                log.info("第{}次尝试调用和风天气API: {}", retryCount + 1, url);
                ResponseEntity<byte[]> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);

                if (responseEntity.getStatusCode().is2xxSuccessful()) {
                    byte[] responseBodyBytes = responseEntity.getBody();
                    if (responseBodyBytes != null) {
                        boolean isGzipped = false;
                        if (responseBodyBytes.length >= 2) {
                            int magic = ((responseBodyBytes[0] & 0xff) << 8) | (responseBodyBytes[1] & 0xff);
                            isGzipped = (magic == GZIPInputStream.GZIP_MAGIC);
                        }

                        String responseBody;
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

                        ObjectMapper objectMapper = new ObjectMapper();
                        ObjectNode jsonResponse = objectMapper.readValue(responseBody, ObjectNode.class);

                        String code = jsonResponse.get("code").asText();
                        if ("200".equals(code)) {
                            ObjectNode now = (ObjectNode) jsonResponse.get("now");
                            Map<String, Object> weatherData = new HashMap<>();

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

                            // 计算颠簸指数（基于风速和能见度）
                            double windSpeedKmH = Double.parseDouble(now.get("windSpeed").asText());
                            double visKm = Double.parseDouble(now.get("vis").asText());
                            double turbulenceIndex = calculateTurbulenceIndex(windSpeedKmH, visKm);
                            weatherData.put("turbulenceIndex", String.format("%.2f", turbulenceIndex));

                            weatherData.put("windShearLevel", "low");
                            weatherData.put("stabilityIndex", "C");

                            log.info("成功获取和风天气数据");
                            return weatherData;
                        } else {
                            log.warn("和风天气API返回错误码: {}", code);
                        }
                    }
                }
            } catch (Exception e) {
                retryCount++;
                log.error("第{}次调用和风天气API失败: {}", retryCount, e.getMessage());

                if (retryCount >= maxRetries) {
                    log.error("已达到最大重试次数({}次)，放弃调用和风天气API", maxRetries);
                    break;
                }

                try {
                    long waitTime = 1000L * retryCount;
                    log.info("等待{}毫秒后进行第{}次重试...", waitTime, retryCount + 1);
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("重试等待被中断");
                    break;
                }
            }
        }

        return null;
    }

    /**
     * 计算颠簸指数（0-1之间）
     * 基于风速和能见度综合评估
     * 
     * @param windSpeedKmH 风速（km/h）
     * @param visKm 能见度（km）
     * @return 颠簸指数（0=平稳，1=强烈颠簸）
     */
    private double calculateTurbulenceIndex(double windSpeedKmH, double visKm) {
        // 风速影响因子（风速越大，颠簸可能性越高）
        // 和风天气返回的风速是 km/h，转换为 m/s 进行计算
        double windSpeedMs = windSpeedKmH / 3.6;
        double windFactor = Math.min(1.0, Math.max(0.0, (windSpeedMs - 6) / 10));
        
        // 能见度影响因子（能见度越低，颠簸可能性越高）
        double visFactor = 0.0;
        if (visKm < 1) {
            visFactor = 0.8;
        } else if (visKm < 3) {
            visFactor = 0.4;
        } else if (visKm < 5) {
            visFactor = 0.2;
        }
        
        // 综合颠簸指数
        double turbulenceIndex = (windFactor * 0.7 + visFactor * 0.3);
        
        return Math.round(turbulenceIndex * 100.0) / 100.0;
    }

    private RestTemplate createRestTemplateWithTimeout() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }

    private LocalDateTime parseRequestTime(String time) {
        if (time == null || time.trim().isEmpty()) {
            return null;
        }

        String normalized = time.trim();
        try {
            return LocalDateTime.parse(normalized);
        } catch (Exception ignored) {
        }

        try {
            return OffsetDateTime.parse(normalized).toLocalDateTime();
        } catch (Exception ignored) {
        }

        log.warn("Failed to parse request time: {}", time);
        return null;
    }

    private int resolveCitywideGridSize() {
        return 100;
    }

    private List<IdwSamplePoint> toIdwSamples(List<Map<String, Object>> rawPoints) {
        if (rawPoints == null || rawPoints.isEmpty()) {
            return Collections.emptyList();
        }

        List<IdwSamplePoint> samples = new ArrayList<>(rawPoints.size());
        for (Map<String, Object> point : rawPoints) {
            Object lnglatObj = point.get("lnglat");
            if (!(lnglatObj instanceof List<?> lnglat) || lnglat.size() < 2) {
                continue;
            }

            Double lng = toDouble(lnglat.get(0));
            Double lat = toDouble(lnglat.get(1));
            Double value = toDouble(point.get("value"));
            if (lng == null || lat == null || value == null) {
                continue;
            }
            samples.add(new IdwSamplePoint(lng, lat, value));
        }

        return limitSampleCount(samples);
    }

    private List<IdwSamplePoint> limitSampleCount(List<IdwSamplePoint> samples) {
        if (samples == null || samples.isEmpty()) {
            return Collections.emptyList();
        }

        if (samples.size() <= CITYWIDE_MAX_SOURCE_POINTS) {
            return samples;
        }

        int step = (int) Math.ceil(samples.size() / (double) CITYWIDE_MAX_SOURCE_POINTS);
        List<IdwSamplePoint> downSampled = new ArrayList<>(CITYWIDE_MAX_SOURCE_POINTS);
        for (int i = 0; i < samples.size(); i += step) {
            downSampled.add(samples.get(i));
        }
        return downSampled;
    }

    private List<IdwSamplePoint> buildEnhancedCitywideSamples(List<IdwSamplePoint> rawSamples, double[] bbox, int gridSize) {
        if (rawSamples == null || rawSamples.isEmpty() || bbox == null || bbox.length < 4) {
            return Collections.emptyList();
        }

        if (rawSamples.size() >= CITYWIDE_SYNTHETIC_THRESHOLD) {
            return rawSamples;
        }

        double minLng = bbox[0];
        double minLat = bbox[1];
        double maxLng = bbox[2];
        double maxLat = bbox[3];

        double lngStep = (maxLng - minLng) / Math.max(1d, gridSize - 1d);
        double latStep = (maxLat - minLat) / Math.max(1d, gridSize - 1d);
        double spreadLng = Math.max(lngStep * 1.8d, (maxLng - minLng) / 120d);
        double spreadLat = Math.max(latStep * 1.8d, (maxLat - minLat) / 120d);

        int syntheticPerSample;
        if (rawSamples.size() <= 150) {
            syntheticPerSample = 8;
        } else if (rawSamples.size() <= 500) {
            syntheticPerSample = 6;
        } else {
            syntheticPerSample = 4;
        }

        List<IdwSamplePoint> enhanced = new ArrayList<>(rawSamples.size() * (syntheticPerSample + 1));
        for (IdwSamplePoint sample : rawSamples) {
            enhanced.add(sample);
            for (int i = 0; i < syntheticPerSample; i++) {
                double angle = 2d * Math.PI * (i / (double) syntheticPerSample);
                double lng = sample.lng + Math.cos(angle) * spreadLng;
                double lat = sample.lat + Math.sin(angle) * spreadLat;

                lng = clampValue(lng, minLng, maxLng);
                lat = clampValue(lat, minLat, maxLat);

                double ringDecay = 0.68d
                        + 0.18d * (0.5d + 0.5d * Math.sin(sample.lng * 11.3d + sample.lat * 7.7d + i));
                double value = clampValue(sample.value * ringDecay, 0d, 100d);
                enhanced.add(new IdwSamplePoint(lng, lat, value));
            }
        }

        return limitSampleCount(enhanced);
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private double interpolateByIdw(double lng, double lat, List<IdwSamplePoint> samples) {
        return interpolateByIdw(lng, lat, samples, IDW_NEIGHBOR_LIMIT, IDW_POWER);
    }

    private double interpolateByIdw(double lng, double lat, List<IdwSamplePoint> samples, int neighborLimit, double power) {
        if (samples.isEmpty()) {
            return 0d;
        }

        PriorityQueue<double[]> nearest = new PriorityQueue<>((a, b) -> Double.compare(b[0], a[0]));
        for (IdwSamplePoint sample : samples) {
            double dLng = lng - sample.lng;
            double dLat = lat - sample.lat;
            double d2 = dLng * dLng + dLat * dLat;

            if (d2 < 1e-12) {
                return sample.value;
            }

            double[] entry = new double[] { d2, sample.value };
            if (nearest.size() < neighborLimit) {
                nearest.offer(entry);
            } else if (d2 < nearest.peek()[0]) {
                nearest.poll();
                nearest.offer(entry);
            }
        }

        double weightedValue = 0d;
        double weightSum = 0d;
        for (double[] neighbor : nearest) {
            double d = Math.sqrt(neighbor[0]);
            double weight = 1d / Math.pow(d, power);
            weightedValue += neighbor[1] * weight;
            weightSum += weight;
        }

        if (weightSum <= 0d) {
            return 0d;
        }
        return weightedValue / weightSum;
    }

    private double interpolateByGaussianKernel(double lng, double lat, List<IdwSamplePoint> samples, double sigma,
            int neighborLimit) {
        if (samples.isEmpty()) {
            return 0d;
        }
        if (sigma <= 1e-12) {
            return interpolateByIdw(lng, lat, samples);
        }

        PriorityQueue<double[]> nearest = new PriorityQueue<>((a, b) -> Double.compare(b[0], a[0]));
        for (IdwSamplePoint sample : samples) {
            double dLng = lng - sample.lng;
            double dLat = lat - sample.lat;
            double d2 = dLng * dLng + dLat * dLat;
            if (d2 < 1e-12) {
                return sample.value;
            }

            double[] entry = new double[] { d2, sample.value };
            if (nearest.size() < neighborLimit) {
                nearest.offer(entry);
            } else if (d2 < nearest.peek()[0]) {
                nearest.poll();
                nearest.offer(entry);
            }
        }

        double sigma2 = sigma * sigma;
        double weightedValue = 0d;
        double weightSum = 0d;
        for (double[] neighbor : nearest) {
            double weight = Math.exp(-neighbor[0] / (2d * sigma2));
            weightedValue += neighbor[1] * weight;
            weightSum += weight;
        }

        if (weightSum <= 0d) {
            return 0d;
        }
        return weightedValue / weightSum;
    }

    private double resolveGaussianSigma(double[] bbox, int gridSize) {
        double minLng = bbox[0];
        double minLat = bbox[1];
        double maxLng = bbox[2];
        double maxLat = bbox[3];
        double lngStep = (maxLng - minLng) / Math.max(1d, gridSize - 1d);
        double latStep = (maxLat - minLat) / Math.max(1d, gridSize - 1d);
        double baseStep = Math.sqrt(lngStep * lngStep + latStep * latStep);
        return Math.max(baseStep * 3.2d, 1e-5);
    }

    private double[][] smoothGrid(double[][] source, int passes) {
        if (source == null || source.length == 0 || source[0].length == 0 || passes <= 0) {
            return source;
        }

        int rows = source.length;
        int cols = source[0].length;
        double[][] current = source;
        int[][] kernel = new int[][] {
                { 1, 2, 1 },
                { 2, 4, 2 },
                { 1, 2, 1 }
        };

        for (int pass = 0; pass < passes; pass++) {
            double[][] next = new double[rows][cols];
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    double weightedSum = 0d;
                    double dynamicWeight = 0d;
                    for (int ky = -1; ky <= 1; ky++) {
                        int py = clampIndex(y + ky, 0, rows - 1);
                        for (int kx = -1; kx <= 1; kx++) {
                            int px = clampIndex(x + kx, 0, cols - 1);
                            int weight = kernel[ky + 1][kx + 1];
                            weightedSum += current[py][px] * weight;
                            dynamicWeight += weight;
                        }
                    }
                    next[y][x] = dynamicWeight > 0d ? (weightedSum / dynamicWeight) : current[y][x];
                }
            }
            current = next;
        }

        return current;
    }

    private double[][] normalizeByQuantileStretch(double[][] grid, double lowQuantile, double highQuantile) {
        if (grid == null || grid.length == 0 || grid[0].length == 0) {
            return grid;
        }

        int rows = grid.length;
        int cols = grid[0].length;
        List<Double> values = new ArrayList<>(rows * cols);
        for (double[] row : grid) {
            for (double value : row) {
                values.add(value);
            }
        }

        double low = percentile(values, lowQuantile);
        double high = percentile(values, highQuantile);
        double span = high - low;

        double[][] normalized = new double[rows][cols];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                double value = grid[y][x];
                if (span <= 1e-6) {
                    normalized[y][x] = clampValue(value, 0d, 100d);
                } else {
                    double stretched = (value - low) / span * 100d;
                    normalized[y][x] = clampValue(stretched, 0d, 100d);
                }
            }
        }
        return normalized;
    }

    private double percentile(List<Double> values, double quantile) {
        if (values == null || values.isEmpty()) {
            return 0d;
        }

        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);

        double q = clampValue(quantile, 0d, 1d);
        double index = q * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sorted.get(lower);
        }

        double fraction = index - lower;
        return sorted.get(lower) * (1d - fraction) + sorted.get(upper) * fraction;
    }

    private int clampIndex(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clampValue(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double deterministicNoise(int x, int y) {
        long n = x * 374761393L + y * 668265263L + 0x9E3779B97F4A7C15L;
        n = (n ^ (n >> 13)) * 1274126177L;
        n = n ^ (n >> 16);
        double unit = (n & 0x7fffffffL) / (double) 0x7fffffffL;
        return unit * 2d - 1d;
    }

    private double normalizeRiskTo100(double value) {
        if (value <= 5d) {
            return clampValue(value * 20d, 0d, 100d);
        }
        return clampValue(value, 0d, 100d);
    }

    private List<Map<String, Object>> buildCitywideHeatmapFast(List<Map<String, Object>> sourcePoints, double[] bbox) {
        if (bbox == null || sourcePoints == null || sourcePoints.isEmpty()) {
            return Collections.emptyList();
        }

        double minLng = bbox[0];
        double minLat = bbox[1];
        double maxLng = bbox[2];
        double maxLat = bbox[3];
        if (maxLng <= minLng || maxLat <= minLat) {
            return Collections.emptyList();
        }

        int gridSize = CITYWIDE_FAST_GRID_SIZE;
        double[][] valueSum = new double[gridSize][gridSize];
        int[][] hitCount = new int[gridSize][gridSize];

        for (Map<String, Object> point : sourcePoints) {
            Object lnglatObj = point.get("lnglat");
            if (!(lnglatObj instanceof List<?> lnglat) || lnglat.size() < 2) {
                continue;
            }

            Double lng = toDouble(lnglat.get(0));
            Double lat = toDouble(lnglat.get(1));
            Double value = toDouble(point.get("value"));
            if (lng == null || lat == null || value == null) {
                continue;
            }
            if (lng < minLng || lng > maxLng || lat < minLat || lat > maxLat) {
                continue;
            }

            int gx = clampIndex((int) Math.floor((lng - minLng) / (maxLng - minLng) * (gridSize - 1)), 0, gridSize - 1);
            int gy = clampIndex((int) Math.floor((lat - minLat) / (maxLat - minLat) * (gridSize - 1)), 0, gridSize - 1);

            valueSum[gy][gx] += normalizeRiskTo100(value);
            hitCount[gy][gx] += 1;
        }

        List<Map<String, Object>> points = new ArrayList<>(gridSize * gridSize);
        for (int gy = 0; gy < gridSize; gy++) {
            double lat = minLat + (maxLat - minLat) * (gy / (double) (gridSize - 1));
            for (int gx = 0; gx < gridSize; gx++) {
                int count = hitCount[gy][gx];
                if (count <= 0) {
                    continue;
                }

                double lng = minLng + (maxLng - minLng) * (gx / (double) (gridSize - 1));
                double value = valueSum[gy][gx] / count;

                Map<String, Object> outputPoint = new HashMap<>();
                outputPoint.put("lnglat", Arrays.asList(lng, lat));
                outputPoint.put("value", clampValue(value, 0d, 100d));
                outputPoint.put("gridX", gx);
                outputPoint.put("gridY", gy);
                outputPoint.put("gridSize", gridSize);
                outputPoint.put("bboxMinLng", minLng);
                outputPoint.put("bboxMinLat", minLat);
                outputPoint.put("bboxMaxLng", maxLng);
                outputPoint.put("bboxMaxLat", maxLat);
                points.add(outputPoint);
            }
        }

        if (points.size() <= CITYWIDE_FAST_MAX_POINTS) {
            return points;
        }

        int step = (int) Math.ceil(points.size() / (double) CITYWIDE_FAST_MAX_POINTS);
        List<Map<String, Object>> downSampled = new ArrayList<>(CITYWIDE_FAST_MAX_POINTS);
        for (int i = 0; i < points.size(); i += step) {
            downSampled.add(points.get(i));
        }
        return downSampled;
    }

    private List<Map<String, Object>> interpolateCitywideHeatmap(List<Map<String, Object>> sourcePoints, double[] bbox) {
        if (bbox == null || sourcePoints == null || sourcePoints.isEmpty()) {
            return Collections.emptyList();
        }

        List<IdwSamplePoint> samples = toIdwSamples(sourcePoints);
        if (samples.isEmpty()) {
            return Collections.emptyList();
        }

        int gridSize = resolveCitywideGridSize();
        List<IdwSamplePoint> enhancedSamples = buildEnhancedCitywideSamples(samples, bbox, gridSize);

        double minLng = bbox[0];
        double minLat = bbox[1];
        double maxLng = bbox[2];
        double maxLat = bbox[3];
        double gaussianSigma = resolveGaussianSigma(bbox, gridSize);

        double[][] rawGrid = new double[gridSize][gridSize];
        double[][] gaussianGrid = new double[gridSize][gridSize];

        for (int gy = 0; gy < gridSize; gy++) {
            double lat = minLat + (maxLat - minLat) * (gy / (double) (gridSize - 1));
            for (int gx = 0; gx < gridSize; gx++) {
                double lng = minLng + (maxLng - minLng) * (gx / (double) (gridSize - 1));
                rawGrid[gy][gx] = interpolateByIdw(lng, lat, samples);
                gaussianGrid[gy][gx] = interpolateByGaussianKernel(lng, lat, enhancedSamples, gaussianSigma,
                        CITYWIDE_GAUSSIAN_NEIGHBOR_LIMIT);
            }
        }

        double[][] smoothedGrid = smoothGrid(gaussianGrid, 2);
        List<Double> smoothedValues = new ArrayList<>(gridSize * gridSize);
        for (double[] row : smoothedGrid) {
            for (double value : row) {
                smoothedValues.add(value);
            }
        }
        double noiseAmplitude = Math.max(0.4d, (percentile(smoothedValues, 0.9d) - percentile(smoothedValues, 0.1d)) * 0.06d);

        double[][] blendedGrid = new double[gridSize][gridSize];
        for (int gy = 0; gy < gridSize; gy++) {
            for (int gx = 0; gx < gridSize; gx++) {
                double noise = deterministicNoise(gx, gy) * noiseAmplitude;
                double blended = rawGrid[gy][gx] * 0.70d + smoothedGrid[gy][gx] * 0.25d + noise;
                blendedGrid[gy][gx] = clampValue(blended, 0d, 100d);
            }
        }

        double[][] normalizedGrid = normalizeByQuantileStretch(blendedGrid, 0.10d, 0.95d);

        List<Map<String, Object>> points = new ArrayList<>(gridSize * gridSize);
        for (int gy = 0; gy < gridSize; gy++) {
            double lat = minLat + (maxLat - minLat) * (gy / (double) (gridSize - 1));
            for (int gx = 0; gx < gridSize; gx++) {
                double lng = minLng + (maxLng - minLng) * (gx / (double) (gridSize - 1));
                Map<String, Object> point = new HashMap<>();
                point.put("lnglat", Arrays.asList(lng, lat));
                point.put("value", normalizedGrid[gy][gx]);
                point.put("gridX", gx);
                point.put("gridY", gy);
                point.put("gridSize", gridSize);
                point.put("bboxMinLng", minLng);
                point.put("bboxMinLat", minLat);
                point.put("bboxMaxLng", maxLng);
                point.put("bboxMaxLat", maxLat);
                points.add(point);
            }
        }

        return points;
    }

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

    public Map<String, Object> getWeatherForecastTrend(String pointId) {
        try {
            // 根据 pointId 获取监测点坐标
            LandingPoint point = landingPointService.getEntity(pointId);
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

            List<WeatherForecast> todayForecasts = List.of();
            try {
                todayForecasts = weatherForecastMapper.selectList(
                        new LambdaQueryWrapper<WeatherForecast>()
                                .eq(WeatherForecast::getPointId, pointId)
                                .ge(WeatherForecast::getForecastTime, startOfDay)
                                .le(WeatherForecast::getForecastTime, endOfDay)
                                .orderByAsc(WeatherForecast::getForecastTime));
            } catch (Exception dbEx) {
                log.warn("weather_forecast 表不可用，改走 Open-Meteo: {}", dbEx.getMessage());
            }

            Map<String, Object> weatherData;

            // 2. 如果数据库有今天的预报数据，直接使用
            if (!todayForecasts.isEmpty()) {
                weatherData = convertForecastsToResponse(todayForecasts);
            } else {
                double latitude = point.getLatitude().doubleValue();
                double longitude = point.getLongitude().doubleValue();

                // 3. 数据库没有今天的预报数据，调用 Open-Meteo API
                weatherData = callOpenMeteoAPI(buildOpenMeteoForecastUri(latitude, longitude));

                try {
                    saveForecastDataToDatabase(pointId, weatherData);
                } catch (Exception persistEx) {
                    log.warn("weather_forecast 持久化跳过: {}", persistEx.getMessage());
                }
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
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("time", List.of());
            empty.put("temperature_2m", List.of());
            empty.put("precipitation", List.of());
            empty.put("visibility", List.of());
            empty.put("wind_speed_10m", List.of());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("updateTime", LocalDateTime.now().toString());
            result.put("pointId", pointId);
            result.put("data", empty);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 过滤出从当前 15 分钟档开始的 3 小时预报（上海时区，含 time_iso 供前端对齐）
     */
    private Map<String, Object> filterNext3HoursData(Map<String, Object> weatherData) {
        Map<String, Object> filteredData = new LinkedHashMap<>();

        List<String> allTimes = castStringList(weatherData.get("time"));
        List<String> allTimeIso = castStringList(weatherData.get("time_iso"));
        List<Double> allTemperatures = castDoubleList(weatherData.get("temperature_2m"));
        List<Double> allPrecipitation = castDoubleList(weatherData.get("precipitation"));
        List<Double> allWindSpeed = castDoubleList(weatherData.get("wind_speed_10m"));
        List<Integer> allVisibility = castIntegerList(weatherData.get("visibility"));
        List<Integer> allWeatherCode = castIntegerList(weatherData.get("weather_code"));
        List<String> allWeatherText = castStringList(weatherData.get("weather_text"));

        if (allTimes == null || allTimes.isEmpty()) {
            return filteredData;
        }

        ZoneId zone = TimeBucketUtil.ZONE;
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime windowStart = floorTo15Minutes(now);
        ZonedDateTime windowEnd = now.plusHours(3);

        List<String> filteredTimes = new ArrayList<>();
        List<String> filteredTimeIso = new ArrayList<>();
        List<Double> filteredTemperatures = new ArrayList<>();
        List<Double> filteredPrecipitation = new ArrayList<>();
        List<Double> filteredWindSpeed = new ArrayList<>();
        List<Integer> filteredVisibility = new ArrayList<>();
        List<Integer> filteredWeatherCode = new ArrayList<>();
        List<String> filteredWeatherText = new ArrayList<>();

        for (int i = 0; i < allTimes.size(); i++) {
            ZonedDateTime pointTime = parseForecastPointTime(allTimes.get(i), allTimeIso, i, zone);
            if (pointTime == null) {
                continue;
            }
            if (pointTime.isBefore(windowStart) || pointTime.isAfter(windowEnd)) {
                continue;
            }
            if (pointTime.getMinute() % 15 != 0) {
                continue;
            }

            filteredTimes.add(formatHm(pointTime));
            filteredTimeIso.add(pointTime.toOffsetDateTime().toString());
            appendForecastValue(allTemperatures, i, filteredTemperatures, 0d);
            appendForecastValue(allPrecipitation, i, filteredPrecipitation, 0d);
            appendForecastValue(allWindSpeed, i, filteredWindSpeed, 0d);
            appendForecastValue(allVisibility, i, filteredVisibility, 0);
            appendForecastValue(allWeatherCode, i, filteredWeatherCode, 0);
            if (allWeatherText != null && i < allWeatherText.size()) {
                filteredWeatherText.add(allWeatherText.get(i));
            }
        }

        prependNowPointIfNeeded(
                filteredTimes, filteredTimeIso, filteredTemperatures, filteredPrecipitation,
                filteredWindSpeed, filteredVisibility, filteredWeatherCode, filteredWeatherText, now);

        filteredData.put("time", filteredTimes);
        filteredData.put("time_iso", filteredTimeIso);
        filteredData.put("temperature_2m", filteredTemperatures);
        filteredData.put("precipitation", filteredPrecipitation);
        filteredData.put("wind_speed_10m", filteredWindSpeed);
        filteredData.put("visibility", filteredVisibility);
        filteredData.put("weather_code", filteredWeatherCode);
        filteredData.put("weather_text", filteredWeatherText);
        return filteredData;
    }

    private ZonedDateTime floorTo15Minutes(ZonedDateTime time) {
        int flooredMinute = (time.getMinute() / TimeBucketUtil.BUCKET_MINUTES) * TimeBucketUtil.BUCKET_MINUTES;
        return time.withMinute(flooredMinute).withSecond(0).withNano(0);
    }

    private ZonedDateTime parseForecastPointTime(String hm, List<String> isoList, int index, ZoneId zone) {
        if (isoList != null && index < isoList.size() && isoList.get(index) != null && !isoList.get(index).isBlank()) {
            try {
                String raw = isoList.get(index).trim().replace(' ', '+');
                if (raw.contains("+") || raw.endsWith("Z")) {
                    return OffsetDateTime.parse(raw).atZoneSameInstant(zone);
                }
                return LocalDateTime.parse(raw).atZone(zone);
            } catch (Exception ignored) {
                // fallback to HH:mm
            }
        }
        if (hm == null || !hm.contains(":")) {
            return null;
        }
        String[] parts = hm.split(":");
        if (parts.length != 2) {
            return null;
        }
        LocalDate date = LocalDate.now(zone);
        return ZonedDateTime.of(
                date,
                LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])),
                zone);
    }

    private String formatHm(ZonedDateTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    private void prependNowPointIfNeeded(List<String> times,
                                         List<String> timeIso,
                                         List<Double> temperatures,
                                         List<Double> precipitations,
                                         List<Double> windSpeeds,
                                         List<Integer> visibilities,
                                         List<Integer> weatherCodes,
                                         List<String> weatherTexts,
                                         ZonedDateTime now) {
        if (times.isEmpty()) {
            return;
        }
        ZonedDateTime first;
        try {
            first = OffsetDateTime.parse(timeIso.get(0)).atZoneSameInstant(now.getZone());
        } catch (Exception ex) {
            return;
        }
        if (!first.isAfter(now)) {
            return;
        }
        times.add(0, formatHm(now));
        timeIso.add(0, now.toOffsetDateTime().toString());
        temperatures.add(0, temperatures.get(0));
        precipitations.add(0, precipitations.get(0));
        windSpeeds.add(0, windSpeeds.get(0));
        visibilities.add(0, visibilities.get(0));
        weatherCodes.add(0, weatherCodes.get(0));
        if (!weatherTexts.isEmpty()) {
            weatherTexts.add(0, weatherTexts.get(0));
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object value) {
        if (value instanceof List<?> list) {
            return (List<String>) list;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Double> castDoubleList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Double>) list;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Integer> castIntegerList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Integer>) list;
        }
        return null;
    }

    private <T> void appendForecastValue(List<T> source, int index, List<T> target, T fallback) {
        if (source != null && index < source.size() && source.get(index) != null) {
            target.add(source.get(index));
        } else {
            target.add(fallback);
        }
    }

    /**
     * 构建 Open-Meteo 15 分钟预报请求 URI（与 V1 一致，不传 timezone，使用 API 默认 GMT）
     */
    private URI buildOpenMeteoForecastUri(double lat, double lng) {
        return UriComponentsBuilder.fromHttpUrl("https://api.open-meteo.com/v1/forecast")
                .queryParam("latitude", lat)
                .queryParam("longitude", lng)
                .queryParam("timezone", TimeBucketUtil.ZONE.getId())
                .queryParam("minutely_15", "temperature_2m,wind_speed_10m,visibility,precipitation,weather_code")
                .queryParam("forecast_days", 1)
                .build()
                .encode()
                .toUri();
    }

    /**
     * 调用 Open-Meteo API
     */
    private Map<String, Object> callOpenMeteoAPI(URI apiUri) {
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<String> responseEntity = restTemplate.getForEntity(apiUri, String.class);
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
                    List<String> timeIso = new ArrayList<>();
                    for (int i = 0; i < timeArray.size(); i++) {
                        String timeStr = timeArray.get(i).asText();
                        // 解析 ISO 8601 时间格式
                        LocalDateTime time = LocalDateTime.parse(timeStr);
                        timeIso.add(timeStr);
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
                        // Open-Meteo 风速为 km/h，折线图展示 m/s
                        windSpeed.add(windArray.get(i).asDouble() / 3.6d);
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
                    minutely15.put("time_iso", timeIso);
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

        List<String> timeIso = new ArrayList<>();

        for (WeatherForecast forecast : forecasts) {
            LocalDateTime forecastTime = forecast.getForecastTime();
            times.add(String.format("%02d:%02d", forecastTime.getHour(), forecastTime.getMinute()));
            timeIso.add(forecastTime.atZone(TimeBucketUtil.ZONE).toOffsetDateTime().toString());
            temperature.add(forecast.getTemperature() != null ? forecast.getTemperature().doubleValue() : 0.0);
            precipitation.add(forecast.getPrecipitation() != null ? forecast.getPrecipitation().doubleValue() : 0.0);
            windSpeed.add(forecast.getWindSpeed() != null ? forecast.getWindSpeed().doubleValue() : 0.0);
            visibility.add(forecast.getVisibility() != null ? forecast.getVisibility().intValue() : 0);
            weatherCode.add(forecast.getWeatherCode());
            weatherText.add(forecast.getWeatherText() != null ? forecast.getWeatherText() : "鏈煡");
        }

        minutely15.put("time", times);
        minutely15.put("time_iso", timeIso);
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
    LocalDateTime startOfDay = today.atStartOfDay();
    LocalDateTime endOfDay = today.atTime(23, 59, 59);

    // 先删除今天的所有预报数据，避免重复
    weatherForecastMapper.delete(
            new LambdaQueryWrapper<WeatherForecast>()
                    .eq(WeatherForecast::getPointId, pointId)
                    .ge(WeatherForecast::getForecastTime, startOfDay)
                    .le(WeatherForecast::getForecastTime, endOfDay)
    );

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
                weatherInfo.put("text", "鏈煡");
        }

        return weatherInfo;
    }

    public Map<String, Object> getCitywideHeatmap() {
        Region region = regionService.getEntity(regionService.getDefault().getRegionId());
        var envelope = regionBoundaryService.resolveEnvelope(region);
        String citywideBounds = String.format("[%s,%s,%s,%s]",
                envelope.west(), envelope.south(),
                envelope.east(), envelope.north());

        List<Map<String, Object>> sourcePoints = generateCommonHeatmapData(citywideBounds, null, null);
        double[] bbox = parseBoundingBox(citywideBounds);
        List<Map<String, Object>> interpolatedPoints = buildCitywideHeatmapFast(sourcePoints, bbox);

        Map<String, Object> result = new HashMap<>();
        result.put("data", interpolatedPoints);
        return result;
    }

}


