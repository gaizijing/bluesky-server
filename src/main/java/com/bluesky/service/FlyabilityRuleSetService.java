package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.common.ResultCode;
import com.bluesky.dto.RuleSetRequest;
import com.bluesky.entity.FlyabilityRuleSet;
import com.bluesky.enums.RuleSetStatus;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.FlyabilityRuleSetMapper;
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
public class FlyabilityRuleSetService {

    private final FlyabilityRuleSetMapper mapper;
    private final ObjectMapper objectMapper;
    private final RulePublishNotifier rulePublishNotifier;

    public List<Map<String, Object>> list() {
        return mapper.selectList(new LambdaQueryWrapper<FlyabilityRuleSet>()
                        .orderByDesc(FlyabilityRuleSet::getUpdatedAt))
                .stream().map(this::toMap).toList();
    }

    public Map<String, Object> getById(String id) {
        return toMap(require(id));
    }

    public FlyabilityRuleSet getPublished() {
        FlyabilityRuleSet published = mapper.selectLatestPublished();
        if (published == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "无已发布的适飞规则集");
        }
        return published;
    }

    @Transactional
    public Map<String, Object> create(RuleSetRequest request) {
        FlyabilityRuleSet entity = new FlyabilityRuleSet();
        entity.setRuleSetId("FS" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        applyRequest(entity, request);
        entity.setVersionNo(1);
        entity.setStatus(RuleSetStatus.DRAFT.name());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return toMap(entity);
    }

    @Transactional
    public Map<String, Object> update(String id, RuleSetRequest request) {
        FlyabilityRuleSet entity = require(id);
        applyRequest(entity, request);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        return toMap(entity);
    }

    @Transactional
    public Map<String, Object> publish(String id) {
        FlyabilityRuleSet entity = require(id);
        LocalDateTime now = LocalDateTime.now();
        retirePreviousPublished(id, now);
        entity.setVersionNo(mapper.selectMaxPublishedVersionNo() + 1);
        entity.setStatus(RuleSetStatus.PUBLISHED.name());
        entity.setEffectiveFrom(now);
        entity.setEffectiveTo(null);
        entity.setUpdatedAt(now);
        mapper.updateById(entity);
        rulePublishNotifier.notifyPublished(RuleType.FLYABILITY, entity.getRuleSetId());
        return toMap(entity);
    }

    /** 结束其它已发布规则的生效期，线上仅保留 effective_from 最新的一条 */
    private void retirePreviousPublished(String excludeId, LocalDateTime now) {
        List<FlyabilityRuleSet> others = mapper.selectList(new LambdaQueryWrapper<FlyabilityRuleSet>()
                .eq(FlyabilityRuleSet::getStatus, RuleSetStatus.PUBLISHED.name())
                .ne(FlyabilityRuleSet::getRuleSetId, excludeId)
                .isNull(FlyabilityRuleSet::getEffectiveTo));
        for (FlyabilityRuleSet old : others) {
            old.setEffectiveTo(now);
            old.setUpdatedAt(now);
            mapper.updateById(old);
        }
    }

    @Transactional
    public void delete(String id) {
        FlyabilityRuleSet entity = require(id);
        if (RuleSetStatus.PUBLISHED.name().equals(entity.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已发布规则集不可删除");
        }
        mapper.deleteById(id);
    }

    private FlyabilityRuleSet require(String id) {
        FlyabilityRuleSet entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "适飞规则集不存在: " + id);
        }
        return entity;
    }

    private void applyRequest(FlyabilityRuleSet entity, RuleSetRequest request) {
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
    private Map<String, Object> toMap(FlyabilityRuleSet entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ruleSetId", entity.getRuleSetId());
        map.put("name", entity.getName());
        map.put("versionNo", entity.getVersionNo());
        map.put("status", entity.getStatus());
        map.put("effectiveFrom", entity.getEffectiveFrom());
        map.put("effectiveTo", entity.getEffectiveTo());
        map.put("publishedAt", entity.getEffectiveFrom());
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
