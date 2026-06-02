package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.dto.RuleSetRequest;
import com.bluesky.service.FlyabilityRuleSetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "适飞规则集")
@RestController
@RequestMapping("/flyability-rule-sets")
@RequiredArgsConstructor
public class FlyabilityRuleSetController {

    private final FlyabilityRuleSetService service;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.success(service.list());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable String id) {
        return Result.success(service.getById(id));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody RuleSetRequest request) {
        return Result.success(service.create(request));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable String id, @Valid @RequestBody RuleSetRequest request) {
        return Result.success(service.update(id, request));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布适飞规则集")
    public Result<Map<String, Object>> publish(@PathVariable String id) {
        return Result.success(service.publish(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.success();
    }
}
