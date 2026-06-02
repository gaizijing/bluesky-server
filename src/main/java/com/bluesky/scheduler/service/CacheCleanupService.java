package com.bluesky.scheduler.service;

import com.bluesky.scheduler.config.SchedulerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheCleanupService {

    private final JdbcTemplate jdbcTemplate;
    private final SchedulerProperties properties;

    public Map<String, Integer> cleanupAll() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cacheCutoff = now.minusDays(properties.getCacheRetentionDays());
        LocalDateTime warningCutoff = now.minusDays(properties.getWarningRetentionDays());
        LocalDateTime aiCutoff = now.minusDays(properties.getAiConclusionRetentionDays());

        Map<String, Integer> totals = new LinkedHashMap<>();
        totals.put("weather_grid_cache", purgeTable(
                "weather_grid_cache", "cache_id",
                "bucket_time < ? OR (expires_at IS NOT NULL AND expires_at < ?)",
                cacheCutoff, now));
        totals.put("osi_landing_cache", purgeTable(
                "osi_landing_cache", "cache_id", "bucket_time < ?", cacheCutoff));
        totals.put("osi_route_cache", purgeTable(
                "osi_route_cache", "cache_id", "bucket_time < ?", cacheCutoff));
        totals.put("risk_field_cache", purgeTable(
                "risk_field_cache", "cache_id", "bucket_time < ?", cacheCutoff));
        totals.put("warning_handle_records", purgeWarningHandles(warningCutoff));
        totals.put("warning_records", purgeTable(
                "warning_records", "warning_id", "created_at < ?", warningCutoff));
        totals.put("ai_conclusion_cache", purgeTable(
                "ai_conclusion_cache", "conclusion_id", "created_at < ?", aiCutoff));

        log.info("缓存清理完成: {}", totals);
        return totals;
    }

    private int purgeWarningHandles(LocalDateTime warningCutoff) {
        int batch = properties.getCleanupBatchSize();
        int total = 0;
        int deleted;
        do {
            deleted = jdbcTemplate.update(
                    "DELETE FROM warning_handle_records WHERE id IN ("
                            + "SELECT h.id FROM warning_handle_records h "
                            + "INNER JOIN warning_records w ON w.warning_id = h.warning_id "
                            + "WHERE w.created_at < ? LIMIT " + batch + ")",
                    warningCutoff);
            total += deleted;
        } while (deleted >= batch);
        return total;
    }

    private int purgeTable(String table, String idColumn, String whereClause, LocalDateTime... params) {
        int batch = properties.getCleanupBatchSize();
        int total = 0;
        int deleted;
        do {
            if (params.length == 2) {
                deleted = jdbcTemplate.update(
                        "DELETE FROM " + table + " WHERE " + idColumn + " IN ("
                                + "SELECT " + idColumn + " FROM " + table + " WHERE " + whereClause
                                + " LIMIT " + batch + ")",
                        params[0], params[1]);
            } else {
                deleted = jdbcTemplate.update(
                        "DELETE FROM " + table + " WHERE " + idColumn + " IN ("
                                + "SELECT " + idColumn + " FROM " + table + " WHERE " + whereClause
                                + " LIMIT " + batch + ")",
                        params[0]);
            }
            total += deleted;
        } while (deleted >= batch);
        return total;
    }
}
