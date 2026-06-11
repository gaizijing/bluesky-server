package com.bluesky.isim.controller;

import com.bluesky.common.Result;
import com.bluesky.isim.service.IsimUdpService;
import com.bluesky.isim.config.IsimConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Tag(name = "ISIM模拟机集成", description = "ISIM模拟机数据交换和控制接口")
@RestController
@RequestMapping("/isim")
@RequiredArgsConstructor
public class IsimController {

    private final IsimUdpService isimUdpService;
    private final IsimConfig isimConfig;

    @Operation(summary = "获取ISIM集成状态", description = "获取ISIM模拟机的连接状态和发送状态")
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();

        // 连接状态：3秒内收到过ISIM数据视为连接中
        boolean connected = isimUdpService.isConnected();
        // 发送状态：是否正在发送风场数据
        boolean sendingWindData = isimUdpService.isSendingWindData();
        
        // 配置信息
        Map<String, Object> config = new HashMap<>();
        config.put("host", isimConfig.getHost());
        config.put("sendPort", isimConfig.getSendPort());
        config.put("receivePort", isimConfig.getReceivePort());
        status.put("config", config);
        
        // 运行状态
        Map<String, Object> runtime = new HashMap<>();
        runtime.put("connected", connected);
        runtime.put("sendingWindData", sendingWindData);
        runtime.put("lastReceiveTime", isimUdpService.getLastReceiveTime());
        runtime.put("lastSendTime", isimUdpService.getLastSendTime());
        runtime.put("timestamp", System.currentTimeMillis());
        status.put("runtime", runtime);

