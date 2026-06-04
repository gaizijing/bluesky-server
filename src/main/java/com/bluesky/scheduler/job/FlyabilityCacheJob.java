package com.bluesky.scheduler.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.FlyabilityRuleSet;
import com.bluesky.entity.LandingPoint;
import com.bluesky.entity.OsiLandingCache;
import com.bluesky.entity.OsiRouteCache;
import com.bluesky.entity.Route;
import com.bluesky.entity.RouteWaypoint;
import com.bluesky.mapper.OsiLandingCacheMapper;
import com.bluesky.mapper.OsiRouteCacheMapper;
import com.bluesky.scheduler.config.SchedulerProperties;
import com.bluesky.service.FlyabilityService;
import com.bluesky.service.FlyabilityRuleSetService;
import com.bluesky.service.LandingPointService;
import com.bluesky.service.RouteLifecycleService;
import com.bluesky.service.WeatherService;
import com.bluesky.service.flyability.FlyabilityCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlyabilityCacheJob {

    private final FlyabilityRuleSetService ruleSetService;
    private final LandingPointService landingPointService;
    private final RouteLifecycleService routeLifecycleService;
    private final FlyabilityService flyabilityService;
    private final WeatherService weatherService;
    private final FlyabilityCalculator calculator;
    private final OsiLandingCacheMapper osiLandingCacheMapper;
    private final OsiRouteCacheMapper osiRouteCacheMapper;
    private final ObjectMapper objectMapper;
    private final SchedulerProperties properties;

    public void run(String regionId, LocalDateTime bucketTime) {
        FlyabilityRuleSet ruleSet = ruleSetService.getPublished();
        String ruleVersion = ruleSet.getRuleSetId() + "-v" + ruleSet.getVersionNo();

        List<LandingPoint> points = landingPointService.listAllEntities().stream()
                .filter(p -> regionId.equals(p.getRegionId()))
                .toList();

        for (LandingPoint point : points) {
            try {
                Map<String, Object> weather = weatherService.buildFlyabilityWeatherMap(
                        point.getLandingPointId(), bucketTime);
                Map<String, Object> evaluated = calculator.evaluate(ruleSet.getRulesJson(), weather);
                upsertLanding(point.getLandingPointId(), bucketTime, evaluated, ruleVersion);
            } catch (Exception e) {
                log.warn("适飞缓存失败 landingPoint={} bucket={}: {}",
                        point.getLandingPointId(), bucketTime, e.getMessage());
            }
            sleepSample();
        }

        List<Route> routes = routeLifecycleService.listRoutesByRegion(regionId);
        for (Route route : routes) {
            String versionId = route.getCurrentVersionId();
            if (versionId == null || versionId.isBlank()) {
                continue;
            }
            try {
                List<RouteWaypoint> waypoints = routeLifecycleService.listWaypoints(route.getId(), versionId);
                if (waypoints.isEmpty()) {
                    continue;
                }
                Map<String, Object> evaluated = flyabilityService.evaluateRouteAtBucket(
                        waypoints, bucketTime, ruleSet.getRulesJson());
                upsertRoute(route.getId(), versionId, bucketTime, evaluated, ruleVersion);
            } catch (Exception e) {
                log.warn("航路适飞缓存失败 route={} bucket={}: {}",
                        route.getId(), bucketTime, e.getMessage());
            }
            sleepSample();
        }

        log.info("适飞缓存完成 region={} bucket={} landing={} routes={}",
                regionId, bucketTime, points.size(), routes.size());
    }

    private void upsertLanding(String landingPointId, LocalDateTime bucketTime,
                        Map<String, Object> evaluated, String ruleVersion) throws Exception {
        String factorsJson = objectMapper.writeValueAsString(evaluated.get("factorResults"));
        LocalDateTime now = LocalDateTime.now();

        OsiLandingCache existing = osiLandingCacheMapper.selectOne(new LambdaQueryWrapper<OsiLandingCache>()
                .eq(OsiLandingCache::getLandingPointId, landingPointId)
                .eq(OsiLandingCache::getBucketTime, bucketTime)
                .last("LIMIT 1"));

        if (existing != null) {
            existing.setLevel(String.valueOf(evaluated.get("level")));
            existing.setFactorResultsJson(factorsJson);
            existing.setRuleVersion(ruleVersion);
            existing.setComputedAt(now);
            osiLandingCacheMapper.updateById(existing);
        } else {
            OsiLandingCache row = new OsiLandingCache();
            row.setLandingPointId(landingPointId);
            row.setBucketTime(bucketTime);
            row.setLevel(String.valueOf(evaluated.get("level")));
            row.setFactorResultsJson(factorsJson);
            row.setRuleVersion(ruleVersion);
            row.setComputedAt(now);
            osiLandingCacheMapper.insert(row);
        }
    }

    private void upsertRoute(String routeId, String routeVersionId, LocalDateTime bucketTime,
                             Map<String, Object> evaluated, String ruleVersion) throws Exception {
        String segmentsJson = objectMapper.writeValueAsString(evaluated.get("segments"));
        LocalDateTime now = LocalDateTime.now();

        OsiRouteCache existing = osiRouteCacheMapper.selectOne(new LambdaQueryWrapper<OsiRouteCache>()
                .eq(OsiRouteCache::getRouteId, routeId)
                .eq(OsiRouteCache::getRouteVersionId, routeVersionId)
                .eq(OsiRouteCache::getBucketTime, bucketTime)
                .last("LIMIT 1"));

        if (existing != null) {
            existing.setLevel(String.valueOf(evaluated.get("level")));
            existing.setFactorResultsJson(segmentsJson);
            existing.setRuleVersion(ruleVersion);
            existing.setComputedAt(now);
            osiRouteCacheMapper.updateById(existing);
        } else {
            OsiRouteCache row = new OsiRouteCache();
            row.setRouteId(routeId);
            row.setRouteVersionId(routeVersionId);
            row.setBucketTime(bucketTime);
            row.setLevel(String.valueOf(evaluated.get("level")));
            row.setFactorResultsJson(segmentsJson);
            row.setRuleVersion(ruleVersion);
            row.setComputedAt(now);
            osiRouteCacheMapper.insert(row);
        }
    }

    private void sleepSample() {
        long ms = properties.getGridSampleIntervalMs();
        if (ms > 0) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
