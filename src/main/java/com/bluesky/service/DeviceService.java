package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.Device;
import com.bluesky.entity.DeviceAlarm;
import com.bluesky.entity.DeviceHistoryData;
import com.bluesky.mapper.DeviceAlarmMapper;
import com.bluesky.mapper.DeviceHistoryDataMapper;
import com.bluesky.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 设备监测服务
 */
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final DeviceAlarmMapper deviceAlarmMapper;
    private final DeviceHistoryDataMapper deviceHistoryDataMapper;

    /**
     * 获取设备统计数量
     */
    public List<Map<String, Object>> getDeviceCount() {
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getIsActive, true)
        );

        // 按类型分组统计
        Map<String, Map<String, Object>> typeMap = new LinkedHashMap<>();
        
        for (Device device : devices) {
            String type = device.getType();
            String typeName = convertTypeToName(type);
            String icon = convertTypeToIcon(type);
            
            typeMap.computeIfAbsent(type, k -> {
                Map<String, Object> map = new HashMap<>();
                map.put("type", type);
                map.put("name", typeName);
                map.put("icon", icon);
                map.put("online", 0);
                map.put("total", 0);
                map.put("status", "normal");
                return map;
            });
            
            Map<String, Object> map = typeMap.get(type);
            map.put("online", (Integer) map.get("online") + device.getOnlineCount());
            map.put("total", (Integer) map.get("total") + device.getTotalCount());
        }

        return new ArrayList<>(typeMap.values());
    }

    /**
     * 获取设备告警列表
     */
    public List<Map<String, Object>> getDeviceAlarms(String date, String level, Integer limit) {
        LambdaQueryWrapper<DeviceAlarm> wrapper = new LambdaQueryWrapper<DeviceAlarm>()
                .orderByDesc(DeviceAlarm::getAlarmTime);
        
        if (date != null && !date.isEmpty()) {
            wrapper.eq(DeviceAlarm::getAlarmDate, LocalDate.parse(date));
        }
        if (level != null && !level.isEmpty()) {
            wrapper.eq(DeviceAlarm::getLevel, level);
        }
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }

        List<DeviceAlarm> alarms = deviceAlarmMapper.selectList(wrapper);
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (DeviceAlarm alarm : alarms) {
            Map<String, Object> map = new HashMap<>();
            map.put("date", alarm.getAlarmDate().toString().substring(5)); // MM-DD
            map.put("deviceType", convertDeviceTypeToChinese(alarm.getDeviceType()));
            map.put("deviceName", alarm.getDeviceName());
            map.put("alarmContent", alarm.getAlarmContent());
            map.put("alarmTime", alarm.getAlarmTime().toString());
            map.put("level", alarm.getLevel());
            result.add(map);
        }
        
        return result;
    }

    /**
     * 获取历史监测数据（从数据库查询）
     */
    public Map<String, Object> getHistoryData() {
        // 生成时间标签（最近12小时）
        List<String> timeLabels = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 11; i >= 0; i--) {
            LocalDateTime time = now.minusHours(i);
            timeLabels.add(String.format("%02d:%02d", time.getHour(), time.getMinute()));
        }

        // 从数据库查询各类型的最新历史数据
        Map<String, Object> trendData = new HashMap<>();
        trendData.put("temperature", queryHistoryDataByType("temperature", 12));
        trendData.put("humidity", queryHistoryDataByType("humidity", 12));
        trendData.put("windSpeed", queryHistoryDataByType("windSpeed", 12));

        Map<String, Object> timelineData = new HashMap<>();
        timelineData.put("radialSpeed", queryHistoryDataByType("radialSpeed", 12));
        timelineData.put("speedStd", queryHistoryDataByType("speedStd", 12));
        timelineData.put("snr", queryHistoryDataByType("snr", 12));

        // 雷达图数据（取最新值）
        List<Map<String, Object>> radarData = new ArrayList<>();
        radarData.add(createRadarItem("温度", getLatestValue("temperature", 25.5)));
        radarData.add(createRadarItem("湿度", getLatestValue("humidity", 64.0)));
        radarData.add(createRadarItem("风速", getLatestValue("windSpeed", 4.5)));
        radarData.add(createRadarItem("风向", 180.0)); // 风向固定值
        radarData.add(createRadarItem("气压", 1012.0)); // 气压固定值

        List<Map<String, Object>> weatherRadarData = new ArrayList<>();
        weatherRadarData.add(createRadarItem("弱回波", 30.0));
        weatherRadarData.add(createRadarItem("中回波", 60.0));
        weatherRadarData.add(createRadarItem("强回波", 90.0));

        Map<String, Object> result = new HashMap<>();
        result.put("timeLabels", timeLabels);
        result.put("trendData", trendData);
        result.put("timelineData", timelineData);
        result.put("radarData", radarData);
        result.put("weatherRadarData", weatherRadarData);
        
        return result;
    }

    /**
     * 根据类型查询历史数据
     */
    private List<Double> queryHistoryDataByType(String dataType, int limit) {
        try {
            List<DeviceHistoryData> dataList = deviceHistoryDataMapper.selectList(
                    new LambdaQueryWrapper<DeviceHistoryData>()
                            .eq(DeviceHistoryData::getDataType, dataType)
                            .orderByDesc(DeviceHistoryData::getDataTime)
                            .last("LIMIT " + limit)
            );
            
            List<Double> result = new ArrayList<>();
            if (dataList != null) {
                for (int i = dataList.size() - 1; i >= 0; i--) {
                    DeviceHistoryData data = dataList.get(i);
                    if (data != null && data.getValue() != null) {
                        result.add(data.getValue().doubleValue());
                    }
                }
            }
            
            // 如果数据不足，用默认值填充
            while (result.size() < limit) {
                result.add(getDefaultValue(dataType));
            }
            
            return result;
        } catch (Exception e) {
            // 出错时返回默认值
            List<Double> result = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                result.add(getDefaultValue(dataType));
            }
            return result;
        }
    }

    /**
     * 获取最新值
     */
    private double getLatestValue(String dataType, double defaultValue) {
        DeviceHistoryData latest = deviceHistoryDataMapper.selectOne(
                new LambdaQueryWrapper<DeviceHistoryData>()
                        .eq(DeviceHistoryData::getDataType, dataType)
                        .orderByDesc(DeviceHistoryData::getDataTime)
                        .last("LIMIT 1")
        );
        return latest != null ? latest.getValue().doubleValue() : defaultValue;
    }

    /**
     * 获取默认值
     */
    private double getDefaultValue(String dataType) {
        return switch (dataType) {
            case "temperature" -> 25.0;
            case "humidity" -> 63.0;
            case "windSpeed" -> 4.5;
            case "radialSpeed" -> 8.5;
            case "speedStd" -> 1.2;
            case "snr" -> 35.0;
            default -> 0.0;
        };
    }

    // ==================== 工具方法 ====================
    
    private String convertTypeToName(String type) {
        return switch (type) {
            case "weatherStation" -> "自动气象站";
            case "windLidarSmall" -> "激光测风雷达";
            case "windLidar3D" -> "三维激光测风雷达";
            case "weatherRadar" -> "小型天气雷达";
            default -> type;
        };
    }

    private String convertTypeToIcon(String type) {
        return switch (type) {
            case "weatherStation", "weatherRadar" -> "btn_img2";
            case "windLidarSmall" -> "btn_img3";
            case "windLidar3D" -> "btn_img4";
            default -> "btn_img2";
        };
    }

    private String convertDeviceTypeToChinese(String type) {
        return switch (type) {
            case "weatherStation" -> "气象站";
            case "windLidarSmall", "windLidar3D" -> "激光雷达";
            case "weatherRadar" -> "雷达";
            default -> type;
        };
    }

    private List<Double> generateRandomData(int count, double min, double max) {
        List<Double> data = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            data.add(min + (max - min) * random.nextDouble());
        }
        return data;
    }

    private Map<String, Object> createRadarItem(String name, double value) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("value", value);
        return map;
    }
}
