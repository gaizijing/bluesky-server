package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.FlyabilityRuleSet;
import com.bluesky.entity.RiskFieldCache;
import com.bluesky.mapper.RiskFieldCacheMapper;
import com.bluesky.service.risk.RiskMetCalculator;
import com.bluesky.util.TimeBucketUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RiskService {

    private final FlyabilityRuleSetService flyabilityRuleSetService;
    private final RegionService regionService;
    private final RiskFieldCacheMapper riskFieldCacheMapper;
    private final WeatherService weatherService;
    private final RiskMetCalculator riskMetCalculator;

    public Map<String, Object> queryPoint(double lng, double lat, String time, int heightM) {
        OffsetDateTime requested = TimeBucketUtil.parseOrNow(time);
        LocalDateTime bucket = TimeBucketUtil.toBucket(requested).atZoneSameInstant(TimeBucketUtil.ZONE).toLocalDateTime();

        RiskFieldCache nearest = riskFieldCacheMapper.selectOne(new LambdaQueryWrapper<RiskFieldCache>()
                .eq(RiskFieldCache::getBucketTime, bucket)
                .eq(RiskFieldCache::getHeightM, heightM)
                .orderByAsc(RiskFieldCache::getCacheId)
                .last("LIMIT 1"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lng", lng);
        result.put("lat", lat);
        result.put("heightM", heightM);
        result.put("bucketTime", bucket.atZone(TimeBucketUtil.ZONE).toOffsetDateTime());

        if (nearest != null) {
            result.put("value", nearest.getValue());
            result.put("level", nearest.getLevel());
            result.put("reason", nearest.getReason());
            result.put("ruleVersion", nearest.getRuleVersion());
            result.put("isStale", false);
            return result;
        }

        FlyabilityRuleSet flyabilityRuleSet = flyabilityRuleSetService.getPublished();
        Map<String, Object> weather = weatherService.buildFlyabilityWeatherMap(lng, lat, bucket);
        Map<String, Object> evaluated = riskMetCalculator.evaluate(
                flyabilityRuleSet.getRulesJson(),
                weather);
        result.put("value", evaluated.get("value"));
        result.put("level", evaluated.get("level"));
        result.put("reason", evaluated.get("reason"));
        result.put("ruleVersion", flyabilityRuleSet.getRuleSetId() + "-v" + flyabilityRuleSet.getVersionNo());
        result.put("isStale", true);
        return result;
    }

    /**
     * 读取区域风险场格点（取 targetTime 之前最近一个 bucket，可选 bbox 裁剪）
     */
    public List<RiskFieldCache> loadFieldCells(String regionId, LocalDateTime targetTime, int heightM,
                                               Double west, Double south, Double east, Double north) {
        LambdaQueryWrapper<RiskFieldCache> latestWrapper = new LambdaQueryWrapper<RiskFieldCache>()
                .eq(RiskFieldCache::getRegionId, regionId)
                .eq(RiskFieldCache::getHeightM, heightM);
        if (targetTime != null) {
            latestWrapper.le(RiskFieldCache::getBucketTime, targetTime);
        }
        latestWrapper.orderByDesc(RiskFieldCache::getBucketTime).last("LIMIT 1");

        RiskFieldCache latest = riskFieldCacheMapper.selectOne(latestWrapper);
        if (latest == null || latest.getBucketTime() == null) {
            return List.of();
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

    public Map<String, Object> queryHeatmap(String regionId, String time, int heightM,
                                            Double west, Double south, Double east, Double north) {
        regionService.assertRegionAccess(regionId);
        OffsetDateTime requested = TimeBucketUtil.parseOrNow(time);
        LocalDateTime bucket = TimeBucketUtil.toBucket(requested).atZoneSameInstant(TimeBucketUtil.ZONE).toLocalDateTime();

        List<RiskFieldCache> cells = loadFieldCells(regionId, bucket, heightM, west, south, east, north);
        LocalDateTime resolvedBucket = cells.isEmpty()
                ? bucket
                : cells.get(0).getBucketTime();
        List<Map<String, Object>> grid = cells.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("lng", c.getLng());
            item.put("lat", c.getLat());
            item.put("value", c.getValue());
            item.put("level", c.getLevel());
            item.put("reason", c.getReason());
            return item;
        }).toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("regionId", regionId);
        payload.put("bucketTime", resolvedBucket.atZone(TimeBucketUtil.ZONE).toOffsetDateTime());
        payload.put("heightM", heightM);
        payload.put("cells", grid);
        payload.put("isStale", grid.isEmpty());
        return payload;
    }
}
