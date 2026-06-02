package com.bluesky.enums;

public enum WarningStatus {
    NEW,
    ACKNOWLEDGED,
    HANDLED,
    CLOSED;

    public static WarningStatus parse(String value) {
        return WarningStatus.valueOf(value);
    }
}