        return Result.success(status);
    }

    @Operation(summary = "控制ISIM数据传输", description = "控制ISIM自动发送风场数据（START_SENDING: 收到飞机位置后自动查询风场并发送; STOP_SENDING: 停止自动发送）")
    @PostMapping("/control")
    public Result<Map<String, Object>> controlDataFlow(
            @RequestBody Map<String, Object> controlRequest) {

        try {
            String command = (String) controlRequest.get("command");

            Map<String, Object> result = new HashMap<>();
            result.put("command", command);
            result.put("timestamp", System.currentTimeMillis());

            switch (command.toUpperCase()) {
                case "START_SENDING":
                    isimUdpService.activate();
                    result.put("status", "started");
                    break;

                case "STOP_SENDING":
                    isimUdpService.deactivate();
                    result.put("status", "stopped");
                    break;

                default:
                    result.put("status", "unknown_command");
                    return Result.error(400, "未知命令：" + command);
            }

            log.info("执行ISIM控制命令：{}", command);
            return Result.success(result);

        } catch (Exception e) {
            log.error("执行ISIM控制命令失败", e);
            return Result.error(500, "控制命令执行失败：" + e.getMessage());
        }
    }

    @Operation(summary = "断开ISIM连接", description = "断开UDP连接，停止接收和发送数据")
    @PostMapping("/disconnect")
    public Result<Map<String, Object>> disconnect() {
        try {
            isimUdpService.stopUDP();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已断开ISIM连接");
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("用户请求断开ISIM连接");
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("断开ISIM连接失败", e);
            return Result.error(500, "断开连接失败：" + e.getMessage());
        }
    }

    @Operation(summary = "发送地轴U/V/W风分量", description = "手动发送地轴坐标系下的三个风速分量(U东, V北, W上)到ISIM模拟机（调试用）")
    @PostMapping("/send-body-wind")
    public Result<Map<String, Object>> sendBodyWind(
            @Parameter(description = "地轴U风(东向, m/s)") @RequestParam double u,
            @Parameter(description = "地轴V风(北向, m/s)") @RequestParam double v,
            @Parameter(description = "地轴W风(垂直向上, m/s)") @RequestParam double w) {

        try {
            isimUdpService.sendBodyWind(u, v, w);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "地轴U/V/W风分量已发送到ISIM");
            result.put("u", u);
            result.put("v", v);
            result.put("w", w);
            result.put("timestamp", System.currentTimeMillis());

            log.info("发送地轴风到ISIM：U={}, V={}, W={} m/s", u, v, w);
            return Result.success(result);

        } catch (Exception e) {
            log.error("发送地轴风到ISIM失败", e);
            return Result.error(500, "发送地轴风失败：" + e.getMessage());
        }
    }

    @Operation(summary = "更新ISIM目标地址", description = "更新ISIM模拟机的IP地址和端口，可选同时发送飞机重定位指令")
    @PostMapping("/update-target")
    public Result<Map<String, Object>> updateTarget(
            @RequestBody Map<String, Object> targetRequest) {

        try {
            String host = (String) targetRequest.get("host");
            Integer sendPort = toInteger(targetRequest.get("sendPort"));
            Integer receivePort = toInteger(targetRequest.get("receivePort"));
            
            // 可选：位置参数（安全转换，支持 Integer 和 Double）
            Double longitude = toDouble(targetRequest.get("longitude"));
            Double latitude = toDouble(targetRequest.get("latitude"));
            Double altitude = toDouble(targetRequest.get("altitude"));

            if (host == null || host.trim().isEmpty()) {
                return Result.error(400, "主机地址不能为空");
            }

            if (!host.matches("^([0-9]{1,3}\\.){3}[0-9]{1,3}$") && !host.equals("localhost")) {
                return Result.error(400, "主机地址格式无效");
            }

            if (sendPort != null && (sendPort < 1 || sendPort > 65535)) {
                return Result.error(400, "发送端口必须在1-65535范围内");
            }

            if (receivePort != null && (receivePort < 1 || receivePort > 65535)) {
                return Result.error(400, "接收端口必须在1-65535范围内");
            }

            // 位置参数校验
            if (longitude != null && (longitude < -180 || longitude > 180)) {
                return Result.error(400, "经度必须在-180~180范围内");
            }
            if (latitude != null && (latitude < -90 || latitude > 90)) {
                return Result.error(400, "纬度必须在-90~90范围内");
            }
            if (altitude != null && altitude < 0) {
                return Result.error(400, "高度不能为负数");
            }

            String oldHost = isimConfig.getHost();
            int oldSendPort = isimConfig.getSendPort();
            int oldReceivePort = isimConfig.getReceivePort();

            isimConfig.setHost(host);
            if (sendPort != null) {
                isimConfig.setSendPort(sendPort);
            }
            if (receivePort != null) {
                isimConfig.setReceivePort(receivePort);
                log.warn("接收端口已更新，但需要重启ISIM服务才能生效");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "ISIM目标地址已更新");
            result.put("oldHost", oldHost);
            result.put("oldSendPort", oldSendPort);
            result.put("oldReceivePort", oldReceivePort);
            result.put("newHost", isimConfig.getHost());
            result.put("newSendPort", isimConfig.getSendPort());
            result.put("newReceivePort", isimConfig.getReceivePort());
            result.put("timestamp", System.currentTimeMillis());

            log.info("ISIM目标地址已更新：host={}->{}, sendPort={}->{}, receivePort={}->{}",
                    oldHost, isimConfig.getHost(),
                    oldSendPort, isimConfig.getSendPort(),
                    oldReceivePort, isimConfig.getReceivePort());
            
            // 重新启动UDP服务（会重置状态，关闭旧连接，建立新连接）
            isimUdpService.start();
            
            // 如果提供了位置参数，自动发送重定位指令
            if (longitude != null && latitude != null) {
                double alt = altitude != null ? altitude : 1000.0;
                isimUdpService.sendRelocateCommand(longitude, latitude, alt);
                
                result.put("relocated", true);
                result.put("longitude", longitude);
                result.put("latitude", latitude);
                result.put("altitude", alt);
                result.put("message", "ISIM目标地址已更新，同时已发送重定位指令");

                // 推送位置给前端，让前端能立即聚焦飞机
                isimUdpService.pushInitialPositionToFrontend(longitude, latitude, alt);
            }

            return Result.success(result);

        } catch (Exception e) {
            log.error("更新ISIM目标地址失败", e);
            return Result.error(500, "更新ISIM目标地址失败：" + e.getMessage());
        }
    }
    
    /**
     * 安全将对象转换为Double（支持Integer和Double）
     */
    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Operation(summary = "飞机重定位", description = "发送经纬度重定位飞机位置")
    @PostMapping("/relocate")
    public Result<Map<String, Object>> relocateAircraft(
            @Parameter(description = "经度") @RequestParam double longitude,
            @Parameter(description = "纬度") @RequestParam double latitude,
            @Parameter(description = "高度（米）") @RequestParam(defaultValue = "1000") double altitude) {

        try {
            // 验证经纬度范围
            if (longitude < -180 || longitude > 180) {
                return Result.error(400, "经度必须在-180到180之间");
            }
            if (latitude < -90 || latitude > 90) {
                return Result.error(400, "纬度必须在-90到90之间");
            }
            if (altitude < 0 || altitude > 10000) {
                return Result.error(400, "高度必须在0到10000米之间");
            }

            isimUdpService.sendRelocateCommand(longitude, latitude, altitude);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "飞机重定位指令已发送");
            result.put("longitude", longitude);
            result.put("latitude", latitude);
            result.put("altitude", altitude);
            result.put("timestamp", System.currentTimeMillis());

            log.info("发送飞机重定位指令：LON={}, LAT={}, ALT={}m", longitude, latitude, altitude);
            return Result.success(result);

        } catch (Exception e) {
            log.error("发送飞机重定位指令失败", e);
            return Result.error(500, "发送飞机重定位指令失败：" + e.getMessage());
        }
    }
}