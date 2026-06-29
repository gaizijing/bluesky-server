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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
        entity.setRulesJson(toJson(normalizeRules(coerceRulesForRead(request.getRules()))));
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
    private Map<String, Object> normalizeRules(Map<String, Object> rules) {
        Map<String, Object> coerced = coerceRulesForRead(rules);
        Object l1Raw = coerced.get("l1Rules");
        if (!(l1Raw instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "l1Rules 不能为空");
        }
        List<Map<String, Object>> l1Rules = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> rule)) {
                continue;
            }
            String factor = rule.get("factor") == null ? null : String.valueOf(rule.get("factor")).trim();
            if (factor == null || factor.isBlank()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "每条 l1Rules 须包含 factor");
            }
            String operator = rule.get("operator") == null ? null : String.valueOf(rule.get("operator")).trim();
            if (operator == null || operator.isBlank()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "每条 l1Rules 须包含 operator（gte / lte）");
            }
            if (rule.get("threshold") == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "每条 l1Rules 须包含 threshold");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("factor", factor);
            row.put("operator", operator);
            row.put("threshold", rule.get("threshold"));
            row.put("level", normalizeLevel(rule.get("level")));
            l1Rules.add(row);
        }
        if (l1Rules.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "l1Rules 不能为空");
        }
        return Map.of("l1Rules", l1Rules);
    }

    /** 读取时把旧 rules / l2Rules 转为 l1Rules 展示格式 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> coerceRulesForRead(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of("l1Rules", List.of());
        }
        List<Map<String, Object>> l1Rules = new ArrayList<>();

        Object l1Raw = raw.get("l1Rules");
        if (l1Raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> rule) {
                    l1Rules.add(coerceRuleRow(rule));
                }
            }
        }

        Object legacyRaw = raw.get("rules");
        if (l1Rules.isEmpty() && legacyRaw instanceof List<?> legacy) {
            for (Object item : legacy) {
                if (item instanceof Map<?, ?> rule) {
                    l1Rules.add(convertLegacyRule(rule));
                }
            }
        }

        Object l2Raw = raw.get("l2Rules");
        if (l2Raw instanceof List<?> l2List) {
            for (Object item : l2List) {
                if (item instanceof Map<?, ?> rule) {
                    Map<String, Object> row = coerceRuleRow(rule);
                    row.put("level", "high");
                    l1Rules.add(row);
                }
            }
        }

        if (l1Rules.isEmpty()) {
            return Map.of("l1Rules", List.of());
        }
        return Map.of("l1Rules", l1Rules);
    }

    private Map<String, Object> coerceRuleRow(Map<?, ?> rule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("factor", String.valueOf(rule.get("factor")).trim());
        row.put("operator", rule.get("operator") == null ? "gte" : String.valueOf(rule.get("operator")).trim());
        row.put("threshold", rule.get("threshold"));
        row.put("level", normalizeLevelQuiet(rule.get("level")));
        return row;
    }

    private Map<String, Object> convertLegacyRule(Map<?, ?> rule) {
        String type = rule.get("type") == null ? "" : String.valueOf(rule.get("type")).trim().toUpperCase(Locale.ROOT);
        String factor = switch (type) {
            case "WIND" -> "windSpeedMs";
            case "VISIBILITY" -> "visibilityKm";
            case "PRECIP", "RAIN" -> "precipMmH";
            case "WIND_SHEAR", "WINDSHEAR" -> "windShearMs";
            default -> type.isBlank() ? "windSpeedMs" : type.toLowerCase(Locale.ROOT);
        };
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("factor", factor);
        row.put("operator", "gte");
        row.put("threshold", rule.get("threshold"));
        row.put("level", "high");
        return row;
    }

    private String normalizeLevelQuiet(Object raw) {
        try {
            return normalizeLevel(raw);
        } catch (BusinessException e) {
            return "medium";
        }
    }

    private String normalizeLevel(Object raw) {
        if (raw == null) {
            return "medium";
        }
        String level = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        return switch (level) {
            case "high", "red" -> "high";
            case "low", "green" -> "low";
            case "medium", "yellow" -> "medium";
            default -> {
                if (!Set.of("high", "medium", "low").contains(level)) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "level 须为 high / medium / low");
                }
                yield level;
            }
        };
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
            Map<String, Object> parsed = objectMapper.readValue(entity.getRulesJson(), Map.class);
            map.put("rules", coerceRulesForRead(parsed));
        } catch (JsonProcessingException e) {
            map.put("rules", entity.getRulesJson());
        }
        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getUpdatedAt());
        return map;
    }
}
