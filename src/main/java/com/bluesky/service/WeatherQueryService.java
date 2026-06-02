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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.*;

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
        String productKey = product != null && !product.isBlank() ? product : "temperature";

        var cacheOpt = weatherGridCacheService.find(regionId, bucketLocal, heightM, productKey);
        if (cacheOpt.isPresent()) {
            WeatherGridCache cache = cacheOpt.get();
            TemporalMeta meta = TimeBucketUtil.buildMeta(requested, TimeBucketUtil.now(), false);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("regionId", regionId);
            payload.put("product", productKey);
            payload.put("heightM", heightM);
            payload.put("requestedTime", meta.getRequestedTime());
            payload.put("bucketTime", meta.getBucketTime());
            payload.put("computedAt", cache.getComputedAt() != null
                    ? cache.getComputedAt().atZone(TimeBucketUtil.ZONE).toOffsetDateTime()
                    : meta.getComputedAt());
            payload.put("isStale", false);
            payload.put("grid", weatherGridCacheService.toGridPoints(cache));
            return payload;
        }

        TemporalMeta meta = TimeBucketUtil.buildMeta(requested, TimeBucketUtil.now(), true);
        var region = regionService.getEntity(regionId);
        String bounds = String.format("[%s,%s,%s,%s]",
                region.getWest(), region.getSouth(), region.getEast(), region.getNorth());
        Map<String, Object> heatmap = weatherService.getWeatherHeatmapGeo(bounds, meta.getBucketTime().toString(), null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("regionId", regionId);
        payload.put("product", productKey);
        payload.put("heightM", heightM);
        payload.put("requestedTime", meta.getRequestedTime());
        payload.put("bucketTime", meta.getBucketTime());
        payload.put("computedAt", meta.getComputedAt());
        payload.put("isStale", true);
        payload.put("grid", heatmap.getOrDefault("points", List.of()));
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
}
