package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 天气预报数据
 * 存储 Open-Meteo API 获取的 15 分钟间隔预报数据
 */
@Data
@TableName("weather_forecast")
public class WeatherForecast {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 监测点ID
     */
    private String pointId;
    
    /**
     * 预报时间
     */
    private LocalDateTime forecastTime;
    
    /**
     * 温度(℃)
     */
    private BigDecimal temperature;
    
    /**
     * 风速(m/s)
     */
    private BigDecimal windSpeed;
    
    /**
     * 能见度(km)
     */
    private BigDecimal visibility;
    
    /**
     * 降水量(mm)
     */
    private BigDecimal precipitation;
    
    /**
     * 天气代码(WMO code)
     */
    private Integer weatherCode;
    
    /**
     * 天气文字描述
     */
    private String weatherText;
    
   
    
    /**
     * 数据来源
     */
    private String dataSource;
    
    /**
     * 数据质量
     */
    private Integer dataQuality;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}