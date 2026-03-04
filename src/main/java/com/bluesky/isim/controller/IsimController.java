package com.bluesky.isim.controller;

import com.bluesky.common.Result;
import com.bluesky.isim.model.WeatherData;
import com.bluesky.isim.service.IsimUdpService;
import com.bluesky.isim.service.WeatherDataService;
import com.bluesky.isim.config.IsimConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * ISIM 控制器
 * 提供ISIM相关的REST API接口
 */
@Slf4j
@Tag(name = "ISIM模拟机集成", description = "ISIM模拟机数据交换和控制接口")
@RestController
@RequestMapping("/api/isim")
@RequiredArgsConstructor
public class IsimController {
    
    private final IsimUdpService isimUdpService;
    private final WeatherDataService weatherDataService;
    private final IsimConfig isimConfig;
    
    /**
     * 获取ISIM集成状态
     */
    @Operation(summary = "获取ISIM集成状态", description = "获取ISIM模拟机的连接和运行状态")
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 基本信息
        status.put("service", "ISIM Integration Service");
        status.put("version", "1.0.0");
        status.put("timestamp", System.currentTimeMillis());
        
        // 配置信息
        Map<String, Object> config = new HashMap<>();
        // 这里需要从IsimConfig获取配置，但IsimConfig不是直接可用的
        // 可以添加配置注入或通过服务获取
        config.put("enabled", true);
        config.put("description", "ISIM integration is running");
        status.put("config", config);
        
        // 运行状态
        Map<String, Object> runtime = new HashMap<>();
        runtime.put("uptime", "N/A"); // 可以通过系统时间计算
        runtime.put("lastDataReceived", "N/A");
        runtime.put("lastDataSent", "N/A");
        status.put("runtime", runtime);
        
