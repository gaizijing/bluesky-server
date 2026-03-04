package com.bluesky.isim.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ISIM配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "isim")
public class IsimConfig {
    /**
     * ISIM模拟机UDP接收地址（后端发送气象数据的目标）
     */
    private String host = "127.0.0.1";
    
    /**
     * ISIM模拟机UDP接收端口（后端发送气象数据的目标端口）
     */
    private int sendPort = 8152;
    
    /**
     * 后端UDP监听端口（接收ISIM姿态数据）
     */
    private int receivePort = 8151;
    
    /**
     * 发送气象数据的频率（毫秒），0表示不自动发送
     */
    private int sendInterval = 1000;
    
    /**
     * 是否启用ISIM集成
     */
    private boolean enabled = true;
    
    /**
     * WebSocket路径
     */
    private String websocketPath = "/ws/isim-data";
}