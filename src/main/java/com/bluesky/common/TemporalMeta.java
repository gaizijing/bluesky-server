package com.bluesky.common;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class TemporalMeta {
    private OffsetDateTime requestedTime;
    private OffsetDateTime bucketTime;
    private OffsetDateTime computedAt;
    private OffsetDateTime dataSourceTime;
    private OffsetDateTime expiresAt;
    private Boolean isStale;
}
