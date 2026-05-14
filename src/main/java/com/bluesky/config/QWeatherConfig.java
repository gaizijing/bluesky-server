package com.bluesky.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 和风天气API配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "qweather")
public class QWeatherConfig {
    
    /**
     * API密钥
     */
    private String apiKey;
    
    /**
     * API基础URL
     */
    private String baseUrl = "https://api.qweather.com";
}
