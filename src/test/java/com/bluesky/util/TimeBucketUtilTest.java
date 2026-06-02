package com.bluesky.util;

import com.bluesky.common.TemporalMeta;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TimeBucketUtilTest {

    @Test
    void toBucket_floorsToQuarterHour() {
        OffsetDateTime input = OffsetDateTime.parse("2026-06-01T10:37:45+08:00");
        OffsetDateTime bucket = TimeBucketUtil.toBucket(input);
        assertEquals(30, bucket.getMinute());
        assertEquals(0, bucket.getSecond());
    }

    @Test
    void buildMeta_containsBucketAndStaleFlag() {
        OffsetDateTime requested = OffsetDateTime.parse("2026-06-01T10:37:00+08:00");
        OffsetDateTime computedAt = OffsetDateTime.parse("2026-06-01T10:40:00+08:00");
        TemporalMeta meta = TimeBucketUtil.buildMeta(requested, computedAt, true);

        assertEquals(requested, meta.getRequestedTime());
        assertEquals(TimeBucketUtil.toBucket(requested), meta.getBucketTime());
        assertEquals(computedAt, meta.getComputedAt());
        assertTrue(meta.getIsStale());
    }
}
