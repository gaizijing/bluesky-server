package com.bluesky.config;

import com.bluesky.entity.RegionConfigEntity;
import com.bluesky.event.RegionConfigEvent;
import com.bluesky.service.RegionConfigService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

/**
 * 地区配置
 */
@Configuration
@Data
@JsonIgnoreProperties(value = {"$$beanFactory", "$$interceptor"}, ignoreUnknown = true)
public class RegionConfig implements ApplicationListener<RegionConfigEvent> {

    @Autowired
    private RegionConfigService regionConfigService;
    
    /**
     * 默认地区名称
     */
    private String defaultName = "青岛";
    
    /**
     * 地区边界
     */
    private Bounds bounds = new Bounds();
    
    /**
     * 初始化配置，从数据库加载
     */
    @PostConstruct
    public void init() {
        try {
            RegionConfigEntity defaultConfig = regionConfigService.getDefaultConfig();
            if (defaultConfig != null) {
                this.defaultName = defaultConfig.getName();
                this.bounds.setWest(defaultConfig.getWest());
                this.bounds.setEast(defaultConfig.getEast());
                this.bounds.setSouth(defaultConfig.getSouth());
                this.bounds.setNorth(defaultConfig.getNorth());
            }
        } catch (Exception e) {
            // 如果数据库中没有配置，使用默认值
            System.err.println("数据库中没有默认地区配置，使用默认值");
        }
    }
    
    /**
     * 监听地区配置更新事件
     */
    @Override
    public void onApplicationEvent(RegionConfigEvent event) {
        RegionConfigEntity config = event.getConfig();
        if (Boolean.TRUE.equals(config.getIsDefault())) {
            this.defaultName = config.getName();
            this.bounds.setWest(config.getWest());
            this.bounds.setEast(config.getEast());
            this.bounds.setSouth(config.getSouth());
            this.bounds.setNorth(config.getNorth());
        }
    }
    
    @Data
    public static class Bounds {
        private double west = 120.0;
        private double east = 121.0;
        private double south = 36.0;
        private double north = 37.0;
    }
    
}