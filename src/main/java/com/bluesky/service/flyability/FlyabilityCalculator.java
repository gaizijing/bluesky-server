package com.bluesky.service.flyability;

import com.bluesky.enums.FlyabilityLevel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class FlyabilityCalculator {

    private final ObjectMapper objectMapper;

    public Map<String, Object> evaluate(String rulesJson, Map<String, Object> weather) {
        Map<String, Object> rules = parseRules(rulesJson);
        List<Map<String, Object>> factorResults = new ArrayList<>();
        FlyabilityLevel aggregate = FlyabilityLevel.GREEN;

        aggregate = FlyabilityLevel.max(aggregate, evaluateRangeFactor(factorResults, "windSpeedMs", "风速",
                doubleVal(weather.get("windSpeed")), rules.get("windSpeedMs"), ThresholdDirection.HIGHER_WORSE));
        aggregate = FlyabilityLevel.max(aggregate, evaluateRangeFactor(factorResults, "windShearMs", "风切变",
                doubleVal(weather.get("windShearMs")), rules.get("windShearMs"), ThresholdDirection.HIGHER_WORSE));
        aggregate = FlyabilityLevel.max(aggregate, evaluateRangeFactor(factorResults, "turbulenceIndex", "颠簸指数",
                doubleVal(weather.get("turbulenceIndex")), rules.get("turbulenceIndex"), ThresholdDirection.HIGHER_WORSE));
        aggregate = FlyabilityLevel.max(aggregate, evaluateRangeFactor(factorResults, "turbulence", "湍流",
                doubleVal(weather.get("turbulence")), rules.get("turbulence"), ThresholdDirection.HIGHER_WORSE));
        aggregate = FlyabilityLevel.max(aggregate, evaluateRangeFactor(factorResults, "visibilityKm", "能见度",
                doubleVal(weather.get("visibility")), rules.get("visibilityKm"), ThresholdDirection.LOWER_WORSE));
        aggregate = FlyabilityLevel.max(aggregate, evaluateRangeFactor(factorResults, "precipMmH", "降水",
                doubleVal(weather.get("precipitation")), rules.get("precipMmH"), ThresholdDirection.HIGHER_WORSE));
        aggregate = FlyabilityLevel.max(aggregate, evaluateTempFactor(factorResults, doubleVal(weather.get("temperature")), rules.get("temperatureC")));
        aggregate = FlyabilityLevel.max(aggregate, evaluateRangeFactor(factorResults, "cloudBaseM", "云底高度",
                doubleVal(weather.get("cloudBase")), rules.get("cloudBaseM"), ThresholdDirection.LOWER_WORSE));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", aggregate.name());
        result.put("factorResults", factorResults);
        return result;
    }

    private enum ThresholdDirection {
        /** 数值越大越危险，如风速、降水 */
        HIGHER_WORSE,
        /** 数值越小越危险，如能见度、云底高度 */
        LOWER_WORSE
    }

    @SuppressWarnings("unchecked")
    private FlyabilityLevel evaluateRangeFactor(List<Map<String, Object>> out, String key, String label,
                                                double value, Object ruleObj, ThresholdDirection direction) {
        Map<String, Object> factor = new LinkedHashMap<>();
        factor.put("factor", key);
        factor.put("label", label);
        factor.put("value", value);
        FlyabilityLevel level = FlyabilityLevel.GREEN;
        if (ruleObj instanceof Map<?, ?> rule) {
            double yellow = doubleVal(rule.get("yellow"));
            double red = doubleVal(rule.get("red"));
            if (direction == ThresholdDirection.HIGHER_WORSE) {
                if (value >= red && red > 0) {
                    level = FlyabilityLevel.RED;
                } else if (value >= yellow && yellow > 0) {
                    level = FlyabilityLevel.YELLOW;
                }
            } else {
                if (red > 0 && value <= red) {
                    level = FlyabilityLevel.RED;
                } else if (yellow > 0 && value <= yellow) {
                    level = FlyabilityLevel.YELLOW;
                }
            }
            factor.put("thresholdYellow", yellow);
            factor.put("thresholdRed", red);
        }
        factor.put("level", level.name());
        out.add(factor);
        return level;
    }

    @SuppressWarnings("unchecked")
    private FlyabilityLevel evaluateTempFactor(List<Map<String, Object>> out, double value, Object ruleObj) {
        Map<String, Object> factor = new LinkedHashMap<>();
        factor.put("factor", "temperatureC");
        factor.put("label", "温度");
        factor.put("value", value);
        FlyabilityLevel level = FlyabilityLevel.GREEN;
        if (ruleObj instanceof Map<?, ?> rule) {
            double min = doubleVal(rule.get("min"));
            double max = doubleVal(rule.get("max"));
            if ((min != 0 || max != 0) && (value < min || value > max)) {
                level = FlyabilityLevel.RED;
            }
            factor.put("min", min);
            factor.put("max", max);
        }
        factor.put("level", level.name());
        out.add(factor);
        return level;
    }

    private Map<String, Object> parseRules(String rulesJson) {
        try {
            return objectMapper.readValue(rulesJson, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private double doubleVal(Object value) {
        if (value == null) return 0d;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0d;
        }
    }
}
