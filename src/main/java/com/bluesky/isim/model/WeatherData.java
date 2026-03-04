package com.bluesky.isim.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发送给ISIM的气象数据模型
 */
@Data
public class WeatherData {
    // 气象要素
    private BigDecimal windDirection;      // 风向（度，0-360）
    private BigDecimal windSpeed;          // 风速（米/秒）
    private BigDecimal temperature;        // 温度（摄氏度）
    private BigDecimal humidity;           // 湿度（百分比）
    private BigDecimal pressure;           // 气压（百帕）
    private BigDecimal visibility;         // 能见度（米）
    private BigDecimal cloudCover;         // 云量（百分比）
    private BigDecimal precipitation;      // 降水量（毫米/小时）
    
    // 位置信息
    private BigDecimal longitude;          // 经度
    private BigDecimal latitude;           // 纬度
    private BigDecimal altitude;           // 海拔（米）
    
    // 时间戳
    private LocalDateTime timestamp;
    
    // 数据来源
    private String pointId;                // 监测点ID
    private String source = "BLUESKY";     // 数据来源标识
    
    // 湍流相关（可选）
    private BigDecimal turbulenceIntensity; // 湍流强度
    private BigDecimal windShear;           // 风切变
    
    /**
     * 转换为ISIM格式的字符串
     * 格式待定，需要根据ISIM要求定义
     */
    public String toIsimFormat() {
        // 默认格式：用分号分隔，顺序固定
        // 格式：windDirection;windSpeed;temperature;humidity;pressure;visibility;cloudCover;precipitation;longitude;latitude;altitude
        return String.format("%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s",
                windDirection != null ? windDirection.toPlainString() : "0",
                windSpeed != null ? windSpeed.toPlainString() : "0",
                temperature != null ? temperature.toPlainString() : "20",
                humidity != null ? humidity.toPlainString() : "50",
                pressure != null ? pressure.toPlainString() : "1013",
                visibility != null ? visibility.toPlainString() : "10000",
                cloudCover != null ? cloudCover.toPlainString() : "30",
                precipitation != null ? precipitation.toPlainString() : "0",
                longitude != null ? longitude.toPlainString() : "120.0",
                latitude != null ? latitude.toPlainString() : "36.0",
                altitude != null ? altitude.toPlainString() : "100");
    }
    
    /**
     * 从气象实体创建WeatherData
     */
    public static WeatherData fromWeatherRealtime(com.bluesky.entity.WeatherRealtime weather) {
        if (weather == null) {
            return null;
        }
        
        WeatherData data = new WeatherData();
        data.setWindDirection(BigDecimal.valueOf(weather.getWind360()));
        data.setWindSpeed(weather.getWindSpeed());
        data.setTemperature(weather.getTemp());
        data.setHumidity(BigDecimal.valueOf(weather.getHumidity()));
        data.setPressure(weather.getPressure());
        data.setVisibility(weather.getVis());
        data.setCloudCover(BigDecimal.valueOf(weather.getCloud()));
        data.setPrecipitation(weather.getPrecip());
        data.setTimestamp(weather.getObsTime());
        
        // 位置信息需要从监测点获取，这里先留空
        return data;
    }
}