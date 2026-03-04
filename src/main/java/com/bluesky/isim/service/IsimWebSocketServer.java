package com.bluesky.isim.service;

import com.bluesky.isim.model.SimData;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.websocket.server.ServerEndpoint;
import java.math.BigDecimal;
import java.util.Map;

/**
 * ISIM WebSocket服务器端点
 * 前端通过 ws://localhost:8080/ws/isim-data 连接
 */
@Slf4j
@Component
@ServerEndpoint("/ws/isim-data")
public class IsimWebSocketServer {
    
    private static IsimWebSocketService webSocketService;
    private static IsimUdpService isimUdpService;
    private static WeatherDataService weatherDataService;
    private static ObjectMapper objectMapper;
    
    /**
     * 注入WebSocket服务（静态字段需要通过setter注入）
     */
    @Autowired
    public void setWebSocketService(IsimWebSocketService service) {
        webSocketService = service;
    }
    
    @Autowired
    public void setIsimUdpService(IsimUdpService service) {
        isimUdpService = service;
    }
    
    @Autowired
    public void setWeatherDataService(WeatherDataService service) {
        weatherDataService = service;
    }
    
    @Autowired
    public void setObjectMapper(ObjectMapper mapper) {
        objectMapper = mapper;
    }
    
    /**
     * 连接建立
     */
    @OnOpen
    public void onOpen(Session session) {
        if (webSocketService != null) {
            webSocketService.registerSession(session);
            webSocketService.sendMessage(session, 
                "{\"type\":\"connected\",\"message\":\"已连接到ISIM数据流\"}");
        }
    }
    
    /**
     * 连接关闭
     */
    @OnClose
    public void onClose(Session session) {
        if (webSocketService != null) {
            webSocketService.removeSession(session);
        }
    }
    
    /**
     * 收到前端消息
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        log.debug("收到前端消息：{}", message);
        
        try {
            // 解析JSON消息
            Map<String, Object> messageData = objectMapper.readValue(message, Map.class);
            String type = (String) messageData.get("type");
            
            if ("aircraft_position".equals(type)) {
                // 处理飞机位置消息
                handleAircraftPosition(messageData, session);
            } else if ("command".equals(type)) {
                // 处理命令消息
                handleCommand(messageData, session);
            } else if ("connection_control".equals(type)) {
                // 处理连接控制消息
                handleConnectionControl(messageData, session);
            } else {
                // 其他消息，简单回复
                webSocketService.sendMessage(session, 
                    "{\"type\":\"ack\",\"message\":\"收到消息\"}");
            }
            
        } catch (Exception e) {
            log.error("处理WebSocket消息失败: {}", message, e);
            webSocketService.sendMessage(session, 
                "{\"type\":\"error\",\"message\":\"处理消息失败: " + e.getMessage() + "\"}");
        }
    }
    
    /**
     * 处理飞机位置消息
     */
    private void handleAircraftPosition(Map<String, Object> messageData, Session session) {
        try {
            // 解析飞机位置
            Double longitude = getDoubleValue(messageData, "longitude");
            Double latitude = getDoubleValue(messageData, "latitude");
            Double altitude = getDoubleValue(messageData, "altitude");
            Double heading = getDoubleValue(messageData, "heading", 0.0);
            Double pitch = getDoubleValue(messageData, "pitch", 0.0);
            Double roll = getDoubleValue(messageData, "roll", 0.0);
            
            if (longitude == null || latitude == null) {
                log.warn("飞机位置消息缺少经纬度信息");
                return;
            }
            
            log.debug("收到飞机位置: lon={}, lat={}, alt={}", longitude, latitude, altitude);
            
            // 获取气象数据并发送给ISIM
            if (weatherDataService != null && isimUdpService != null) {
                // 根据飞机位置获取气象数据
                com.bluesky.isim.model.WeatherData weatherData = 
                    weatherDataService.getWeatherDataByLocation(
                        BigDecimal.valueOf(longitude), 
                        BigDecimal.valueOf(latitude)
                    );
                
                if (weatherData != null) {
                    // 发送气象数据给ISIM
                    isimUdpService.sendWeatherData(weatherData);
                    
                    // 回复前端
                    webSocketService.sendMessage(session, 
                        "{\"type\":\"weather_data_sent\",\"message\":\"已根据飞机位置发送气象数据\",\"longitude\":" + 
                        longitude + ",\"latitude\":" + latitude + "}");
                    
                    log.debug("已根据飞机位置发送气象数据: lon={}, lat={}", longitude, latitude);
                }
            }
            
        } catch (Exception e) {
            log.error("处理飞机位置消息失败", e);
        }
    }
    
    /**
     * 处理命令消息
     */
    private void handleCommand(Map<String, Object> messageData, Session session) {
        String command = (String) messageData.get("command");
        log.debug("收到命令: {}", command);
        
        webSocketService.sendMessage(session, 
            "{\"type\":\"command_response\",\"command\":\"" + command + "\",\"status\":\"received\"}");
    }
    
    /**
     * 处理连接控制消息
     */
    private void handleConnectionControl(Map<String, Object> messageData, Session session) {
        try {
            String action = (String) messageData.get("action");
            log.info("收到连接控制消息: action={}", action);
            
            if ("activate".equals(action) && isimUdpService != null) {
                isimUdpService.activate();
                webSocketService.sendMessage(session, 
                    "{\"type\":\"connection_control\",\"action\":\"activate\",\"status\":\"success\"}");
            } else if ("deactivate".equals(action) && isimUdpService != null) {
                isimUdpService.deactivate();
                webSocketService.sendMessage(session, 
                    "{\"type\":\"connection_control\",\"action\":\"deactivate\",\"status\":\"success\"}");
            } else {
                webSocketService.sendMessage(session, 
                    "{\"type\":\"connection_control\",\"action\":\"" + action + "\",\"status\":\"unknown_action\"}");
            }
        } catch (Exception e) {
            log.error("处理连接控制消息失败", e);
            webSocketService.sendMessage(session, 
                "{\"type\":\"error\",\"message\":\"处理连接控制失败: " + e.getMessage() + "\"}");
        }
    }
    
    /**
     * 从Map中获取Double值
     */
    private Double getDoubleValue(Map<String, Object> map, String key) {
        return getDoubleValue(map, key, null);
    }
    
    private Double getDoubleValue(Map<String, Object> map, String key, Double defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        
        return defaultValue;
    }
    
    /**
     * 错误处理
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket错误，Session ID：{}", session.getId(), error);
    }
}