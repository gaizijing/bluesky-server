package com.bluesky.service.flyability;

import java.util.Map;

/** 适飞规则阈值解析：新格式 low/medium/high，兼容旧 yellow/red/min/max。 */
public final class FlyabilityThresholdUtil {

    private FlyabilityThresholdUtil() {
    }

    public enum Direction {
        HIGHER_WORSE,
        LOWER_WORSE,
        RANGE
    }

    public record RangeThresholds(double medium, double high, double low) {
    }

    public static RangeThresholds readRangeThresholds(Map<?, ?> rule, Direction direction) {
        if (rule == null) {
            return new RangeThresholds(0d, 0d, 0d);
        }
        double medium = firstDouble(rule, "medium", "yellow");
        if (direction == Direction.HIGHER_WORSE) {
            double high = firstDouble(rule, "high", "red");
            return new RangeThresholds(medium, high, 0d);
        }
        if (direction == Direction.LOWER_WORSE) {
            double low = firstDouble(rule, "low", "red");
            return new RangeThresholds(medium, 0d, low);
        }
        double low = firstDouble(rule, "low", "min");
        double high = firstDouble(rule, "high", "max");
        return new RangeThresholds(0d, high, low);
    }

    private static double firstDouble(Map<?, ?> rule, String primary, String legacy) {
        Object value = rule.get(primary);
        if (value == null) {
            value = rule.get(legacy);
        }
        return doubleVal(value);
    }

    private static double doubleVal(Object value) {
        if (value == null) {
            return 0d;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0d;
        }
    }
}
