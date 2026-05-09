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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
import java.time.OffsetDateTime;
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
    private static final int CITYWIDE_MAX_SOURCE_POINTS = 15000;
    private static final int IDW_NEIGHBOR_LIMIT = 20;
    private static final double IDW_POWER = 2.0d;
    private static final int CITYWIDE_SYNTHETIC_THRESHOLD = 1200;
    private static final int CITYWIDE_GAUSSIAN_NEIGHBOR_LIMIT = 30;
    private static final int CITYWIDE_FAST_GRID_SIZE = 70;
    private static final int CITYWIDE_FAST_MAX_POINTS = 12000;

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

            weatherRealtimeMapper.insert(newRecord);

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
    private List<Map<String, Object>> generateCommonHeatmapData(String bounds, List<String> pointIds, LocalDateTime targetTime) {
        double[] bbox = parseBoundingBox(bounds);
        if (bbox == null) {
            return Collections.emptyList();
        }

        List<MonitoringPoint> monitorPoints = new ArrayList<>();
        if (pointIds != null && !pointIds.isEmpty()) {
            for (String pointId : pointIds) {
                if (pointId == null || pointId.isEmpty()) {
                    continue;
                }
                MonitoringPoint point = monitoringPointService.getById(pointId);
                if (point != null) {
                    monitorPoints.add(point);
                }
            }
        } else {
            monitorPoints.addAll(monitoringPointService.getAll());
        }

        List<Map<String, Object>> points = new ArrayList<>();
        for (MonitoringPoint point : monitorPoints) {
            points.addAll(buildHeatmapPointsForMonitor(point, targetTime));
        }
        return points;
    }

    private List<Map<String, Object>> buildHeatmapPointsForMonitor(MonitoringPoint monitor, LocalDateTime targetTime) {
        if (monitor == null || monitor.getBboxMinLng() == null || monitor.getBboxMinLat() == null
                || monitor.getBboxMaxLng() == null || monitor.getBboxMaxLat() == null) {
            return Collections.emptyList();
        }

        double pointMinLng = monitor.getBboxMinLng().doubleValue();
        double pointMinLat = monitor.getBboxMinLat().doubleValue();
        double pointMaxLng = monitor.getBboxMaxLng().doubleValue();
        double pointMaxLat = monitor.getBboxMaxLat().doubleValue();

        List<MicroscaleWeather> weatherList = getWeatherPointsFromDatabase(monitor.getId(), targetTime);
        List<Map<String, Object>> points = new ArrayList<>(weatherList.size());

        for (MicroscaleWeather weather : weatherList) {
            int gridSize = weather.getGridSize() != null ? weather.getGridSize() : 10;
            if (gridSize <= 1) {
                continue;
            }

            if (weather.getGridX() == null || weather.getGridY() == null || weather.getRiskLevel() == null) {
                continue;
            }

            double gridX = weather.getGridX().doubleValue();
            double gridY = weather.getGridY().doubleValue();
            double lng = pointMinLng + (pointMaxLng - pointMinLng) * (gridX / (double) (gridSize - 1));
            double lat = pointMinLat + (pointMaxLat - pointMinLat) * (gridY / (double) (gridSize - 1));

            Map<String, Object> point = new HashMap<>();
            point.put("lnglat", Arrays.asList(lng, lat));
            point.put("value", weather.getRiskLevel());
            if (weather.getReason() != null && !weather.getReason().isBlank()) {
                point.put("reason", weather.getReason());
            }
            if (weather.getWindSpeed() != null) {
                point.put("windSpeed", weather.getWindSpeed());
            }
            if (weather.getWindShear() != null) {
                point.put("windShear", weather.getWindShear());
            }
            if (weather.getTurbulence() != null) {
                point.put("turbulence", weather.getTurbulence());
            }
            point.put("gridX", gridX);
            point.put("gridY", gridY);
            point.put("gridSize", gridSize);
            point.put("bboxMinLng", pointMinLng);
            point.put("bboxMinLat", pointMinLat);
            point.put("bboxMaxLng", pointMaxLng);
            point.put("bboxMaxLat", pointMaxLat);
            points.add(point);
        }
        return points;
    }

    /**
     * Read one snapshot (same data_time) for a monitoring point.
     */
    private List<MicroscaleWeather> getWeatherPointsFromDatabase(String pointId, LocalDateTime targetTime) {
        if (pointId == null || pointId.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<MicroscaleWeather> latestQuery = new LambdaQueryWrapper<MicroscaleWeather>()
                .eq(MicroscaleWeather::getPointId, pointId);
        if (targetTime != null) {
            latestQuery.le(MicroscaleWeather::getDataTime, targetTime);
        }
        latestQuery.orderByDesc(MicroscaleWeather::getDataTime).last("LIMIT 1");

        MicroscaleWeather latest = microscaleWeatherMapper.selectOne(latestQuery);
        if (latest == null || latest.getDataTime() == null) {
            return Collections.emptyList();
        }

        return microscaleWeatherMapper.selectList(
                new LambdaQueryWrapper<MicroscaleWeather>()
                        .eq(MicroscaleWeather::getPointId, pointId)
                        .eq(MicroscaleWeather::getDataTime, latest.getDataTime())
                        .orderByAsc(MicroscaleWeather::getGridY)
                        .orderByAsc(MicroscaleWeather::getGridX));
    }

    /**
     * Build area-mode heatmap data.
     */
    public Map<String, Object> getWeatherHeatmapGeo(String bounds, String time, String pointId) {
        LocalDateTime targetTime = parseRequestTime(time);
        List<Map<String, Object>> points = generateCommonHeatmapData(bounds, Collections.singletonList(pointId), targetTime);

        Map<String, Object> result = new HashMap<>();
        result.put("data", points);
        return result;
    }

    // ==================== 瀹搞儱鍙块弬瑙勭《 ====================

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
                        "https://devapi.qweather.com/v7/weather/now?location=%f,%f",
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
            weatherText.add(forecast.getWeatherText() != null ? forecast.getWeatherText() : "鏈煡");
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
        RegionConfig.Bounds boundsConfig = regionConfig.getBounds();
        String citywideBounds = String.format("[%s,%s,%s,%s]",
                boundsConfig.getWest(), boundsConfig.getSouth(),
                boundsConfig.getEast(), boundsConfig.getNorth());

        List<Map<String, Object>> sourcePoints = generateCommonHeatmapData(citywideBounds, null, null);
        double[] bbox = parseBoundingBox(citywideBounds);
        List<Map<String, Object>> interpolatedPoints = buildCitywideHeatmapFast(sourcePoints, bbox);

        Map<String, Object> result = new HashMap<>();
        result.put("data", interpolatedPoints);
        return result;
    }

}


