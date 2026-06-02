package com.bluesky.scheduler.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.FlyabilityRuleSet;
import com.bluesky.entity.LandingPoint;
import com.bluesky.entity.OsiLandingCache;
import com.bluesky.mapper.OsiLandingCacheMapper;
import com.bluesky.scheduler.config.SchedulerProperties;
import com.bluesky.service.FlyabilityRuleSetService;
import com.bluesky.service.LandingPointService;
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
    private final WeatherService weatherService;
    private final FlyabilityCalculator calculator;
    private final OsiLandingCacheMapper osiLandingCacheMapper;
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
                Map<String, Object> weather = weatherService.buildFlyabilityWeatherMap(point.getLandingPointId());
                Map<String, Object> evaluated = calculator.evaluate(ruleSet.getRulesJson(), weather);
                upsert(point.getLandingPointId(), bucketTime, evaluated, ruleVersion);
            } catch (Exception e) {
                log.warn("适飞缓存失败 landingPoint={} bucket={}: {}",
                        point.getLandingPointId(), bucketTime, e.getMessage());
            }
            sleepSample();
        }
        log.info("适飞缓存完成 region={} bucket={} points={}", regionId, bucketTime, points.size());
    }

    private void upsert(String landingPointId, LocalDateTime bucketTime,
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
