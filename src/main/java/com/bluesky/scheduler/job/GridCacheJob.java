package com.bluesky.scheduler.job;

import com.bluesky.entity.Region;
import com.bluesky.scheduler.client.RegionGridSampler;
import com.bluesky.scheduler.config.SchedulerProperties;
import com.bluesky.scheduler.service.WeatherGridCacheService;
import com.bluesky.service.RegionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GridCacheJob {

    private final RegionService regionService;
    private final RegionGridSampler gridSampler;
    private final WeatherGridCacheService gridCacheService;
    private final SchedulerProperties properties;

    public void run(String regionId, LocalDateTime bucketTime) {
        Region region = regionService.getEntity(regionId);
        LocalDateTime dataSourceTime = LocalDateTime.now();
        int rows = properties.getGridRows();
        int cols = properties.getGridCols();

        for (Integer heightM : properties.getHeights()) {
            for (String product : properties.getProducts()) {
                try {
                    Map<String, Object> grid = gridSampler.sample(
                            region, rows, cols, product, properties.getGridSampleIntervalMs());
                    if (!hasAnyValue(grid)) {
                        log.warn("格点缓存跳过（Open-Meteo 无有效 value）region={} bucket={} height={} product={}",
                                regionId, bucketTime, heightM, product);
                        continue;
                    }
                    gridCacheService.upsert(regionId, bucketTime, heightM, product, grid, dataSourceTime);
                    log.info("格点缓存完成 region={} bucket={} height={} product={}",
                            regionId, bucketTime, heightM, product);
                } catch (Exception e) {
                    log.warn("格点缓存失败 region={} bucket={} height={} product={}: {}",
                            regionId, bucketTime, heightM, product, e.getMessage());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean hasAnyValue(Map<String, Object> grid) {
        Object cells = grid.get("cells");
        if (!(cells instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> cell && cell.get("value") != null) {
                return true;
            }
        }
        return false;
    }
}
