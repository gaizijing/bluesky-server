package com.bluesky.service.risk;

import com.bluesky.service.flyability.FlyabilityThresholdUtil;
import com.bluesky.service.flyability.FlyabilityThresholdUtil.Direction;
import com.bluesky.service.flyability.FlyabilityThresholdUtil.RangeThresholds;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * R_met 综合风险：阈值与适飞规则集共用，按权重加权求和（算法与适飞 max 分档分离）。
 */
@Component
@RequiredArgsConstructor
public class RiskMetCalculator {

    private final ObjectMapper objectMapper;

    private static final Map<String, String> LEGACY_FACTOR_ALIASES = Map.of(
            "wind", "windSpeedMs",
            "windShear", "windShearMs",
            "visibility", "visibilityKm",
            "precip", "precipMmH",
            "cloudBase", "cloudBaseM",
            "temperature", "temperatureC"
    );

    private static final Map<String, String> WEATHER_KEYS = Map.ofEntries(
            Map.entry("windSpeedMs", "windSpeed"),
            Map.entry("windShearMs", "windShearMs"),
            Map.entry("visibilityKm", "visibility"),
            Map.entry("precipMmH", "precipitation"),
            Map.entry("turbulenceIndex", "turbulenceIndex"),
            Map.entry("turbulence", "turbulence"),
            Map.entry("cloudBaseM", "cloudBase"),
            Map.entry("temperatureC", "temperature")
    );

    private static final Map<String, FactorKind> FACTOR_KINDS = Map.ofEntries(
            Map.entry("windSpeedMs", FactorKind.HIGHER_WORSE),
            Map.entry("windShearMs", FactorKind.HIGHER_WORSE),
            Map.entry("visibilityKm", FactorKind.LOWER_WORSE),
            Map.entry("precipMmH", FactorKind.HIGHER_WORSE),
            Map.entry("turbulenceIndex", FactorKind.HIGHER_WORSE),
            Map.entry("turbulence", FactorKind.HIGHER_WORSE),
            Map.entry("cloudBaseM", FactorKind.LOWER_WORSE),
            Map.entry("temperatureC", FactorKind.RANGE)
    );

    private static final Map<String, String> FACTOR_LABELS = Map.ofEntries(
            Map.entry("windSpeedMs", "风速"),
            Map.entry("windShearMs", "风切变"),
            Map.entry("visibilityKm", "能见度"),
            Map.entry("precipMmH", "降水"),
            Map.entry("turbulenceIndex", "颠簸指数"),
            Map.entry("turbulence", "湍流"),
            Map.entry("cloudBaseM", "云底高度"),
            Map.entry("temperatureC", "温度")
    );

    private static final Map<String, Object> DEFAULT_R_MET_CONFIG = Map.of(
            "factors", List.of(
                    Map.of("name", "windSpeedMs", "weight", 0.4),
                    Map.of("name", "windShearMs", "weight", 0.3),
                    Map.of("name", "visibilityKm", "weight", 0.3)
            ),
            "outputCap", 100
    );

    /** 阈值与 R_met 权重均在适飞规则 JSON：顶层为阈值，{@code rMet} 为加权配置。 */
    public Map<String, Object> evaluate(String flyabilityRulesJson, Map<String, Object> weather) {
        return evaluate(flyabilityRulesJson, null, weather);
    }

    /** @deprecated 保留第二参数以兼容旧 risk_rule_set，新数据请写入 flyability rules 的 rMet 段 */
    public Map<String, Object> evaluate(String flyabilityRulesJson, String legacyRiskRulesJson,
                                        Map<String, Object> weather) {
        Map<String, Object> allRules = parseRules(flyabilityRulesJson);
        Map<String, Object> riskConfig = resolveRiskConfig(allRules, legacyRiskRulesJson);
        List<Map<String, Object>> factorDefs = castFactorList(riskConfig.get("factors"));
        double outputCap = doubleVal(riskConfig.get("outputCap"), 100d);

        double weightSum = 0d;
        double weightedScore = 0d;
        List<Map<String, Object>> factorResults = new ArrayList<>();
        String topReason = "综合风险一般";
        double topContribution = 0d;

        for (Map<String, Object> def : factorDefs) {
            String factorKey = normalizeFactorKey(String.valueOf(def.get("name")));
            double weight = doubleVal(def.get("weight"), 0d);
            if (weight <= 0d || factorKey == null) {
                continue;
            }

            double value = readWeatherValue(factorKey, weather);
            double factorScore = scoreFactor(factorKey, value, allRules.get(factorKey));
            double contribution = weight * factorScore;
            weightSum += weight;
            weightedScore += contribution;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("factor", factorKey);
            row.put("label", FACTOR_LABELS.getOrDefault(factorKey, factorKey));
            row.put("value", value);
            row.put("weight", weight);
            row.put("score", round2(factorScore));
            row.put("contribution", round2(contribution));
            factorResults.add(row);

            if (contribution > topContribution && factorScore >= 0.5d) {
                topContribution = contribution;
                topReason = FACTOR_LABELS.getOrDefault(factorKey, factorKey) + riskReasonSuffix(factorScore);
            }
        }

        double normalized = weightSum > 0d ? weightedScore / weightSum : 0d;
        double value = Math.min(outputCap, Math.max(0d, normalized * outputCap));
        String level = value >= 70d ? "HIGH" : value >= 40d ? "MEDIUM" : "LOW";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", round2(value));
        result.put("level", level);
        result.put("reason", topReason);
        result.put("factorResults", factorResults);
        return result;
    }

