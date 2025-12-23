package com.lantian.lam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantian.lam.model.entity.UdpReceivedData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
@Slf4j

@Service
public class UdpReceiverService {
    
    @Value("${app.udp.port:2771}")
    private int port;
    
    @Value("${app.udp.buffer-size:1024}")
    private int bufferSize;
    
    @Value("${app.udp.enabled:true}")
    private boolean enabled;
    
    @Value("${app.udp.receive-timeout:5000}")
    private long receiveTimeout;
    
    // 实现接收逻辑
    public void startReceiving() {
        if (!enabled) {
            return;
        }
        
        // UDP 接收实现
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[bufferSize];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            
            while (true) {
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                // 处理接收到的消息
                processMessage(message);
            }
        } catch (Exception e) {
            // 错误处理
        }
    }
    
    private void processMessage(String message) {
         log.info("收到 UDP 消息: {} 来自 {}:{}", message);
        // 处理业务逻辑
        try {
            UdpReceivedData data = parseUdpReceivedData(message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * 解析接收到的飞行数据字符串
     * @param message 接收到的原始消息
     * @return 解析后的飞行数据对象
     * @throws Exception 解析异常
     */
    private UdpReceivedData parseUdpReceivedData(String message) throws Exception {
        UdpReceivedData data = new UdpReceivedData();

        // 假设数据是JSON格式，使用Jackson进行解析
        ObjectMapper mapper = new ObjectMapper();

        JsonNode rootNode = mapper.readTree(message);

        // 解析各个字段
        if (rootNode.has("Longitude")) {
            data.Longitude = rootNode.get("Longitude").asDouble();
        }
        if (rootNode.has("Latitude")) {
            data.Latitude = rootNode.get("Latitude").asDouble();
        }
        if (rootNode.has("Altitude")) {
            data.Altitude = rootNode.get("Altitude").asDouble();
        }
        if (rootNode.has("Heading")) {
            data.Heading = rootNode.get("Heading").asDouble();
        }
        if (rootNode.has("Pitch")) {
            data.Pitch = rootNode.get("Pitch").asDouble();
        }
        if (rootNode.has("Bank")) {
            data.Bank = rootNode.get("Bank").asDouble();
        }

        // 解析时间相关字段
        if (rootNode.has("Month")) {
            data.Month = rootNode.get("Month").asInt();
        }
        if (rootNode.has("Hour")) {
            data.Hour = rootNode.get("Hour").asInt();
        }
        if (rootNode.has("Minute")) {
            data.Minute = rootNode.get("Minute").asInt();
        }

        // 解析气象相关字段
        if (rootNode.has("WindDirection")) {
            data.WindDirection = rootNode.get("WindDirection").asInt();
        }
        if (rootNode.has("WindSpeed")) {
            data.WindSpeed = rootNode.get("WindSpeed").asInt();
        }
        if (rootNode.has("WindHeight")) {
            data.WindHeight = rootNode.get("WindHeight").asInt();
        }
        if (rootNode.has("VisibilityDistance")) {
            data.VisibilityDistance = rootNode.get("VisibilityDistance").asInt();
        }
        if (rootNode.has("VisibilityBottom")) {
            data.VisibilityBottom = rootNode.get("VisibilityBottom").asInt();
        }
        if (rootNode.has("VisibilityTop")) {
            data.VisibilityTop = rootNode.get("VisibilityTop").asInt();
        }
        if (rootNode.has("RainLevel")) {
            data.RainLevel = rootNode.get("RainLevel").asInt();
        }
        if (rootNode.has("SnowLevel")) {
            data.SnowLevel = rootNode.get("SnowLevel").asInt();
        }
        if (rootNode.has("PositiveTemperature")) {
            data.PositiveTemperature = rootNode.get("PositiveTemperature").asInt();
        }
        if (rootNode.has("CloudType")) {
            data.CloudType = rootNode.get("CloudType").asInt();
        }
        if (rootNode.has("CloudHeight")) {
            data.CloudHeight = rootNode.get("CloudHeight").asInt();
        }
        if (rootNode.has("CloudBottom")) {
            data.CloudBottom = rootNode.get("CloudBottom").asInt();
        }

        // 解析控制面相关字段
        if (rootNode.has("Throttle")) {
            data.Throttle = rootNode.get("Throttle").asInt();
        }
        if (rootNode.has("Throttle1")) {
            data.Throttle1 = rootNode.get("Throttle1").asInt();
        }
        if (rootNode.has("Throttle2")) {
            data.Throttle2 = rootNode.get("Throttle2").asInt();
        }
        if (rootNode.has("Flaps")) {
            data.Flaps = rootNode.get("Flaps").asInt();
        }
        if (rootNode.has("Spoilers")) {
            data.Spoilers = rootNode.get("Spoilers").asInt();
        }
        if (rootNode.has("ParkingBrake")) {
            data.ParkingBrake = rootNode.get("ParkingBrake").asInt();
        }
        if (rootNode.has("Gear")) {
            data.Gear = rootNode.get("Gear").asInt();
        }

        // 解析速度与控制信息
        if (rootNode.has("IAS")) {
            data.IAS = rootNode.get("IAS").asDouble();
        }
        if (rootNode.has("Rudder")) {
            data.Rudder = rootNode.get("Rudder").asDouble();
        }
        if (rootNode.has("Elevator")) {
            data.Elevator = rootNode.get("Elevator").asDouble();
        }
        if (rootNode.has("Aileron")) {
            data.Aileron = rootNode.get("Aileron").asDouble();
        }
        if (rootNode.has("BrakeL")) {
            data.BrakeL = rootNode.get("BrakeL").asDouble();
        }
        if (rootNode.has("BrakeR")) {
            data.BrakeR = rootNode.get("BrakeR").asDouble();
        }

        return data;
    }

}

