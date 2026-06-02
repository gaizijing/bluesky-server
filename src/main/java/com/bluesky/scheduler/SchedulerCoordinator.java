package com.bluesky.scheduler;

import com.bluesky.entity.Region;
import com.bluesky.scheduler.config.SchedulerProperties;
import com.bluesky.service.RegionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerCoordinator {

    private final SchedulerProperties properties;
    private final RegionService regionService;
    private final SchedulerPipelineRunner pipelineRunner;

    @Scheduled(cron = "${scheduler.grid-cron:0 2,17,32,47 * * * *}")
    public void scheduleGridCache() {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDateTime bucket = pipelineRunner.currentBucketLocal();
        List<Region> regions = regionService.listEnabled();
        log.info("定时调度触发 bucket={} regions={}", bucket, regions.size());
        for (Region region : regions) {
            pipelineRunner.runAsync(region.getRegionId(), bucket);
        }
    }
}
