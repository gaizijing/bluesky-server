package com.bluesky.scheduler;

import com.bluesky.scheduler.config.SchedulerProperties;
import com.bluesky.scheduler.job.FlyabilityCacheJob;
import com.bluesky.scheduler.job.GridCacheJob;
import com.bluesky.scheduler.job.RiskCacheJob;
import com.bluesky.util.TimeBucketUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerPipelineRunner {

    private final SchedulerProperties properties;
    private final GridCacheJob gridCacheJob;
    private final FlyabilityCacheJob flyabilityCacheJob;
    private final RiskCacheJob riskCacheJob;

    @Async
    public void runAsync(String regionId, LocalDateTime bucketTime) {
        run(regionId, bucketTime);
    }

    public void run(String regionId, LocalDateTime bucketTime) {
        if (!properties.isEnabled()) {
            return;
        }
        long start = System.currentTimeMillis();
        log.info("调度流水线开始 region={} bucket={}", regionId, bucketTime);
        try {
            gridCacheJob.run(regionId, bucketTime);
            flyabilityCacheJob.run(regionId, bucketTime);
            riskCacheJob.run(regionId, bucketTime);
            log.info("调度流水线完成 region={} bucket={} durationMs={}",
                    regionId, bucketTime, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("调度流水线失败 region={} bucket={}", regionId, bucketTime, e);
        }
    }

    public LocalDateTime currentBucketLocal() {
        return TimeBucketUtil.currentBucketLocal();
    }
}