    private enum FactorKind {
        HIGHER_WORSE, LOWER_WORSE, RANGE
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveRiskConfig(Map<String, Object> allRules, String legacyRiskRulesJson) {
        Object rMet = allRules.get("rMet");
        if (rMet instanceof Map<?, ?> map) {
            Map<String, Object> config = new LinkedHashMap<>();
            map.forEach((k, v) -> config.put(String.valueOf(k), v));
            return config;
        }
        if (legacyRiskRulesJson != null && !legacyRiskRulesJson.isBlank()) {
            Map<String, Object> legacy = parseRules(legacyRiskRulesJson);
            if (!legacy.isEmpty()) {
                return legacy;
            }
        }
        return DEFAULT_R_MET_CONFIG;
    }

    private String normalizeFactorKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim();
        return LEGACY_FACTOR_ALIASES.getOrDefault(key, key);
    }

    private double readWeatherValue(String factorKey, Map<String, Object> weather) {
        String weatherKey = WEATHER_KEYS.get(factorKey);
        if (weatherKey == null || weather == null) {
            return 0d;
        }
        return doubleVal(weather.get(weatherKey), 0d);
    }

    @SuppressWarnings("unchecked")
    private double scoreFactor(String factorKey, double value, Object ruleObj) {
        FactorKind kind = FACTOR_KINDS.get(factorKey);
        if (kind == null || !(ruleObj instanceof Map<?, ?> rule)) {
            return 0d;
        }
        return switch (kind) {
            case HIGHER_WORSE -> scoreHigherWorse(value, rule);
            case LOWER_WORSE -> scoreLowerWorse(value, rule);
            case RANGE -> scoreTemperatureRange(value, rule);
        };
    }

    private double scoreHigherWorse(double value, Map<?, ?> rule) {
        RangeThresholds t = FlyabilityThresholdUtil.readRangeThresholds(rule, Direction.HIGHER_WORSE);
        double medium = t.medium();
        double high = t.high();
        if (medium <= 0d && high <= 0d) {
            return 0d;
        }
        if (high > 0d && value >= high) {
            return 1d;
        }
        if (medium > 0d && value >= medium) {
            if (high > medium) {
                return 0.5d + 0.5d * (value - medium) / (high - medium);
            }
            return 0.75d;
        }
        if (medium > 0d) {
            return 0.5d * Math.min(1d, Math.max(0d, value / medium));
        }
        return 0d;
    }

    private double scoreLowerWorse(double value, Map<?, ?> rule) {
        RangeThresholds t = FlyabilityThresholdUtil.readRangeThresholds(rule, Direction.LOWER_WORSE);
        double medium = t.medium();
        double low = t.low();
        if (medium <= 0d && low <= 0d) {
            return 0d;
        }
        if (low > 0d && value <= low) {
            return 1d;
        }
        if (medium > 0d && value <= medium) {
            if (medium > low) {
                return 0.5d + 0.5d * (medium - value) / (medium - low);
            }
            return 0.75d;
        }
        return 0d;
    }

    private double scoreTemperatureRange(double value, Map<?, ?> rule) {
        RangeThresholds t = FlyabilityThresholdUtil.readRangeThresholds(rule, Direction.RANGE);
        if ((t.low() != 0d || t.high() != 0d) && (value < t.low() || value > t.high())) {
            return 1d;
        }
        return 0d;
    }

    private String riskReasonSuffix(double factorScore) {
        if (factorScore >= 0.85d) {
            return "偏高";
        }
        if (factorScore >= 0.5d) {
            return "偏大";
        }
        return "一般";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castFactorList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((k, v) -> row.put(String.valueOf(k), v));
                out.add(row);
            }
        }
        return out;
    }

    private Map<String, Object> parseRules(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private double doubleVal(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
