package com.bluesky.isim.service;

import com.bluesky.entity.MonitoringPoint;
import com.bluesky.entity.WeatherRealtime;
import com.bluesky.isim.model.WeatherData;
import com.bluesky.service.MonitoringPointService;
import com.bluesky.service.WeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 气象数据服务
 * 负责获取和准备发送给ISIM的气象数据
 */
@Slf4j
@Service
public class WeatherDataService {
    
    private final WeatherService weatherService;
    private final MonitoringPointService monitoringPointService;
    
    @Autowired
    public WeatherDataService(WeatherService weatherService,
                             MonitoringPointService monitoringPointService) {
        this.weatherService = weatherService;
        this.monitoringPointService = monitoringPointService;
    }
    
    /**
     * 获取指定监测点的最新气象数据
     */
    public WeatherData getWeatherDataByPointId(String pointId) {
        try {
            // 获取气象数据
            Map<String, Object> weatherResult = weatherService.getRealtimeWeather(pointId);
            if (weatherResult == null || !weatherResult.containsKey("data")) {
                log.warn("未找到监测点 {} 的气象数据", pointId);
                return createDefaultWeatherData(pointId);
            }
            
            WeatherRealtime weatherRealtime = (WeatherRealtime) weatherResult.get("data");
            if (weatherRealtime == null) {
                return createDefaultWeatherData(pointId);
            }
            
            // 获取监测点位置信息
            MonitoringPoint point = monitoringPointService.getById(pointId);
            
            // 转换为WeatherData
            WeatherData weatherData = new WeatherData();
            weatherData.setWindDirection(BigDecimal.valueOf(weatherRealtime.getWind360()));
            weatherData.setWindSpeed(weatherRealtime.getWindSpeed());
            weatherData.setTemperature(weatherRealtime.getTemp());
            weatherData.setHumidity(BigDecimal.valueOf(weatherRealtime.getHumidity()));
            weatherData.setPressure(weatherRealtime.getPressure());
            weatherData.setVisibility(weatherRealtime.getVis());
            weatherData.setCloudCover(BigDecimal.valueOf(weatherRealtime.getCloud()));
            weatherData.setPrecipitation(weatherRealtime.getPrecip());
            weatherData.setTimestamp(weatherRealtime.getObsTime());
            weatherData.setPointId(pointId);
            
            // 设置位置信息
            if (point != null) {
                weatherData.setLongitude(point.getLongitude());
                weatherData.setLatitude(point.getLatitude());
                weatherData.setAltitude(point.getAltitude());
            }
            
            // 尝试获取湍流和风切变数据（如果存在）
            // 注意：WeatherRealtime实体可能没有这些字段，需要根据实际情况调整
            try {
                if (weatherRealtime.getWindShearLevel() != null) {
                    // 将风切变等级转换为数值
                    BigDecimal windShearValue = convertWindShearLevel(weatherRealtime.getWindShearLevel());
                    weatherData.setWindShear(windShearValue);
                }
                
                if (weatherRealtime.getStabilityIndex() != null) {
                    // 将稳定指数转换为湍流强度
                    BigDecimal turbulenceValue = convertStabilityIndex(weatherRealtime.getStabilityIndex());
                    weatherData.setTurbulenceIntensity(turbulenceValue);
                }
            } catch (Exception e) {
                log.debug("无法获取湍流或风切变数据", e);
            }
            
            return weatherData;
            
        } catch (Exception e) {
            log.error("获取监测点 {} 气象数据失败", pointId, e);
            return createDefaultWeatherData(pointId);
        }
    }
    
    /**
     * 获取当前选中监测点的最新气象数据
     */
    public WeatherData getLatestWeatherData() {
        try {
            // 获取当前选中的监测点
            MonitoringPoint selectedPoint = monitoringPointService.getSelected();
            if (selectedPoint == null) {
                log.warn("未找到选中的监测点");
                return createDefaultWeatherData(null);
            }
            
            return getWeatherDataByPointId(selectedPoint.getId());
            
        } catch (Exception e) {
            log.error("获取最新气象数据失败", e);
            return createDefaultWeatherData(null);
        }
    }
    
