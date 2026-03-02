package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 实时气象数据实体类
 */
@Data
@TableName("weather_realtime")
public class WeatherRealtime implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 重点关注区域ID */
    private String pointId;

    /** 观测时间 */
    private LocalDateTime obsTime;

    /** 温度(℃) */
    private BigDecimal temp;

    /** 体感温度(℃) */
    private BigDecimal feelsLike;

    /** 天气图标代码 */
    private String icon;

    /** 天气状况文字 */
    private String text;

    /** 风向360度 */
    private Integer wind360;

    /** 风向文字 */
    private String windDir;

    /** 风力等级 */
    private String windScale;

    /** 风速(km/h) */
    private BigDecimal windSpeed;

    /** 相对湿度(%) */
    private Integer humidity;

    /** 降水量(mm) */
    private BigDecimal precip;

    /** 大气压强(hPa) */
    private BigDecimal pressure;

    /** 能见度(km) */
    private BigDecimal vis;

    /** 云量(%) */
    private Integer cloud;

    /** 露点温度(℃) */
    private BigDecimal dew;

    /** 风切变等级: low/medium/high */
    private String windShearLevel;

    /** 稳定度指数: S/A/B/C */
    private String stabilityIndex;

    /** 数据来源 */
    private String dataSource;

    /** 数据质量: good/excellent/fair/poor 或 分数(0-100) */
    private Integer dataQuality;

    private LocalDateTime createdAt;
}
