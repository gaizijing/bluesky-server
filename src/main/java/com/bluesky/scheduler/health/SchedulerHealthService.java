package com.bluesky.scheduler.health;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SchedulerHealthService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> snapshot() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("weatherGridByRegion", queryList("""
                SELECT region_id AS "regionId",
                       MAX(bucket_time) AS "lastBucketTime",
                       MAX(computed_at) AS "lastComputedAt",
                       COUNT(*) AS "rowCount"
                FROM weather_grid_cache
                GROUP BY region_id
                ORDER BY region_id
                """));
        body.put("osiLanding", queryList("""
                SELECT MAX(bucket_time) AS "lastBucketTime",
                       MAX(computed_at) AS "lastComputedAt",
                       COUNT(*) AS "rowCount"
                FROM osi_landing_cache
                """));
        body.put("riskFieldByRegion", queryList("""
                SELECT region_id AS "regionId",
                       MAX(bucket_time) AS "lastBucketTime",
                       MAX(computed_at) AS "lastComputedAt",
                       COUNT(*) AS "rowCount"
                FROM risk_field_cache
                GROUP BY region_id
                ORDER BY region_id
                """));
        return body;
    }

    private List<Map<String, Object>> queryList(String sql) {
        return jdbcTemplate.queryForList(sql);
    }
}
