package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.RiskFieldCache;
import com.bluesky.entity.RiskRuleSet;
import com.bluesky.mapper.RiskFieldCacheMapper;
import com.bluesky.util.TimeBucketUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RiskService {

    private final RiskRuleSetService ruleSetService;
    private final RegionService regionService;
    private final RiskFieldCacheMapper riskFieldCacheMapper;
    private final WeatherService weatherService;

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

        RiskRuleSet ruleSet = ruleSetService.getPublished();
        Map<String, Object> weather = weatherService.getWeatherByCoordinates(lng, lat);
        double wind = doubleVal(weather.get("windSpeed"));
        double value = Math.min(100d, wind * 8d);
        String level = value >= 70 ? "HIGH" : value >= 40 ? "MEDIUM" : "LOW";
        result.put("value", value);
        result.put("level", level);
        result.put("reason", wind >= 10 ? "风速偏大" : "综合风险一般");
        result.put("ruleVersion", ruleSet.getRuleSetId() + "-v" + ruleSet.getVersionNo());
        result.put("isStale", true);
        return result;
    }

    public Map<String, Object> queryHeatmap(String regionId, String time, int heightM,
                                            Double west, Double south, Double east, Double north) {
        regionService.assertRegionAccess(regionId);
        OffsetDateTime requested = TimeBucketUtil.parseOrNow(time);
        LocalDateTime bucket = TimeBucketUtil.toBucket(requested).atZoneSameInstant(TimeBucketUtil.ZONE).toLocalDateTime();

        LambdaQueryWrapper<RiskFieldCache> wrapper = new LambdaQueryWrapper<RiskFieldCache>()
                .eq(RiskFieldCache::getRegionId, regionId)
                .eq(RiskFieldCache::getBucketTime, bucket)
                .eq(RiskFieldCache::getHeightM, heightM);
        if (west != null && east != null && south != null && north != null) {
            wrapper.ge(RiskFieldCache::getLng, west).le(RiskFieldCache::getLng, east)
                    .ge(RiskFieldCache::getLat, south).le(RiskFieldCache::getLat, north);
        }

        List<RiskFieldCache> cells = riskFieldCacheMapper.selectList(wrapper);
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
        payload.put("bucketTime", bucket.atZone(TimeBucketUtil.ZONE).toOffsetDateTime());
        payload.put("heightM", heightM);
        payload.put("cells", grid);
        payload.put("isStale", grid.isEmpty());
        return payload;
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
