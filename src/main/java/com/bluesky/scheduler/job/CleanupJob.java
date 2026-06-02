package com.bluesky.scheduler.job;

import com.bluesky.scheduler.config.SchedulerProperties;
import com.bluesky.scheduler.service.CacheCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CleanupJob {

    private final CacheCleanupService cacheCleanupService;
    private final SchedulerProperties properties;

    @Scheduled(cron = "${scheduler.cleanup-cron:0 0 2 * * *}")
    public void runDailyCleanup() {
        if (!properties.isEnabled()) {
            return;
        }
        log.info("开始每日缓存清理");
        cacheCleanupService.cleanupAll();
    }
}
