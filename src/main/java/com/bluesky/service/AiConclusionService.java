package com.bluesky.service;

import com.bluesky.common.ResultCode;
import com.bluesky.exception.BusinessException;
import com.bluesky.util.TimeBucketUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class AiConclusionService {

    private final FlyabilityService flyabilityService;
    private final WarningService warningService;
    private final RouteLifecycleService routeLifecycleService;

    @Value("${llm.timeout-ms:3000}")
    private long timeoutMs;

    public Map<String, Object> generate(String scene, String regionId, String targetType,
                                        String targetId, String time) {
        Map<String, Object> factPack = buildFactPack(scene, regionId, targetType, targetId, time);
        try {
            return CompletableFuture.supplyAsync(() -> callLlm(factPack))
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            return templateFallback(scene, factPack);
        }
    }

    private Map<String, Object> buildFactPack(String scene, String regionId, String targetType,
                                              String targetId, String time) {
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("scene", scene);
        pack.put("regionId", regionId);
        pack.put("targetType", targetType);
        pack.put("targetId", targetId);
        pack.put("time", time != null ? time : TimeBucketUtil.now().toString());

        switch (scene != null ? scene : "") {
            case "landing_brief" -> pack.put("flyability",
                    flyabilityService.landingMatrix(regionId, targetId, time, 1));
            case "l2_warning_detail" -> pack.put("warning", warningService.getById(targetId));
            case "route_avoidance" -> pack.put("route",
                    routeLifecycleService.getRouteDetail(targetId, null));
            default -> throw new BusinessException(ResultCode.BAD_REQUEST, "未知 scene: " + scene);
        }
        return pack;
    }

    private Map<String, Object> callLlm(Map<String, Object> factPack) {
        throw new UnsupportedOperationException("LLM provider not configured");
    }

    private Map<String, Object> templateFallback(String scene, Map<String, Object> factPack) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scene", scene);
        result.put("source", "template");
        result.put("summary", switch (scene != null ? scene : "") {
            case "landing_brief" -> "当前起降点气象条件总体平稳，建议关注风速与能见度变化后再执行放飞决策。";
            case "l2_warning_detail" -> "预警已触发，请结合规则命中项与现场观测进行处置。";
            case "route_avoidance" -> "航路沿线存在局部不利气象，建议评估绕飞或推迟计划。";
            default -> "暂无 AI 解读，请人工研判。";
        });
        result.put("factPackRef", factPack.get("scene"));
        return result;
    }
}
