package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.service.AiConclusionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "AI 解读")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiConclusionController {

    private final AiConclusionService aiConclusionService;

    @PostMapping("/conclusion")
    @Operation(summary = "生成 AI 解读（超时降级模板）")
    public Result<Map<String, Object>> conclusion(
            @RequestParam String scene,
            @RequestParam String regionId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String time) {
        return Result.success(aiConclusionService.generate(scene, regionId, targetType, targetId, time));
    }
}
