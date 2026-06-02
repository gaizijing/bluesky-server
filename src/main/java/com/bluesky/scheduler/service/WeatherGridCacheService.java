package com.bluesky.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.WeatherGridCache;
import com.bluesky.mapper.WeatherGridCacheMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WeatherGridCacheService {

    private final WeatherGridCacheMapper mapper;
    private final ObjectMapper objectMapper;

    public Optional<WeatherGridCache> find(String regionId, LocalDateTime bucketTime, int heightM, String product) {
        WeatherGridCache row = mapper.selectOne(new LambdaQueryWrapper<WeatherGridCache>()
                .eq(WeatherGridCache::getRegionId, regionId)
                .eq(WeatherGridCache::getBucketTime, bucketTime)
                .eq(WeatherGridCache::getHeightM, heightM)
                .eq(WeatherGridCache::getProduct, product)
                .last("LIMIT 1"));
        return Optional.ofNullable(row);
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
        } catch (Exception ignored) {
        }
        return List.of();
    }
}