    /**
     * 根据经纬度获取最近监测点的气象数据
     * @param longitude 经度
     * @param latitude 纬度
     * @return 最近监测点的气象数据
     */
    public WeatherData getWeatherDataByLocation(BigDecimal longitude, BigDecimal latitude) {
        try {
            if (longitude == null || latitude == null) {
                log.warn("经纬度参数为空，使用默认气象数据");
                return createDefaultWeatherData(null);
            }
            
            // 找到距离指定位置最近的监测点
            List<MonitoringPoint> allPoints = monitoringPointService.getAll();
            if (allPoints == null || allPoints.isEmpty()) {
                log.warn("没有可用的监测点，使用默认气象数据");
                return createDefaultWeatherData(null);
            }
            
            // 计算距离并找到最近的点
            MonitoringPoint nearestPoint = null;
            double minDistance = Double.MAX_VALUE;
            
            for (MonitoringPoint point : allPoints) {
                if (point.getLongitude() == null || point.getLatitude() == null) {
                    continue;
                }
                
                // 计算两点间距离（简化球面距离）
                double distance = calculateDistance(
                    latitude.doubleValue(), longitude.doubleValue(),
                    point.getLatitude().doubleValue(), point.getLongitude().doubleValue()
                );
                
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestPoint = point;
                }
            }
            
            if (nearestPoint == null) {
                log.warn("未找到有效监测点，使用默认气象数据");
                return createDefaultWeatherData(null);
            }
            
            log.debug("找到最近监测点: {}, 距离: {}km", nearestPoint.getName(), minDistance);
            
            // 获取该监测点的气象数据
            return getWeatherDataByPointId(nearestPoint.getId());
            
        } catch (Exception e) {
            log.error("根据经纬度获取气象数据失败: longitude={}, latitude={}", longitude, latitude, e);
            return createDefaultWeatherData(null);
        }
    }
    
    /**
     * 计算两个坐标点之间的距离（公里）
     * 使用Haversine公式计算球面距离
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 地球半径（公里）
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    /**
     * 创建默认气象数据（后备方案）
     */
    private WeatherData createDefaultWeatherData(String pointId) {
        WeatherData weatherData = new WeatherData();
        weatherData.setWindDirection(BigDecimal.valueOf(45));  // 东北风
        weatherData.setWindSpeed(BigDecimal.valueOf(5.0));     // 5米/秒
        weatherData.setTemperature(BigDecimal.valueOf(20.0));  // 20摄氏度
        weatherData.setHumidity(BigDecimal.valueOf(60));       // 60%湿度
        weatherData.setPressure(BigDecimal.valueOf(1013.0));   // 标准大气压
        weatherData.setVisibility(BigDecimal.valueOf(10000));  // 10公里能见度
        weatherData.setCloudCover(BigDecimal.valueOf(30));     // 30%云量
        weatherData.setPrecipitation(BigDecimal.valueOf(0));   // 无降水
        weatherData.setTimestamp(LocalDateTime.now());
        weatherData.setPointId(pointId);
        weatherData.setLongitude(BigDecimal.valueOf(120.3844));
        weatherData.setLatitude(BigDecimal.valueOf(36.1052));
        weatherData.setAltitude(BigDecimal.valueOf(100.0));
        
        return weatherData;
    }
    
    /**
     * 风切变等级转换为数值
     * 简化处理：low=1, medium=2, high=3
     */
    private BigDecimal convertWindShearLevel(String level) {
        if (level == null) return BigDecimal.ZERO;
        
        switch (level.toLowerCase()) {
            case "low": return BigDecimal.ONE;
            case "medium": return BigDecimal.valueOf(2);
            case "high": return BigDecimal.valueOf(3);
            default: return BigDecimal.ZERO;
        }
    }
    
    /**
     * 稳定指数转换为湍流强度
     * 简化处理：A=1(强稳定), B=2, C=3(中性), D=4, E=5, F=6(强不稳定)
     */
    private BigDecimal convertStabilityIndex(String index) {
        if (index == null || index.isEmpty()) return BigDecimal.valueOf(3);
        
        char grade = index.charAt(0);
        switch (grade) {
            case 'A': return BigDecimal.ONE;
            case 'B': return BigDecimal.valueOf(2);
            case 'C': return BigDecimal.valueOf(3);
            case 'D': return BigDecimal.valueOf(4);
            case 'E': return BigDecimal.valueOf(5);
            case 'F': return BigDecimal.valueOf(6);
            default: return BigDecimal.valueOf(3);
        }
    }
}