package com.bluesky.scheduler.service;

import com.bluesky.entity.Region;
import com.bluesky.scheduler.RuleType;
import com.bluesky.scheduler.config.SchedulerProperties;
import com.bluesky.scheduler.event.RulePublishedEvent;
import com.bluesky.scheduler.job.FlyabilityCacheJob;
import com.bluesky.scheduler.job.RiskCacheJob;
import com.bluesky.service.RegionService;
import com.bluesky.util.TimeBucketUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecomputeService {

    private final SchedulerProperties properties;
    private final RegionService regionService;
    private final FlyabilityCacheJob flyabilityCacheJob;
    private final RiskCacheJob riskCacheJob;

    @Async
    public void enqueue(RulePublishedEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDateTime toBucket = TimeBucketUtil.currentBucketLocal();
        int bucketCount = Math.max(1, properties.getRecomputeBuckets());
        LocalDateTime fromBucket = toBucket.minusMinutes((long) (bucketCount - 1) * TimeBucketUtil.BUCKET_MINUTES);
        List<LocalDateTime> buckets = TimeBucketUtil.bucketsBetween(fromBucket, toBucket);

        List<Region> regions = resolveRegions(event.getRegionId());
        log.info("规则发布重算 ruleType={} ruleSetId={} regions={} buckets={}",
                event.getRuleType(), event.getRuleSetId(), regions.size(), buckets.size());

        for (Region region : regions) {
            String regionId = region.getRegionId();
            try {
                switch (event.getRuleType()) {
                    case FLYABILITY -> recomputeFlyability(regionId, buckets);
                    case RISK -> recomputeRisk(regionId, buckets);
                    case WARNING -> log.info("WARNING 规则发布暂不触发 OSI/R_met 缓存重算 region={}", regionId);
                }
            } catch (Exception e) {
                log.error("规则发布重算失败 region={} ruleType={}", regionId, event.getRuleType(), e);
            }
        }
    }

    private void recomputeFlyability(String regionId, List<LocalDateTime> buckets) {
        for (LocalDateTime bucket : buckets) {
            flyabilityCacheJob.run(regionId, bucket);
        }
    }

    private void recomputeRisk(String regionId, List<LocalDateTime> buckets) {
        for (LocalDateTime bucket : buckets) {
            riskCacheJob.run(regionId, bucket);
        }
    }

    private List<Region> resolveRegions(String regionId) {
        if (regionId != null && !regionId.isBlank()) {
            return List.of(regionService.getEntity(regionId));
        }
        return regionService.listEnabled();
    }
}
