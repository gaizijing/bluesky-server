package com.bluesky.scheduler.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.Region;
import com.bluesky.entity.RiskFieldCache;
import com.bluesky.entity.RiskRuleSet;
import com.bluesky.mapper.RiskFieldCacheMapper;
import com.bluesky.scheduler.config.SchedulerProperties;
import com.bluesky.service.RegionBoundaryService;
import com.bluesky.service.RegionService;
import com.bluesky.service.RiskRuleSetService;
import com.bluesky.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskCacheJob {

    private final RegionService regionService;
    private final RegionBoundaryService regionBoundaryService;
    private final RiskRuleSetService ruleSetService;
    private final WeatherService weatherService;
    private final RiskFieldCacheMapper riskFieldCacheMapper;
    private final SchedulerProperties properties;

    public void run(String regionId, LocalDateTime bucketTime) {
        Region region = regionService.getEntity(regionId);
        var envelope = regionBoundaryService.resolveEnvelope(region);
        RiskRuleSet ruleSet = ruleSetService.getPublished();
        String ruleVersion = ruleSet.getRuleSetId() + "-v" + ruleSet.getVersionNo();

        int rows = properties.getGridRows();
        int cols = properties.getGridCols();
        double west = envelope.west();
        double east = envelope.east();
        double south = envelope.south();
        double north = envelope.north();

        for (Integer heightM : properties.getHeights()) {
            riskFieldCacheMapper.delete(new LambdaQueryWrapper<RiskFieldCache>()
                    .eq(RiskFieldCache::getRegionId, regionId)
                    .eq(RiskFieldCache::getBucketTime, bucketTime)
                    .eq(RiskFieldCache::getHeightM, heightM));

            List<RiskFieldCache> batch = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            for (int r = 0; r < rows; r++) {
                double lat = south + (north - south) * r / Math.max(1, rows - 1.0);
                for (int c = 0; c < cols; c++) {
                    double lng = west + (east - west) * c / Math.max(1, cols - 1.0);
                    Map<String, Object> raw = weatherService.getWeatherByCoordinates(lng, lat);
                    Map<String, Object> weather = weatherService.flattenCoordinatesWeather(raw);
                    double wind = doubleVal(weather.get("windSpeed"));
                    double value = Math.min(100d, wind * 8d);
                    String level = value >= 70 ? "HIGH" : value >= 40 ? "MEDIUM" : "LOW";
                    String reason = wind >= 10 ? "风速偏大" : "综合风险一般";

                    RiskFieldCache cell = new RiskFieldCache();
                    cell.setRegionId(regionId);
                    cell.setBucketTime(bucketTime);
                    cell.setHeightM(heightM);
                    cell.setLng(lng);
                    cell.setLat(lat);
                    cell.setValue(BigDecimal.valueOf(value));
                    cell.setLevel(level);
                    cell.setReason(reason);
                    cell.setRuleVersion(ruleVersion);
                    cell.setComputedAt(now);
                    batch.add(cell);
                    sleepSample();
                }
            }
            for (RiskFieldCache cell : batch) {
                riskFieldCacheMapper.insert(cell);
            }
            log.info("风险场缓存完成 region={} bucket={} height={} cells={}",
                    regionId, bucketTime, heightM, batch.size());
        }
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
