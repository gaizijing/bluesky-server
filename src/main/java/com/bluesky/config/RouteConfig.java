package com.bluesky.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 航线配置
 */
@Configuration
@ConfigurationProperties(prefix = "route")
@Data
public class RouteConfig {
    
    /**
     * 最大历史记录数
     */
    private Integer maxHistoryCount = 5;
    
}