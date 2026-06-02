package com.bluesky.enums;

public enum RuleSetStatus {
    DRAFT,
    PUBLISHED;

    public static RuleSetStatus parse(String value) {
        return RuleSetStatus.valueOf(value);
    }
}
