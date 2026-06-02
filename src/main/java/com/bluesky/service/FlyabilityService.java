package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.common.TemporalMeta;
import com.bluesky.entity.FlyabilityRuleSet;
import com.bluesky.entity.LandingPoint;
import com.bluesky.entity.OsiLandingCache;
import com.bluesky.entity.RouteWaypoint;
import com.bluesky.enums.FlyabilityLevel;
import com.bluesky.mapper.OsiLandingCacheMapper;
import com.bluesky.service.flyability.FlyabilityCalculator;
import com.bluesky.util.TimeBucketUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FlyabilityService {

    private final FlyabilityRuleSetService ruleSetService;
    private final LandingPointService landingPointService;
    private final WeatherService weatherService;
    private final RegionService regionService;
    private final OsiLandingCacheMapper osiLandingCacheMapper;
    private final RouteLifecycleService routeLifecycleService;
    private final FlyabilityCalculator calculator;
    private final ObjectMapper objectMapper;

    public Map<String, Object> landingMatrix(String regionId, String landingPointId, String time, int hours) {
        regionService.assertRegionAccess(regionId);
        FlyabilityRuleSet ruleSet = ruleSetService.getPublished();
        String ruleVersion = ruleSet.getRuleSetId() + "-v" + ruleSet.getVersionNo();

        List<LandingPoint> points;
        if (landingPointId != null && !landingPointId.isBlank()) {
            LandingPoint point = landingPointService.getEntity(landingPointId);
            if (!regionId.equals(point.getRegionId())) {
                throw new com.bluesky.exception.BusinessException(com.bluesky.common.ResultCode.BAD_REQUEST, "起降点不属于该 Region");
            }
            points = List.of(point);
        } else {
            points = landingPointService.listAllEntities().stream()
                    .filter(p -> regionId.equals(p.getRegionId())).toList();
        }

        OffsetDateTime requested = TimeBucketUtil.parseOrNow(time);
        OffsetDateTime bucketStart = TimeBucketUtil.toBucket(requested);
        List<Map<String, Object>> matrix = new ArrayList<>();
        int buckets = Math.max(1, hours * 4);
        for (int i = 0; i < buckets; i++) {
            OffsetDateTime bucket = bucketStart.plusMinutes((long) i * TimeBucketUtil.BUCKET_MINUTES);
            LocalDateTime bucketLocal = bucket.atZoneSameInstant(TimeBucketUtil.ZONE).toLocalDateTime();
            for (LandingPoint point : points) {
                matrix.add(buildLandingCell(point, bucketLocal, ruleSet, ruleVersion));
            }
        }

        TemporalMeta meta = TimeBucketUtil.buildMeta(requested, TimeBucketUtil.now(), false);
        boolean anyStale = matrix.stream().anyMatch(c -> Boolean.TRUE.equals(c.get("isStale")));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("regionId", regionId);
        payload.put("ruleVersion", ruleVersion);
        payload.put("bucketTime", meta.getBucketTime());
        payload.put("requestedTime", meta.getRequestedTime());
        payload.put("computedAt", meta.getComputedAt());
        payload.put("isStale", anyStale);
        payload.put("matrix", matrix);
        return payload;
    }

    public Map<String, Object> routeMatrix(String regionId, String routeId, String routeVersionId,
                                           String time, int hours) {
        regionService.assertRegionAccess(regionId);
        Map<String, Object> detail = routeLifecycleService.getRouteDetail(routeId, routeVersionId);
        if (!regionId.equals(detail.get("regionId"))) {
            throw new com.bluesky.exception.BusinessException(
                    com.bluesky.common.ResultCode.BAD_REQUEST, "航路不属于该 Region");
        }

        FlyabilityRuleSet ruleSet = ruleSetService.getPublished();
        String ruleVersion = ruleSet.getRuleSetId() + "-v" + ruleSet.getVersionNo();
        String versionId = String.valueOf(detail.get("routeVersionId"));
        List<RouteWaypoint> waypoints = routeLifecycleService.listWaypoints(routeId, versionId);
        if (waypoints.isEmpty()) {
            throw new com.bluesky.exception.BusinessException(
                    com.bluesky.common.ResultCode.NOT_FOUND, "航路无途经点");
        }

        OffsetDateTime requested = TimeBucketUtil.parseOrNow(time);
        OffsetDateTime bucketStart = TimeBucketUtil.toBucket(requested);
        List<Map<String, Object>> matrix = new ArrayList<>();
        int buckets = Math.max(1, hours * 4);
        for (int i = 0; i < buckets; i++) {
            OffsetDateTime bucket = bucketStart.plusMinutes((long) i * TimeBucketUtil.BUCKET_MINUTES);
            matrix.add(buildRouteCell(routeId, versionId, waypoints, bucket, ruleSet, ruleVersion));
        }

        TemporalMeta meta = TimeBucketUtil.buildMeta(requested, TimeBucketUtil.now(), false);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("regionId", regionId);
        payload.put("routeId", routeId);
        payload.put("routeVersionId", versionId);
        payload.put("ruleVersion", ruleVersion);
        payload.put("bucketTime", meta.getBucketTime());
        payload.put("requestedTime", meta.getRequestedTime());
        payload.put("computedAt", meta.getComputedAt());
        payload.put("isStale", meta.getIsStale());
        payload.put("matrix", matrix);
        return payload;
    }

    private Map<String, Object> buildRouteCell(String routeId, String routeVersionId,
                                               List<RouteWaypoint> waypoints, OffsetDateTime bucket,
                                               FlyabilityRuleSet ruleSet, String ruleVersion) {
        FlyabilityLevel aggregate = FlyabilityLevel.GREEN;
        List<Map<String, Object>> segmentResults = new ArrayList<>();
        for (RouteWaypoint wp : waypoints) {
            Map<String, Object> weather = weatherService.buildFlyabilityWeatherMap(
                    wp.getLongitude(), wp.getLatitude());
            Map<String, Object> evaluated = calculator.evaluate(ruleSet.getRulesJson(), weather);
            FlyabilityLevel level = FlyabilityLevel.valueOf(String.valueOf(evaluated.get("level")));
            aggregate = FlyabilityLevel.max(aggregate, level);
            Map<String, Object> seg = new LinkedHashMap<>();
            seg.put("sequence", wp.getSequence());
            seg.put("level", level.name());
            seg.put("factorResults", evaluated.get("factorResults"));
            segmentResults.add(seg);
        }
        Map<String, Object> cell = new LinkedHashMap<>();
        cell.put("routeId", routeId);
        cell.put("routeVersionId", routeVersionId);
        cell.put("bucketTime", bucket);
        cell.put("level", aggregate.name());
        cell.put("ruleVersion", ruleVersion);
        cell.put("isStale", true);
        cell.put("segments", segmentResults);
        return cell;
    }

    private Map<String, Object> buildLandingCell(LandingPoint point, LocalDateTime bucketTime,
                                                 FlyabilityRuleSet ruleSet, String ruleVersion) {
        OsiLandingCache cached = osiLandingCacheMapper.selectOne(new LambdaQueryWrapper<OsiLandingCache>()
                .eq(OsiLandingCache::getLandingPointId, point.getLandingPointId())
                .eq(OsiLandingCache::getBucketTime, bucketTime)
                .last("LIMIT 1"));

        Map<String, Object> cell = new LinkedHashMap<>();
        cell.put("landingPointId", point.getLandingPointId());
        cell.put("bucketTime", bucketTime.atZone(TimeBucketUtil.ZONE).toOffsetDateTime());

        if (cached != null) {
            cell.put("level", cached.getLevel());
            cell.put("ruleVersion", cached.getRuleVersion());
            cell.put("isStale", false);
            cell.put("factorResults", parseJsonList(cached.getFactorResultsJson()));
            return cell;
        }

        Map<String, Object> weather = weatherService.buildFlyabilityWeatherMap(point.getLandingPointId());
        Map<String, Object> evaluated = calculator.evaluate(ruleSet.getRulesJson(), weather);
        cell.putAll(evaluated);
        cell.put("ruleVersion", ruleVersion);
        cell.put("isStale", true);
        return cell;
    }

    private List<Map<String, Object>> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
