package com.bluesky.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.WeatherGridCache;
import com.bluesky.mapper.WeatherGridCacheMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherGridCacheService {

    private final WeatherGridCacheMapper mapper;
    private final ObjectMapper objectMapper;

    public Optional<WeatherGridCache> find(String regionId, LocalDateTime bucketTime, int heightM, String product) {
        WeatherGridCache exact = mapper.selectOne(new LambdaQueryWrapper<WeatherGridCache>()
                .eq(WeatherGridCache::getRegionId, regionId)
                .eq(WeatherGridCache::getBucketTime, bucketTime)
                .eq(WeatherGridCache::getHeightM, heightM)
                .eq(WeatherGridCache::getProduct, product)
                .last("LIMIT 1"));
        if (exact != null) {
            return Optional.of(exact);
        }

        WeatherGridCache before = mapper.selectOne(new LambdaQueryWrapper<WeatherGridCache>()
                .eq(WeatherGridCache::getRegionId, regionId)
                .eq(WeatherGridCache::getHeightM, heightM)
                .eq(WeatherGridCache::getProduct, product)
                .le(WeatherGridCache::getBucketTime, bucketTime)
                .orderByDesc(WeatherGridCache::getBucketTime)
                .last("LIMIT 1"));

        WeatherGridCache after = mapper.selectOne(new LambdaQueryWrapper<WeatherGridCache>()
                .eq(WeatherGridCache::getRegionId, regionId)
                .eq(WeatherGridCache::getHeightM, heightM)
                .eq(WeatherGridCache::getProduct, product)
                .ge(WeatherGridCache::getBucketTime, bucketTime)
                .orderByAsc(WeatherGridCache::getBucketTime)
                .last("LIMIT 1"));

        return Optional.ofNullable(pickNearestBucket(before, after, bucketTime));
    }

    /**
     * 返回含有效 value 的格点缓存。调度任务可能写入全 null 的和风失败行，需跳过。
     */
    public Optional<WeatherGridCache> findWithValidGrid(String regionId, LocalDateTime bucketTime,
                                                        int heightM, String product) {
        Optional<WeatherGridCache> nearest = find(regionId, bucketTime, heightM, product);
        if (nearest.isPresent() && hasValidGridValues(nearest.get())) {
            return nearest;
        }
        Optional<WeatherGridCache> latest = findLatest(regionId, heightM, product);
        if (latest.isPresent() && hasValidGridValues(latest.get())) {
            return latest;
        }
        return Optional.empty();
    }

    public Optional<WeatherGridCache> findLatest(String regionId, int heightM, String product) {
        WeatherGridCache latest = mapper.selectOne(new LambdaQueryWrapper<WeatherGridCache>()
                .eq(WeatherGridCache::getRegionId, regionId)
                .eq(WeatherGridCache::getHeightM, heightM)
                .eq(WeatherGridCache::getProduct, product)
                .orderByDesc(WeatherGridCache::getBucketTime)
                .last("LIMIT 1"));
        return Optional.ofNullable(latest);
    }

    private boolean hasValidGridValues(WeatherGridCache cache) {
        List<Map<String, Object>> grid = toGridPoints(cache);
        if (grid.isEmpty()) {
            return false;
        }
        return grid.stream().anyMatch(c -> c.get("value") != null);
    }

    /**
     * 在「不晚于请求桶」与「不早于请求桶」两条候选中取更近者。
     * 避免 Timeline 落在 13:30 却只命中 11:45 的稀疏调度格点，而忽略 13:45 的 64×64 种子。
     */
    private WeatherGridCache pickNearestBucket(WeatherGridCache before, WeatherGridCache after,
                                               LocalDateTime requested) {
        if (before == null) {
            return after;
        }
        if (after == null) {
            return before;
        }
        long minutesBefore = ChronoUnit.MINUTES.between(before.getBucketTime(), requested);
        long minutesAfter = ChronoUnit.MINUTES.between(requested, after.getBucketTime());
        if (minutesAfter <= 15 && minutesBefore > 30) {
            return after;
        }
        if (minutesBefore <= minutesAfter) {
            return before;
        }
        return minutesAfter <= 60 ? after : before;
    }

    @Transactional
    public void upsert(String regionId, LocalDateTime bucketTime, int heightM, String product,
                       Map<String, Object> grid, LocalDateTime dataSourceTime) {
        String json;
        try {
            json = objectMapper.writeValueAsString(grid);
        } catch (Exception e) {
            throw new IllegalArgumentException("grid_json 序列化失败", e);
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<WeatherGridCache> existing = find(regionId, bucketTime, heightM, product);
        if (existing.isPresent()) {
            WeatherGridCache row = existing.get();
            row.setGridJson(json);
            row.setDataSourceTime(dataSourceTime);
            row.setComputedAt(now);
            row.setExpiresAt(now.plusDays(7));
            mapper.updateById(row);
        } else {
            WeatherGridCache row = new WeatherGridCache();
            row.setRegionId(regionId);
            row.setBucketTime(bucketTime);
            row.setHeightM(heightM);
            row.setProduct(product);
            row.setGridJson(json);
            row.setDataSourceTime(dataSourceTime);
            row.setComputedAt(now);
            row.setExpiresAt(now.plusDays(7));
            mapper.insert(row);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> toGridPoints(WeatherGridCache cache) {
        if (cache == null || cache.getGridJson() == null) {
            return List.of();
        }
        try {
            Map<String, Object> grid = objectMapper.readValue(cache.getGridJson(), new TypeReference<>() {});
            Object cells = grid.get("cells");
            if (cells instanceof List<?> list) {
                return (List<Map<String, Object>>) (List<?>) list;
            }
        } catch (Exception e) {
            log.warn("解析 grid_json 失败 cacheId={}: {}", cache.getCacheId(), e.getMessage());
        }
        return List.of();
    }
}
