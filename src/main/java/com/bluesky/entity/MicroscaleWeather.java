package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 微尺度天气数据实体类
 */
@Data
@TableName("microscale_weather")
public class MicroscaleWeather implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 监测点ID */
    @TableField("point_id")
    private String pointId;

    /** 数据时间 */
    private LocalDateTime dataTime;

    /** 网格大小(米) */
    private Integer gridSize;

    /** 网格X坐标 */
    private BigDecimal gridX;

    /** 网格Y坐标 */
    private BigDecimal gridY;

    /** 风险等级(0-3) */
    private Integer riskLevel;

    /** 风速 */
    private BigDecimal windSpeed;

    /** 风切变 */
    private BigDecimal windShear;

    /** 湍流 */
    private BigDecimal turbulence;

    private LocalDateTime createdAt;
}
