package com.bluesky.enums;

public enum FlyabilityLevel {
    GREEN,
    YELLOW,
    RED;

    public static FlyabilityLevel max(FlyabilityLevel a, FlyabilityLevel b) {
        if (a == RED || b == RED) return RED;
        if (a == YELLOW || b == YELLOW) return YELLOW;
        return GREEN;
    }
}
