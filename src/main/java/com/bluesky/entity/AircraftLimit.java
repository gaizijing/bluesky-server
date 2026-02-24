package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 飞行器气象限制实体类
 */
@Data
@TableName("aircraft_limits")
public class AircraftLimit implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 飞行器ID */
    private String aircraftId;

    /** 最大风速限制(m/s) */
    private BigDecimal maxWindSpeed;

    /** 最大风切变限制(m/s) */
    private BigDecimal maxWindShear;

    /** 最小能见度限制(km) */
    private BigDecimal minVisibility;

    /** 最大降水量限制(mm) */
    private BigDecimal maxPrecipitation;

    /** 最小云底高度限制(米) */
    private Integer minCloudBase;

    /** 最低温度限制(℃) */
    private BigDecimal tempMin;

    /** 最高温度限制(℃) */
    private BigDecimal tempMax;

    /** 最大湿度限制(%) */
    private Integer maxHumidity;

    /** 最大湍流等级 */
    private String maxTurbulenceLevel;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
