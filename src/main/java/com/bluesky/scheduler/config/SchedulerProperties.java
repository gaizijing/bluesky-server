package com.bluesky.scheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "scheduler")
public class SchedulerProperties {

    /** 是否启用定时调度 */
    private boolean enabled = true;

    /** 格点缓存 cron（默认每 15min 第 2 分钟） */
    private String gridCron = "0 2,17,32,47 * * * *";

    private int gridRows = 4;
    private int gridCols = 4;

    private List<Integer> heights = List.of(100);

    private List<String> products = List.of("temperature", "wind", "visibility", "precip");

    /** 格点采样间隔（毫秒），避免和风 QPS 过高 */
    private long gridSampleIntervalMs = 1500;

    /** 每日清理 cron（默认 02:00） */
    private String cleanupCron = "0 0 2 * * *";

    private int cacheRetentionDays = 7;
    private int warningRetentionDays = 180;
    private int aiConclusionRetentionDays = 30;
    private int cleanupBatchSize = 10000;

    /** 规则发布后重算的时间桶数量（默认 4 桶 = 1h，对齐 landing-matrix hours=1） */
    private int recomputeBuckets = 4;
}
