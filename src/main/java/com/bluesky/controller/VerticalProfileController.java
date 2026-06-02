package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.service.WeatherQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * @deprecated 请使用 GET /weather/vertical-profile
 */
@Deprecated
@Tag(name = "垂直剖面", description = "已迁移至 /weather/vertical-profile")
@RestController
@RequestMapping("/meteorology/vertical-profile")
@RequiredArgsConstructor
public class VerticalProfileController {

    private final WeatherQueryService weatherQueryService;

    @Operation(summary = "获取垂直剖面数据（兼容旧路径）")
    @GetMapping
    public Result<Map<String, Object>> getVerticalProfile(
            @Parameter(description = "起降点 ID") @RequestParam String pointId,
            @Parameter(description = "时间类型: current/1h/3h/6h") @RequestParam(required = false, defaultValue = "current") String timeType) {
        String startTime = switch (timeType != null ? timeType : "current") {
            case "1h" -> java.time.OffsetDateTime.now().minusHours(1).toString();
            case "3h" -> java.time.OffsetDateTime.now().minusHours(3).toString();
            case "6h" -> java.time.OffsetDateTime.now().minusHours(6).toString();
            default -> null;
        };
        return Result.success(weatherQueryService.queryVerticalProfile(pointId, startTime, null, null));
    }
}
