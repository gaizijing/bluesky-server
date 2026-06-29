package com.bluesky.service.flyability;

import com.bluesky.enums.FlyabilityLevel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.bluesky.service.flyability.FlyabilityThresholdUtil.Direction;
import static com.bluesky.service.flyability.FlyabilityThresholdUtil.RangeThresholds;
import static com.bluesky.service.flyability.FlyabilityThresholdUtil.readRangeThresholds;

@Component
@RequiredArgsConstructor
public class FlyabilityCalculator {

    private final ObjectMapper objectMapper;

    public Map<String, Object> evaluate(String rulesJson, Map<String, Object> weather) {
        Map<String, Object> rules = parseRules(rulesJson);
        List<Map<String, Object>> factorResults = new ArrayList<>();
        FlyabilityLevel aggregate = FlyabilityLevel.GREEN;

        aggregate = FlyabilityLevel.max(aggregate, evaluateRangeFactor(factorResults, "windSpeedMs", "风速",
                doubleVal(weather.get("windSpeed")), rules.get("windSpeedMs"), Direction.HIGHER_WORSE));
        aggregate = FlyabilityLevel.max(aggregate, evaluateRangeFactor(factorResults, "windShearMs", "风切变",
                doubleVal(weather.get("windShearMs")), rules.get("windShearMs"), Direction.HIGHER_WORSE));
        aggregate = FlyabilityLevel.max(aggregate, evaluateRangeFactor(factorResults, "turbulenceIndex", "颠簸指数",
                doubleVal(weather.get("turbulenceIndex")), rules.get("turbulenceIndex"), Direction.HIGHER_WORSE));
        aggregate = FlyabilityLevel.max(aggregate, evaluateRangeFactor(factorResults, "turbulence", "湍流",
                doubleVal(weather.get("turbulence")), rules.get("turbulence"), Direction.HIGHER_WORSE));
        aggregate = FlyabilityLevel.max(aggregate, evaluateRangeFactor(factorResults, "visibilityKm", "能见度",
                doubleVal(weather.get("visibility")), rules.get("visibilityKm"), Direction.LOWER_WORSE));
        aggregate = FlyabilityLevel.max(aggregate, evaluateRangeFactor(factorResults, "precipMmH", "降水",
                doubleVal(weather.get("precipitation")), rules.get("precipMmH"), Direction.HIGHER_WORSE));
        aggregate = FlyabilityLevel.max(aggregate, evaluateTempFactor(factorResults, doubleVal(weather.get("temperature")), rules.get("temperatureC")));
        aggregate = FlyabilityLevel.max(aggregate, evaluateRangeFactor(factorResults, "cloudBaseM", "云底高度",
                doubleVal(weather.get("cloudBase")), rules.get("cloudBaseM"), Direction.LOWER_WORSE));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", aggregate.name());
        result.put("factorResults", factorResults);
        return result;
    }

    private FlyabilityLevel evaluateRangeFactor(List<Map<String, Object>> out, String key, String label,
                                                double value, Object ruleObj, Direction direction) {
        Map<String, Object> factor = new LinkedHashMap<>();
        factor.put("factor", key);
        factor.put("label", label);
        factor.put("value", value);
        FlyabilityLevel level = FlyabilityLevel.GREEN;
        if (ruleObj instanceof Map<?, ?> rule) {
            RangeThresholds thresholds = readRangeThresholds(rule, direction);
            if (direction == Direction.HIGHER_WORSE) {
                if (thresholds.high() > 0 && value >= thresholds.high()) {
                    level = FlyabilityLevel.RED;
                } else if (thresholds.medium() > 0 && value >= thresholds.medium()) {
                    level = FlyabilityLevel.YELLOW;
                }
                factor.put("thresholdMedium", thresholds.medium());
                factor.put("thresholdHigh", thresholds.high());
            } else {
                if (thresholds.low() > 0 && value <= thresholds.low()) {
                    level = FlyabilityLevel.RED;
                } else if (thresholds.medium() > 0 && value <= thresholds.medium()) {
                    level = FlyabilityLevel.YELLOW;
                }
                factor.put("thresholdMedium", thresholds.medium());
                factor.put("thresholdLow", thresholds.low());
            }
        }
        factor.put("level", level.name());
        out.add(factor);
        return level;
    }

    private FlyabilityLevel evaluateTempFactor(List<Map<String, Object>> out, double value, Object ruleObj) {
        Map<String, Object> factor = new LinkedHashMap<>();
        factor.put("factor", "temperatureC");
        factor.put("label", "温度");
        factor.put("value", value);
        FlyabilityLevel level = FlyabilityLevel.GREEN;
        if (ruleObj instanceof Map<?, ?> rule) {
            RangeThresholds thresholds = readRangeThresholds(rule, Direction.RANGE);
            if ((thresholds.low() != 0 || thresholds.high() != 0)
                    && (value < thresholds.low() || value > thresholds.high())) {
                level = FlyabilityLevel.RED;
            }
            factor.put("thresholdLow", thresholds.low());
            factor.put("thresholdHigh", thresholds.high());
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
