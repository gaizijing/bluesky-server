package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.common.ResultCode;
import com.bluesky.exception.BusinessException;
import com.bluesky.scheduler.RuleType;
import com.bluesky.scheduler.SchedulerPipelineRunner;
import com.bluesky.scheduler.event.RulePublishedEvent;
import com.bluesky.scheduler.health.SchedulerHealthService;
import com.bluesky.scheduler.service.CacheCleanupService;
import com.bluesky.scheduler.service.RecomputeService;
import com.bluesky.security.LoginUser;
import com.bluesky.security.SecurityUtils;
import com.bluesky.service.RegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开发/运维：手动触发 P2 缓存流水线（需超级管理员）。
 */
@RestController
@RequestMapping("/scheduler")
@RequiredArgsConstructor
public class SchedulerAdminController {

    private final SchedulerPipelineRunner pipelineRunner;
    private final RegionService regionService;
    private final CacheCleanupService cacheCleanupService;
    private final RecomputeService recomputeService;
    private final SchedulerHealthService schedulerHealthService;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        requireSuperAdmin();
        return Result.success(schedulerHealthService.snapshot());
    }

    @PostMapping("/cleanup")
    public Result<Map<String, Integer>> cleanup() {
        requireSuperAdmin();
        return Result.success(cacheCleanupService.cleanupAll());
    }

    @PostMapping("/recompute-by-rule")
    public Result<Map<String, Object>> recomputeByRule(
            @RequestParam RuleType ruleType,
            @RequestParam(required = false) String regionId,
            @RequestParam(required = false) String ruleSetId) {
        requireSuperAdmin();
        recomputeService.enqueue(new RulePublishedEvent(this, ruleType,
                ruleSetId != null ? ruleSetId : "manual", regionId));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ruleType", ruleType.name());
        body.put("regionId", regionId != null ? regionId : "ALL");
        body.put("status", "queued");
        return Result.success(body);
    }

    @PostMapping("/recompute")
    public Result<Map<String, Object>> recompute(
            @RequestParam(required = false) String regionId) {
        requireSuperAdmin();
        LocalDateTime bucket = pipelineRunner.currentBucketLocal();
        if (regionId != null && !regionId.isBlank()) {
            regionService.getEntity(regionId);
            pipelineRunner.runAsync(regionId, bucket);
            return Result.success(summary(regionId, bucket, "queued"));
        }
        regionService.listEnabled().forEach(r ->
                pipelineRunner.runAsync(r.getRegionId(), bucket));
        return Result.success(summary("ALL", bucket, "queued"));
    }

    private void requireSuperAdmin() {
        LoginUser user = SecurityUtils.requireUser();
        if (!user.getRole().isSuperAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅超级管理员可触发调度");
        }
    }

    private Map<String, Object> summary(String regionId, LocalDateTime bucket, String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("regionId", regionId);
        body.put("bucketTime", bucket);
        body.put("status", status);
        return body;
    }
}
