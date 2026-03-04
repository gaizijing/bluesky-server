package com.bluesky.isim.service;

import com.bluesky.isim.model.SimData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.websocket.Session;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ISIM WebSocket服务
 * 负责管理WebSocket连接和广播数据
 */
@Slf4j
@Service
public class IsimWebSocketService {
    
    private final Set<Session> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    private final ObjectMapper objectMapper;
    
    @Autowired
    public IsimWebSocketService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * 注册WebSocket会话
     */
    public void registerSession(Session session) {
        sessions.add(session);
        log.info("注册ISIM WebSocket会话，ID：{}，当前连接数：{}", 
                session.getId(), sessions.size());
    }
    
    /**
     * 移除WebSocket会话
     */
    public void removeSession(Session session) {
        sessions.remove(session);
        log.info("移除ISIM WebSocket会话，ID：{}，当前连接数：{}", 
                session.getId(), sessions.size());
    }
    
    /**
     * 广播SimData给所有连接的前端
     */
    public void broadcastSimData(SimData simData) {
        if (sessions.isEmpty()) {
            return;
        }
        
        try {
            // 将SimData转换为Map，并添加type字段
            Map<String, Object> message = objectMapper.convertValue(simData, Map.class);
            message.put("type", "sim_data");
            
            String jsonData = objectMapper.writeValueAsString(message);
            broadcast(jsonData);
            log.debug("已广播ISIM数据，连接数：{}", sessions.size());
        } catch (JsonProcessingException e) {
            log.error("转换SimData为JSON失败", e);
        }
    }
    
    /**
     * 广播消息给所有连接的前端
     */
    public void broadcast(String message) {
        sessions.forEach(session -> {
            synchronized (session) {
                try {
                    if (session.isOpen()) {
                        session.getBasicRemote().sendText(message);
                    }
                } catch (IOException e) {
                    log.error("广播WebSocket消息失败", e);
                }
            }
        });
    }
    
    /**
     * 发送消息给指定会话
     */
    public void sendMessage(Session session, String message) {
        try {
            if (session.isOpen()) {
                session.getBasicRemote().sendText(message);
            }
        } catch (IOException e) {
            log.error("发送WebSocket消息失败", e);
        }
    }
    
    /**
     * 获取当前连接数
     */
    public int getConnectionCount() {
        return sessions.size();
    }
}