        return Result.success(status);
    }
    
    /**
     * 发送气象数据到ISIM
     */
    @Operation(summary = "发送气象数据到ISIM", description = "手动发送当前监测点的气象数据到ISIM模拟机")
    @PostMapping("/send-weather")
    public Result<Map<String, Object>> sendWeatherData(
            @Parameter(description = "监测点ID，为空则使用当前选中监测点") 
            @RequestParam(required = false) String pointId) {
        
        try {
            WeatherData weatherData;
            
            if (pointId != null && !pointId.trim().isEmpty()) {
                // 发送指定监测点的气象数据
                weatherData = weatherDataService.getWeatherDataByPointId(pointId);
            } else {
                // 发送当前选中监测点的气象数据
                weatherData = weatherDataService.getLatestWeatherData();
            }
            
            if (weatherData == null) {
                return Result.error(400, "无法获取气象数据");
            }
            
            // 发送数据
            isimUdpService.sendWeatherData(weatherData);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "气象数据已发送到ISIM");
            result.put("pointId", weatherData.getPointId());
            result.put("timestamp", System.currentTimeMillis());
            result.put("dataFormat", weatherData.toIsimFormat());
            
            log.info("手动发送气象数据到ISIM，监测点：{}", weatherData.getPointId());
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("发送气象数据到ISIM失败", e);
            return Result.error(500, "发送气象数据失败：" + e.getMessage());
        }
    }
    
    /**
     * 控制ISIM数据传输
     */
    @Operation(summary = "控制ISIM数据传输", description = "控制ISIM数据的发送和接收")
    @PostMapping("/control")
    public Result<Map<String, Object>> controlDataFlow(
            @RequestBody Map<String, Object> controlRequest) {
        
        try {
            String command = (String) controlRequest.get("command");
            Map<String, Object> params = (Map<String, Object>) controlRequest.get("params");
            
            Map<String, Object> result = new HashMap<>();
            result.put("command", command);
            result.put("timestamp", System.currentTimeMillis());
            
            switch (command.toUpperCase()) {
                case "START_SENDING":
                    result.put("message", "开始发送气象数据");
                    result.put("status", "started");
                    // 这里可以设置标志位控制定时发送
                    break;
                    
                case "STOP_SENDING":
                    result.put("message", "停止发送气象数据");
                    result.put("status", "stopped");
                    // 这里可以设置标志位控制定时发送
                    break;
                    
                case "SEND_NOW":
                    // 立即发送一次气象数据
                    WeatherData weatherData = weatherDataService.getLatestWeatherData();
                    if (weatherData != null) {
                        isimUdpService.sendWeatherData(weatherData);
                        result.put("message", "已立即发送气象数据");
                        result.put("dataSent", true);
                    } else {
                        result.put("message", "无法获取气象数据，发送失败");
                        result.put("dataSent", false);
                    }
                    break;
                    
                case "TEST_CONNECTION":
                    // 发送测试数据
                    String testData = "TEST;1.0;2.0;3.0;4.0;5.0;6.0;7.0;8.0;9.0;10.0";
                    isimUdpService.sendRawData(testData);
                    result.put("message", "测试数据已发送");
                    result.put("testData", testData);
                    break;
                    
                case "TAKEOFF":
                    // 起飞指令
                    result.put("message", "起飞指令已接收");
                    result.put("status", "taking_off");
                    // 这里可以触发特定逻辑
                    break;
                    
                case "LAND":
                    // 降落指令
                    result.put("message", "降落指令已接收");
                    result.put("status", "landing");
                    // 这里可以触发特定逻辑
                    break;
                    
                default:
                    result.put("message", "未知命令");
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
    
    /**
     * 获取飞行轨迹数据
     */
    @Operation(summary = "获取飞行轨迹数据", description = "获取记录的飞机飞行轨迹数据")
    @GetMapping("/flight-path")
    public Result<Map<String, Object>> getFlightPath(
            @Parameter(description = "最大点数，0表示全部") 
            @RequestParam(defaultValue = "100") int maxPoints) {
        
        try {
            // 这里需要实现飞行轨迹的存储和查询
            // 目前返回示例数据
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalPoints", 0);
            result.put("maxPoints", maxPoints);
            result.put("points", new Object[0]);
            result.put("message", "飞行轨迹数据功能待实现");
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("获取飞行轨迹数据失败", e);
            return Result.error(500, "获取飞行轨迹数据失败：" + e.getMessage());
        }
    }
    
    /**
     * 手动发送自定义数据到ISIM
     */
    @Operation(summary = "发送自定义数据到ISIM", description = "手动发送自定义格式的数据到ISIM模拟机")
    @PostMapping("/send-custom")
    public Result<Map<String, Object>> sendCustomData(
            @RequestBody Map<String, Object> dataRequest) {
        
        try {
            String data = (String) dataRequest.get("data");
            if (data == null || data.trim().isEmpty()) {
                return Result.error(400, "数据内容不能为空");
            }
            
            // 发送自定义数据
            isimUdpService.sendRawData(data);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "自定义数据已发送到ISIM");
            result.put("data", data);
            result.put("timestamp", System.currentTimeMillis());
            result.put("length", data.length());
            
            log.info("发送自定义数据到ISIM，长度：{}", data.length());
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("发送自定义数据到ISIM失败", e);
            return Result.error(500, "发送自定义数据失败：" + e.getMessage());
        }
    }
    
    /**
     * 更新ISIM目标地址
     */
    @Operation(summary = "更新ISIM目标地址", description = "更新ISIM模拟机的IP地址和端口")
    @PostMapping("/update-target")
    public Result<Map<String, Object>> updateTarget(
            @RequestBody Map<String, Object> targetRequest) {
        
        try {
            String host = (String) targetRequest.get("host");
            Integer sendPort = (Integer) targetRequest.get("sendPort");
            Integer receivePort = (Integer) targetRequest.get("receivePort");
            
            if (host == null || host.trim().isEmpty()) {
                return Result.error(400, "主机地址不能为空");
            }
            
            // 验证主机格式（简单验证）
            if (!host.matches("^([0-9]{1,3}\\.){3}[0-9]{1,3}$") && !host.equals("localhost")) {
                return Result.error(400, "主机地址格式无效");
            }
            
            // 验证端口范围
            if (sendPort != null && (sendPort < 1 || sendPort > 65535)) {
                return Result.error(400, "发送端口必须在1-65535范围内");
            }
            
            if (receivePort != null && (receivePort < 1 || receivePort > 65535)) {
                return Result.error(400, "接收端口必须在1-65535范围内");
            }
            
            // 保存旧配置
            String oldHost = isimConfig.getHost();
            int oldSendPort = isimConfig.getSendPort();
            int oldReceivePort = isimConfig.getReceivePort();
            
            // 更新配置
            isimConfig.setHost(host);
            if (sendPort != null) {
                isimConfig.setSendPort(sendPort);
            }
            if (receivePort != null) {
                isimConfig.setReceivePort(receivePort);
                // 注意：更新接收端口需要重启UDP接收服务，这里仅更新配置
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
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("更新ISIM目标地址失败", e);
            return Result.error(500, "更新ISIM目标地址失败：" + e.getMessage());
        }
    }
}