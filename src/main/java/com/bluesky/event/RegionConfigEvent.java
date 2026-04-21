package com.bluesky.event;

import com.bluesky.entity.RegionConfigEntity;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 地区配置更新事件
 */
@Getter
public class RegionConfigEvent extends ApplicationEvent {
    
    private final RegionConfigEntity config;
    
    public RegionConfigEvent(Object source, RegionConfigEntity config) {
        super(source);
        this.config = config;
    }
}
