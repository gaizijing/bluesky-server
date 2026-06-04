package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.common.ResultCode;
import com.bluesky.common.TemporalMeta;
import com.bluesky.entity.LandingPoint;
import com.bluesky.entity.VerticalProfile;
import com.bluesky.exception.BusinessException;
import com.bluesky.entity.WeatherGridCache;
import com.bluesky.mapper.VerticalProfileMapper;
import com.bluesky.scheduler.service.WeatherGridCacheService;
import com.bluesky.util.TimeBucketUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherQueryService {

    private final WeatherService weatherService;
    private final LandingPointService landingPointService;
    private final RegionService regionService;
    private final RiskService riskService;
    private final VerticalProfileMapper verticalProfileMapper;
    private final WeatherGridCacheService weatherGridCacheService;

    public Map<String, Object> queryPoint(double lng, double lat, String time, int heightM,
                                          String products, boolean includeRisk) {
        OffsetDateTime requested = TimeBucketUtil.parseOrNow(time);
        TemporalMeta meta = TimeBucketUtil.buildMeta(requested, TimeBucketUtil.now(), true);

        Map<String, Object> weather = weatherService.getWeatherByCoordinates(lng, lat);
        if (Boolean.TRUE.equals(weather.get("error"))) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    String.valueOf(weather.getOrDefault("message", "气象查询失败")));
        }

        Map<String, Object> factors = weatherService.flattenCoordinatesWeather(weather);
        Map<String, Object> payload = new LinkedHashMap<>(factors);
        payload.put("lng", lng);
        payload.put("lat", lat);
        payload.put("heightM", heightM);
        payload.put("requestedTime", meta.getRequestedTime());
        payload.put("bucketTime", meta.getBucketTime());
        payload.put("computedAt", meta.getComputedAt());
        payload.put("isStale", meta.getIsStale());

        if (includeRisk) {
            payload.put("risk", riskService.queryPoint(lng, lat, time, heightM));
        }
        return payload;
    }

    public Map<String, Object> queryGridField(String regionId, String product, String time, int heightM) {
        regionService.assertRegionAccess(regionId);
        OffsetDateTime requested = TimeBucketUtil.parseOrNow(time);
        LocalDateTime bucketLocal = TimeBucketUtil.toBucket(requested)
                .atZoneSameInstant(TimeBucketUtil.ZONE).toLocalDateTime();
        String productKey = normalizeGridProduct(product);

        var cacheOpt = weatherGridCacheService.findWithValidGrid(regionId, bucketLocal, heightM, productKey);
        if (cacheOpt.isPresent()) {
            WeatherGridCache cache = cacheOpt.get();
            List<Map<String, Object>> grid = weatherGridCacheService.toGridPoints(cache);
            TemporalMeta meta = TimeBucketUtil.buildMeta(requested, TimeBucketUtil.now(), false);
            boolean bucketMismatch = !cache.getBucketTime().equals(bucketLocal);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("regionId", regionId);
            payload.put("product", productKey);
            payload.put("heightM", heightM);
            payload.put("requestedTime", meta.getRequestedTime());
            payload.put("bucketTime", meta.getBucketTime());
            payload.put("cacheBucketTime", cache.getBucketTime());
            payload.put("cacheHit", true);
            payload.put("cacheStaleBucket", bucketMismatch);
            payload.put("computedAt", cache.getComputedAt() != null
                    ? cache.getComputedAt().atZone(TimeBucketUtil.ZONE).toOffsetDateTime()
                    : meta.getComputedAt());
            payload.put("isStale", bucketMismatch);
            payload.put("grid", grid);
            return payload;
        }

        log.warn("weather_grid_cache 无有效格点: region={} product={} heightM={} bucket={} — 重启后端触发 Flyway V8 或 POST /scheduler/recompute?regionId={}",
                regionId, productKey, heightM, bucketLocal, regionId);

        TemporalMeta meta = TimeBucketUtil.buildMeta(requested, TimeBucketUtil.now(), true);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("regionId", regionId);
        payload.put("product", productKey);
        payload.put("heightM", heightM);
        payload.put("requestedTime", meta.getRequestedTime());
        payload.put("bucketTime", meta.getBucketTime());
        payload.put("computedAt", meta.getComputedAt());
        payload.put("isStale", true);
        payload.put("grid", List.of());
        payload.put("cacheMiss", true);
        return payload;
    }

    public Map<String, Object> queryVerticalProfile(String landingPointId, String startTime,
                                                    String endTime, String heightLevelsM) {
        LandingPoint point = landingPointService.getEntity(landingPointId);
        regionService.assertRegionAccess(point.getRegionId());

        LocalDateTime from = startTime != null && !startTime.isBlank()
                ? TimeBucketUtil.parseOrNow(startTime).atZoneSameInstant(TimeBucketUtil.ZONE).toLocalDateTime()
                : LocalDateTime.now().minusHours(1);

        List<VerticalProfile> profiles = List.of();
        try {
            profiles = verticalProfileMapper.selectList(
                    new LambdaQueryWrapper<VerticalProfile>()
                            .eq(VerticalProfile::getPointId, landingPointId)
                            .ge(VerticalProfile::getDataTime, from)
                            .orderByAsc(VerticalProfile::getHeight));
        } catch (Exception ex) {
            // V2 库可能无 vertical_profile 表
        }

        if (profiles.isEmpty()) {
            profiles = buildSyntheticProfile(landingPointId, point);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("landingPointId", landingPointId);
        payload.put("regionId", point.getRegionId());
        payload.put("longitude", point.getLongitude());
        payload.put("latitude", point.getLatitude());
        payload.put("startTime", from);
        payload.put("heightLayers", profiles);
        payload.put("isStale", profiles.isEmpty());
        return payload;
    }

    public Map<String, Object> queryRealtime(String landingPointId, String time) {
        LandingPoint point = landingPointService.getEntity(landingPointId);
        regionService.assertRegionAccess(point.getRegionId());
        Map<String, Object> payload = queryPoint(
                point.getLongitude().doubleValue(),
                point.getLatitude().doubleValue(),
                time,
                100,
                null,
                false);
        payload.put("landingPointId", landingPointId);
        payload.put("regionId", point.getRegionId());
        return payload;
    }

    private List<VerticalProfile> buildSyntheticProfile(String landingPointId, LandingPoint point) {
        Map<String, Object> base = weatherService.getWeatherByCoordinates(
                point.getLongitude().doubleValue(), point.getLatitude().doubleValue());
        double wind = doubleVal(base.get("windSpeed"));
        double temp = doubleVal(base.get("temperature"));
        List<VerticalProfile> layers = new ArrayList<>();
        for (int h : List.of(50, 100, 200, 300, 500)) {
            VerticalProfile profile = new VerticalProfile();
            profile.setPointId(landingPointId);
            profile.setHeight(h);
            profile.setWindSpeed(BigDecimal.valueOf(wind + h * 0.01));
            profile.setTemperature(BigDecimal.valueOf(temp - h * 0.006));
            profile.setHumidity((int) doubleVal(base.get("humidity")));
            profile.setVisibility(BigDecimal.valueOf(doubleVal(base.get("visibility"))));
            profile.setDataTime(LocalDateTime.now());
            layers.add(profile);
        }
        return layers;
    }

    private double doubleVal(Object value) {
        if (value == null) return 0d;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0d;
        }
    }

    /** 与 RegionGridSampler / 种子数据 product 命名对齐 */
    private String normalizeGridProduct(String product) {
        if (product == null || product.isBlank()) {
            return "temperature";
        }
        return switch (product.trim().toLowerCase()) {
            case "precipitation", "precip" -> "precip";
            case "wind", "visibility", "humidity", "temperature", "cloud", "pressure" -> product.trim().toLowerCase();
            default -> product.trim();
        };
    }

    private boolean hasValidGridValues(List<Map<String, Object>> grid) {
        if (grid == null || grid.isEmpty()) {
            return false;
        }
        return grid.stream().anyMatch(c -> c.get("value") != null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeHeatmapToGrid(Map<String, Object> heatmap) {
        if (heatmap == null || heatmap.isEmpty()) {
            return List.of();
        }

        Object raw = heatmap.get("data");
        if (raw == null) {
            raw = heatmap.get("points");
        }
        if (raw == null) {
            raw = heatmap.get("grid");
        }
        if (!(raw instanceof List<?> source) || source.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> grid = new ArrayList<>();
        for (Object item : source) {
            if (!(item instanceof Map<?, ?> src)) {
                continue;
            }
            Double lng = extractCoordinate(src, "lng", "longitude", "lon", "x");
            Double lat = extractCoordinate(src, "lat", "latitude", "y");
            if (lng == null || lat == null) {
                Object lngLatObj = src.get("lnglat");
                if (lngLatObj instanceof List<?> lngLat && lngLat.size() >= 2) {
                    lng = toDouble(lngLat.get(0));
                    lat = toDouble(lngLat.get(1));
                }
            }
            if (lng == null || lat == null) {
                continue;
            }

            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("lng", lng);
            cell.put("lat", lat);
            Object value = src.get("value");
            if (value != null) {
                cell.put("value", value);
            }
            Object reason = src.get("reason");
            if (reason != null) {
                cell.put("reason", reason);
            }
            grid.add(cell);
        }
        return grid;
    }

    private Double extractCoordinate(Map<?, ?> src, String... keys) {
        for (String key : keys) {
            Double parsed = toDouble(src.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
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
        } catch (Exception ex) {
            return null;
        }
    }
}
