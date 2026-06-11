package com.bluesky.isim.service;

import com.bluesky.isim.config.IsimConfig;
import com.bluesky.isim.model.SimData;
import com.bluesky.isim.util.WindFrameUtil;
import com.bluesky.service.WindFieldService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class IsimUdpService {

    private final IsimConfig config;
    private final IsimWebSocketService webSocketService;
    private final WindFieldService windFieldService;

    private DatagramSocket receiveSocket;
    private DatagramSocket sendSocket;
    private ExecutorService executorService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final AtomicReference<SimData> lastSimData = new AtomicReference<>();
    private final AtomicBoolean isActive = new AtomicBoolean(false);
    
    // 记录最后一次收到ISIM数据的时间（用于判断连接状态）
    private final AtomicReference<Long> lastReceiveTime = new AtomicReference<>(0L);
    // 记录最后一次发送风场数据的时间
    private final AtomicReference<Long> lastSendTime = new AtomicReference<>(0L);

    public IsimUdpService(IsimConfig config,
                         IsimWebSocketService webSocketService,
                         WindFieldService windFieldService) {
        this.config = config;
        this.webSocketService = webSocketService;
        this.windFieldService = windFieldService;
    }

    @PostConstruct
    public void start() {
        if (!config.isEnabled()) {
            log.info("ISIM集成未启用");
            return;
        }

        try {
            // 如果 socket 已存在，先关闭
            stopUDP();
            
            receiveSocket = new DatagramSocket(config.getReceivePort());
            sendSocket = new DatagramSocket();

            executorService = Executors.newSingleThreadExecutor();
            isRunning.set(true);
            isActive.set(false);  // 重置发送状态

            executorService.submit(this::receiveLoop);

            log.info("ISIM UDP服务已启动，监听端口：{}，发送目标：{}:{}",
                    config.getReceivePort(), config.getHost(), config.getSendPort());

            // 启动时自动发送初始位置
            if (config.isSendInitialPosition()) {
                sendRelocateCommand(config.getInitialLongitude(), 
                                   config.getInitialLatitude(), 
                                   config.getInitialAltitude());

            }

        } catch (Exception e) {
            log.error("启动ISIM UDP服务失败", e);
        }
    }
    
    /**
     * 停止UDP服务（断开连接）
     */
    public void stopUDP() {
        isRunning.set(false);
        isActive.set(false);
        
        if (receiveSocket != null) {
            try {
                receiveSocket.close();
            } catch (Exception e) {
                log.warn("关闭receiveSocket时出错", e);
            }
            receiveSocket = null;
        }
        
        if (sendSocket != null) {
            try {
                sendSocket.close();
            } catch (Exception e) {
                log.warn("关闭sendSocket时出错", e);
            }
            sendSocket = null;
        }
        
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        
        log.info("ISIM UDP服务已停止");
    }

    private void receiveLoop() {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (isRunning.get()) {
            try {
                receiveSocket.receive(packet);
                String rawData = new String(packet.getData(), 0, packet.getLength(),
                                           StandardCharsets.UTF_8).trim();

                log.info("收到ISIM数据: {}", rawData);

                SimData simData = parseSimData(rawData);
                lastSimData.set(simData);
                lastReceiveTime.set(System.currentTimeMillis()); // 更新接收时间戳

                webSocketService.broadcastSimData(simData);

                if (isActive.get()) {
                    sendWindDataForAircraftPosition(simData);
                }

            } catch (Exception e) {
                if (isRunning.get()) {
                    log.error("接收ISIM数据异常", e);
                }
            }
        }
    }

    private void sendWindDataForAircraftPosition(SimData simData) {
        if (simData == null) {
            return;
        }

        try {
            double lon = simData.getAircraftLon();
            double lat = simData.getAircraftLat();

            if (lon == 0.0 && lat == 0.0) {
                return;
            }

            Map<String, Double> windData = windFieldService.getWindAtLocation(lon, lat, 10);

            double u = windData.getOrDefault("u", 0.0);
            double v = windData.getOrDefault("v", 0.0);
            double w = 0.0;
            double heading = simData.getAircraftHeading();
            double[] body = WindFrameUtil.enuToBody(u, v, w, heading);

            sendBodyWind(body[0], body[1], body[2]);
            lastSendTime.set(System.currentTimeMillis());

            log.info("已发送风场到ISIM：LON={}, LAT={}, U={}, V={}, HDG={}, bodyX={}, bodyY={}, bodyZ={}",
                    lon, lat, u, v, heading, body[0], body[1], body[2]);

        } catch (Exception e) {
            log.error("发送风场数据失败", e);
        }
    }

    private SimData parseSimData(String rawData) {
        SimData simData = new SimData();

        if (rawData == null || rawData.isEmpty()) {
            return simData;
        }

        rawData = rawData.replaceAll("[^0-9.;truefalse-]", "");
        rawData = rawData.replaceAll(";+$", "");

        String[] fields = rawData.split(";");

        try {
            if (fields.length == 8) {
                // WeatherBridge 旧短格式：roll;pitch;heading;lon;lat;alt;groundSpeed;verticalSpeed
                simData.setAircraftRoll(parseDouble(fields[0]));
                simData.setAircraftPitch(parseDouble(fields[1]));
                simData.setAircraftHeading(parseDouble(fields[2]));
                simData.setAircraftLon(parseDouble(fields[3]));
                simData.setAircraftLat(parseDouble(fields[4]));
                simData.setAircraftAlt(parseDouble(fields[5]));
                simData.setGroundSpeed(parseDouble(fields[6]));
                simData.setVerticalSpeed(parseDouble(fields[7]));
            } else if (fields.length >= 9 && fields.length < 17) {
                // WeatherBridge 新短格式：...;groundSpeed;verticalSpeed;batteryPercent
                simData.setAircraftRoll(parseDouble(fields[0]));
                simData.setAircraftPitch(parseDouble(fields[1]));
                simData.setAircraftHeading(parseDouble(fields[2]));
                simData.setAircraftLon(parseDouble(fields[3]));
                simData.setAircraftLat(parseDouble(fields[4]));
                simData.setAircraftAlt(parseDouble(fields[5]));
                simData.setGroundSpeed(parseDouble(fields[6]));
                simData.setVerticalSpeed(parseDouble(fields[7]));
                simData.setBatteryPercent(parseDouble(fields[8]));
            } else {
                if (fields.length > 0) simData.setAircraftRoll(parseDouble(fields[0]));
                if (fields.length > 1) simData.setAircraftPitch(parseDouble(fields[1]));
                if (fields.length > 2) simData.setAircraftHeading(parseDouble(fields[2]));
                if (fields.length > 3) simData.setAircraftLon(parseDouble(fields[3]));
                if (fields.length > 4) simData.setAircraftLat(parseDouble(fields[4]));
                if (fields.length > 5) simData.setAircraftAlt(parseDouble(fields[5]));

                if (fields.length > 6) simData.setEyeLon(parseDouble(fields[6]));
                if (fields.length > 7) simData.setEyeLat(parseDouble(fields[7]));
                if (fields.length > 8) simData.setEyeAlt(parseDouble(fields[8]));

                if (fields.length > 9) simData.setTrailHide(parseInt(fields[9]));
                if (fields.length > 10) simData.setAirwayHide(parseInt(fields[10]));

                if (fields.length > 11) simData.setObserveLon(parseDouble(fields[11]));
                if (fields.length > 12) simData.setObserveLat(parseDouble(fields[12]));
                if (fields.length > 13) simData.setObserveAlt(parseDouble(fields[13]));
                if (fields.length > 14) simData.setObservePitch(parseDouble(fields[14]));
                if (fields.length > 15) simData.setObserveHeading(parseDouble(fields[15]));

                if (fields.length > 16) simData.setOwnshipLight(parseInt(fields[16]));
            }

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



    public void sendBodyWind(double x, double y, double z) {
        if (!config.isEnabled() || sendSocket == null) {
            log.warn("ISIM服务未启用或未初始化");
            return;
        }

        try {
            // 发机体轴风 X/Y/Z（WindTurbUnit 直接输出到 SteadyWindVelocityBody）
            String command = String.format("X=%.4f;Y=%.4f;Z=%.4f", x, y, z);
            byte[] data = command.getBytes(StandardCharsets.UTF_8);

            InetAddress address = InetAddress.getByName(config.getHost());
            DatagramPacket packet = new DatagramPacket(data, data.length, address, config.getSendPort());

            sendSocket.send(packet);
            log.info("已发送风场到iSim：X={}, Y={}, Z={} m/s (机体轴)", x, y, z);

        } catch (Exception e) {
            log.error("发送风场数据失败", e);
        }
    }
    public void sendRelocateCommand(double longitude, double latitude, double altitude) {
        if (!config.isEnabled() || sendSocket == null) {
            log.warn("ISIM服务未启用或未初始化");
            return;
        }

        try {
            // 发送重定位指令：LON=经度;LAT=纬度;ALT=高度(米)
            String command = String.format("LON=%.6f;LAT=%.6f;ALT=%.2f", longitude, latitude, altitude);
            byte[] data = command.getBytes(StandardCharsets.UTF_8);

            InetAddress address = InetAddress.getByName(config.getHost());
            DatagramPacket packet = new DatagramPacket(data, data.length, address, config.getSendPort());

            sendSocket.send(packet);
            log.info("已发送飞机重定位指令：LON={}, LAT={}, ALT={}m", longitude, latitude, altitude);

        } catch (Exception e) {
            log.error("发送飞机重定位指令失败", e);
        }
    }

    @Scheduled(fixedDelayString = "#{@isimConfig.sendInterval}")
    public void scheduledSendWeatherData() {
        if (config.getSendInterval() <= 0 || !isActive.get()) {
            return;
        }

        try {
            SimData lastData = lastSimData.get();
            if (lastData != null && lastData.getAircraftLon() != 0.0 && lastData.getAircraftLat() != 0.0) {
                sendWindDataForAircraftPosition(lastData);
            }
        } catch (Exception e) {
            log.error("定时发送风场数据失败", e);
        }
    }

    public void activate() {
        if (isActive.compareAndSet(false, true)) {
            log.info("ISIM数据处理已激活，开始发送风场数据");
        }
    }

    public void deactivate() {
        if (isActive.compareAndSet(true, false)) {
            log.info("ISIM数据处理已停用，停止发送风场数据");
        }
    }
    
    /**
     * 判断是否正在连接（3秒内收到过ISIM数据视为连接中）
     */
    public boolean isConnected() {
        long lastTime = lastReceiveTime.get();
        if (lastTime == 0) {
            return false;
        }
        return (System.currentTimeMillis() - lastTime) < 3000; // 3秒内有数据
    }
    
    /**
     * 判断是否正在发送风场数据
     */
    public boolean isSendingWindData() {
        return isActive.get();
    }
    
    /**
     * 获取最后一次收到ISIM数据的时间
     */
    public long getLastReceiveTime() {
        return lastReceiveTime.get();
    }
    
    /**
     * 获取最后一次发送风场数据的时间
     */
    public long getLastSendTime() {
        return lastSendTime.get();
    }

    public boolean isActive() {
        return isActive.get();
    }
    
    /**
     * 推送初始位置给前端（用于配置完成后让前端聚焦飞机）
     */
    public void pushInitialPositionToFrontend(double longitude, double latitude, double altitude) {
        SimData simData = new SimData();
        simData.setHeader("UE5_SIM_DATA");
        simData.setAircraftLon(longitude);
        simData.setAircraftLat(latitude);
        simData.setAircraftAlt(altitude);
        simData.setAircraftHeading(0);
        simData.setAircraftPitch(0);
        simData.setAircraftRoll(0);
        simData.setSource("SERVER_INIT");
        
        webSocketService.broadcastSimData(simData);
        log.info("已推送初始位置给前端：LON={}, LAT={}, ALT={}", longitude, latitude, altitude);
    }

    @PreDestroy
    public void stop() {
        stopUDP();
        log.info("ISIM UDP服务已停止");
    }
}