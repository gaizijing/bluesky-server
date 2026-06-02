package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.common.ResultCode;
import com.bluesky.dto.RuleSetRequest;
import com.bluesky.entity.WarningRuleSet;
import com.bluesky.enums.RuleSetStatus;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.WarningRuleSetMapper;
import com.bluesky.scheduler.RuleType;
import com.bluesky.scheduler.service.RulePublishNotifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarningRuleSetService {

    private final WarningRuleSetMapper mapper;
    private final ObjectMapper objectMapper;
    private final RulePublishNotifier rulePublishNotifier;

    public List<Map<String, Object>> list() {
        return mapper.selectList(new LambdaQueryWrapper<WarningRuleSet>()
                        .orderByDesc(WarningRuleSet::getUpdatedAt))
                .stream().map(this::toMap).toList();
    }

    public Map<String, Object> getById(String id) {
        return toMap(require(id));
    }

    @Transactional
    public Map<String, Object> create(RuleSetRequest request) {
        WarningRuleSet entity = new WarningRuleSet();
        entity.setRuleSetId("WS" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        applyRequest(entity, request);
        entity.setVersionNo(1);
        entity.setStatus(RuleSetStatus.DRAFT.name());
        entity.setEnableLlm(Boolean.TRUE.equals(request.getEnableLlm()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return toMap(entity);
    }

    @Transactional
    public Map<String, Object> update(String id, RuleSetRequest request) {
        WarningRuleSet entity = require(id);
        applyRequest(entity, request);
        if (request.getEnableLlm() != null) {
            entity.setEnableLlm(request.getEnableLlm());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        return toMap(entity);
    }

    @Transactional
    public Map<String, Object> publish(String id) {
        WarningRuleSet entity = require(id);
        entity.setStatus(RuleSetStatus.PUBLISHED.name());
        entity.setEffectiveFrom(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        rulePublishNotifier.notifyPublished(RuleType.WARNING, entity.getRuleSetId());
        return toMap(entity);
    }

    @Transactional
    public Map<String, Object> enableLlm(String id, boolean enabled) {
        WarningRuleSet entity = require(id);
        entity.setEnableLlm(enabled);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        return toMap(entity);
    }

    @Transactional
    public void delete(String id) {
        WarningRuleSet entity = require(id);
        if (RuleSetStatus.PUBLISHED.name().equals(entity.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已发布规则集不可删除");
        }
        mapper.deleteById(id);
    }

    private WarningRuleSet require(String id) {
        WarningRuleSet entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预警规则集不存在: " + id);
        }
        return entity;
    }

    private void applyRequest(WarningRuleSet entity, RuleSetRequest request) {
        entity.setName(request.getName());
        entity.setRulesJson(toJson(request.getRules()));
    }

    private String toJson(Map<String, Object> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "rules 不能为空");
        }
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "rules 格式错误");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(WarningRuleSet entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ruleSetId", entity.getRuleSetId());
        map.put("name", entity.getName());
        map.put("versionNo", entity.getVersionNo());
        map.put("status", entity.getStatus());
        map.put("effectiveFrom", entity.getEffectiveFrom());
        map.put("effectiveTo", entity.getEffectiveTo());
        map.put("enableLlm", entity.getEnableLlm());
        try {
            map.put("rules", objectMapper.readValue(entity.getRulesJson(), Map.class));
        } catch (JsonProcessingException e) {
            map.put("rules", entity.getRulesJson());
        }
        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getUpdatedAt());
        return map;
    }
}
