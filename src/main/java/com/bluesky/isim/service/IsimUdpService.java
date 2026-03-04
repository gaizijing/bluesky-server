package com.bluesky.isim.service;

import com.bluesky.isim.config.IsimConfig;
import com.bluesky.isim.model.SimData;
import com.bluesky.isim.model.WeatherData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ISIM UDP通信服务
 * 负责：
 * 1. 接收ISIM发送的飞机姿态数据
 * 2. 发送气象数据给ISIM
 * 3. 通过WebSocket推送姿态数据给前端
 */
@Slf4j
@Service
public class IsimUdpService {
    
    private final IsimConfig config;
    private final ObjectMapper objectMapper;
    private final IsimWebSocketService webSocketService;
    private final WeatherDataService weatherDataService;
    
    private DatagramSocket receiveSocket;
    private DatagramSocket sendSocket;
    private ExecutorService executorService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // 保存最新的飞机位置数据
    private final AtomicReference<SimData> lastSimData = new AtomicReference<>();
    
    // 是否激活数据处理（前端连接后才激活）
    private final AtomicBoolean isActive = new AtomicBoolean(false);
    
    @Autowired
    public IsimUdpService(IsimConfig config, 
                         ObjectMapper objectMapper,
                         IsimWebSocketService webSocketService,
                         WeatherDataService weatherDataService) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.webSocketService = webSocketService;
        this.weatherDataService = weatherDataService;
    }
    
    /**
     * 启动UDP服务
     */
    @PostConstruct
    public void start() {
        if (!config.isEnabled()) {
            log.info("ISIM集成未启用");
            return;
        }
        
        try {
            // 创建接收套接字（监听ISIM数据）
            receiveSocket = new DatagramSocket(config.getReceivePort());
            // 创建发送套接字
            sendSocket = new DatagramSocket();
            
            executorService = Executors.newSingleThreadExecutor();
            isRunning.set(true);
            
            // 启动接收线程
            executorService.submit(this::receiveLoop);
            
            log.info("ISIM UDP服务已启动，监听端口：{}，发送目标：{}:{}", 
                    config.getReceivePort(), config.getHost(), config.getSendPort());
            
        } catch (Exception e) {
            log.error("启动ISIM UDP服务失败", e);
        }
    }
    
    /**
     * 接收循环
     */
    private void receiveLoop() {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        
        while (isRunning.get()) {
            try {
                receiveSocket.receive(packet);
                String rawData = new String(packet.getData(), 0, packet.getLength(), 
                                           StandardCharsets.UTF_8).trim();
                
               // log.debug("收到ISIM原始数据：{}", rawData);
                
                // 解析数据
                SimData simData = parseSimData(rawData);
                
                // 保存最新的飞机位置数据
                lastSimData.set(simData);
                
                // 通过WebSocket推送给前端
                webSocketService.broadcastSimData(simData);
                
                // 只有在激活状态下才发送气象数据给ISIM
                if (isActive.get()) {
                    sendWeatherDataForAircraftPosition(simData);
                }
                
            } catch (Exception e) {
                if (isRunning.get()) {
                    log.error("接收ISIM数据异常", e);
                }
            }
        }
    }
    
    /**
     * 根据飞机位置发送气象数据给ISIM
     */
    private void sendWeatherDataForAircraftPosition(SimData simData) {
        if (simData == null) {
            return;
        }
        
        try {
            // 检查是否有有效的飞机位置
            if (simData.getAircraftLon() != 0.0 && simData.getAircraftLat() != 0.0) {
                // 根据飞机位置获取气象数据
                WeatherData weatherData = weatherDataService.getWeatherDataByLocation(
                    java.math.BigDecimal.valueOf(simData.getAircraftLon()),
                    java.math.BigDecimal.valueOf(simData.getAircraftLat())
                );
                
                if (weatherData != null) {
                    // 发送气象数据给ISIM
                    sendWeatherData(weatherData);
                    log.debug("根据飞机位置发送气象数据: lon={}, lat={}", 
                             simData.getAircraftLon(), simData.getAircraftLat());
                }
            }
        } catch (Exception e) {
            log.error("根据飞机位置发送气象数据失败", e);
        }
    }
    
    /**
     * 解析ISIM数据
     * 格式：分号分隔的字符串
     */
    private SimData parseSimData(String rawData) {
        SimData simData = new SimData();
        
        if (rawData == null || rawData.isEmpty()) {
            return simData;
        }
        
        // 清理数据：移除非法字符
        rawData = rawData.replaceAll("[^0-9.;truefalse-]", "");
        rawData = rawData.replaceAll(";+$", "");
        
        String[] fields = rawData.split(";");
       // log.debug("解析字段数：{}", fields.length);
        
        try {
            // 飞机核心姿态+位置 (0-5)
            if (fields.length > 0) simData.setAircraftRoll(parseDouble(fields[0]));
            if (fields.length > 1) simData.setAircraftPitch(parseDouble(fields[1]));
            if (fields.length > 2) simData.setAircraftHeading(parseDouble(fields[2]));
            if (fields.length > 3) simData.setAircraftLon(parseDouble(fields[3]));
            if (fields.length > 4) simData.setAircraftLat(parseDouble(fields[4]));
            if (fields.length > 5) simData.setAircraftAlt(parseDouble(fields[5]));
            
            // 眼点位置 (6-8)
            if (fields.length > 6) simData.setEyeLon(parseDouble(fields[6]));
            if (fields.length > 7) simData.setEyeLat(parseDouble(fields[7]));
            if (fields.length > 8) simData.setEyeAlt(parseDouble(fields[8]));
            
            // 基础开关 (9-10)
            if (fields.length > 9) simData.setTrailHide(parseInt(fields[9]));
            if (fields.length > 10) simData.setAirwayHide(parseInt(fields[10]));
            
            // 第三视角位置+姿态 (11-15)
            if (fields.length > 11) simData.setObserveLon(parseDouble(fields[11]));
            if (fields.length > 12) simData.setObserveLat(parseDouble(fields[12]));
            if (fields.length > 13) simData.setObserveAlt(parseDouble(fields[13]));
            if (fields.length > 14) simData.setObservePitch(parseDouble(fields[14]));
            if (fields.length > 15) simData.setObserveHeading(parseDouble(fields[15]));
            
            // 本机灯光 (16)
            if (fields.length > 16) simData.setOwnshipLight(parseInt(fields[16]));
            
        } catch (Exception e) {
            log.error("解析ISIM数据失败", e);
        }
        
        return simData;
    }
    
    private double parseDouble(String str) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    private int parseInt(String str) {
        if (str == null || str.isEmpty()) return 0;
        if ("false".equalsIgnoreCase(str)) return 0;
        if ("true".equalsIgnoreCase(str)) return 1;
        try {
            return (int) Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * 发送气象数据给ISIM
     */
    public void sendWeatherData(WeatherData weatherData) {
        if (!config.isEnabled() || sendSocket == null) {
            log.warn("ISIM服务未启用或未初始化");
            return;
        }
        
        try {
            // 转换为ISIM格式的字符串
            String weatherStr = weatherData.toIsimFormat();
            byte[] data = weatherStr.getBytes(StandardCharsets.UTF_8);
            
            InetAddress address = InetAddress.getByName(config.getHost());
            DatagramPacket packet = new DatagramPacket(data, data.length, address, config.getSendPort());
            
            sendSocket.send(packet);
            log.debug("已发送气象数据给ISIM：{}", weatherStr);
            
        } catch (Exception e) {
            log.error("发送气象数据给ISIM失败", e);
        }
    }
    
    /**
     * 发送原始数据给ISIM（自定义格式）
     */
    public void sendRawData(String data) {
        if (!config.isEnabled() || sendSocket == null) {
            log.warn("ISIM服务未启用或未初始化");
            return;
        }
        
        try {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            InetAddress address = InetAddress.getByName(config.getHost());
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, config.getSendPort());
            
            sendSocket.send(packet);
            log.debug("已发送原始数据给ISIM：{}", data);
            
        } catch (Exception e) {
            log.error("发送原始数据给ISIM失败", e);
        }
    }
    
    /**
     * 定时发送气象数据（如果配置了发送间隔）
     */
    @Scheduled(fixedDelayString = "#{@isimConfig.sendInterval}")
    public void scheduledSendWeatherData() {
        if (config.getSendInterval() <= 0 || !isActive.get()) {
            return;
        }
        
        try {
            // 首先尝试基于最新的飞机位置发送气象数据
            SimData lastData = lastSimData.get();
            if (lastData != null && lastData.getAircraftLon() != 0.0 && lastData.getAircraftLat() != 0.0) {
                // 根据飞机位置获取气象数据
                WeatherData weatherData = weatherDataService.getWeatherDataByLocation(
                    java.math.BigDecimal.valueOf(lastData.getAircraftLon()),
                    java.math.BigDecimal.valueOf(lastData.getAircraftLat())
                );
                
                if (weatherData != null) {
                    sendWeatherData(weatherData);
                    log.debug("定时发送基于飞机位置的气象数据: lon={}, lat={}", 
                             lastData.getAircraftLon(), lastData.getAircraftLat());
                    return;
                }
            }
            
            // 如果没有飞机位置数据，发送默认监测点的数据（兼容旧逻辑）
            log.debug("没有飞机位置数据，发送默认监测点气象数据");
            WeatherData weatherData = weatherDataService.getLatestWeatherData();
            if (weatherData != null) {
                sendWeatherData(weatherData);
            }
        } catch (Exception e) {
            log.error("定时发送气象数据失败", e);
        }
    }
    
    /**
     * 激活ISIM数据处理（前端连接后调用）
     */
    public void activate() {
        if (isActive.compareAndSet(false, true)) {
            log.info("ISIM数据处理已激活，开始发送气象数据");
        }
    }
    
    /**
     * 停用ISIM数据处理（前端断开连接后调用）
     */
    public void deactivate() {
        if (isActive.compareAndSet(true, false)) {
            log.info("ISIM数据处理已停用，停止发送气象数据");
        }
    }
    
    /**
     * 检查是否已激活
     */
    public boolean isActive() {
        return isActive.get();
    }
    
    /**
     * 停止服务
     */
    @PreDestroy
    public void stop() {
        isRunning.set(false);
        isActive.set(false);
        
        if (receiveSocket != null) {
            receiveSocket.close();
        }
        
        if (sendSocket != null) {
            sendSocket.close();
        }
        
        if (executorService != null) {
            executorService.shutdown();
        }
        
        log.info("ISIM UDP服务已停止");
    }
}