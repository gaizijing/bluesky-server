package com.bluesky.util;

import com.bluesky.common.TemporalMeta;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class TimeBucketUtil {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final int BUCKET_MINUTES = 15;

    private TimeBucketUtil() {
    }

    public static OffsetDateTime now() {
        return OffsetDateTime.now(ZONE);
    }

    public static OffsetDateTime parseOrNow(String time) {
        if (time == null || time.isBlank()) {
            return now();
        }
        String normalized = time.trim().replace(' ', '+');
        return OffsetDateTime.parse(normalized);
    }

    public static OffsetDateTime toBucket(OffsetDateTime time) {
        LocalDateTime local = time.atZoneSameInstant(ZONE).toLocalDateTime();
        int minute = local.getMinute();
        int floored = (minute / BUCKET_MINUTES) * BUCKET_MINUTES;
        LocalDateTime bucket = local.withMinute(floored).withSecond(0).withNano(0);
        return bucket.atZone(ZONE).toOffsetDateTime();
    }

    public static TemporalMeta buildMeta(OffsetDateTime requestedTime,
                                         OffsetDateTime computedAt,
                                         boolean isStale) {
        TemporalMeta meta = new TemporalMeta();
        meta.setRequestedTime(requestedTime);
        meta.setBucketTime(toBucket(requestedTime));
        meta.setComputedAt(computedAt != null ? computedAt : now());
        meta.setIsStale(isStale);
        return meta;
    }

    public static OffsetDateTime plusBuckets(OffsetDateTime bucket, int buckets) {
        return bucket.plus((long) buckets * BUCKET_MINUTES, ChronoUnit.MINUTES);
    }
}
