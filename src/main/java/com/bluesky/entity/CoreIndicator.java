package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 核心气象要素实体类
 */
@Data
@TableName("core_indicators")
public class CoreIndicator implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 重点关注区域ID */
    private String pointId;

    /** 数据时间 */
    private LocalDateTime dataTime;

    /** 要素ID: temp/humidity/windSpeed等 */
    private String indicatorId;

    /** 要素名称 */
    private String indicatorName;

    /** 当前值 */
    private BigDecimal value;

    /** 单位 */
    private String unit;

    /** 精度说明 */
    private String precision;

    /** 状态: normal/warning/danger */
    private String status;

    /** 警告阈值 */
    private BigDecimal thresholdWarning;

    /** 危险阈值 */
    private BigDecimal thresholdDanger;

    private LocalDateTime createdAt;
}
