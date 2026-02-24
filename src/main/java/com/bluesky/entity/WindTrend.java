package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 风向趋势数据实体类
 */
@Data
@TableName("wind_trend")
public class WindTrend implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 重点关注区域ID */
    private String pointId;

    /** 数据时间 */
    private LocalDateTime dataTime;

    /** 时间标签(HH:mm) */
    private String timeLabel;

    /** 风速(m/s) */
    private BigDecimal windSpeed;

    /** 风向(度) */
    private Integer windDir;

    /** 风速上限(m/s) */
    private BigDecimal upperLimit;

    /** 风速下限(m/s) */
    private BigDecimal lowerLimit;

    /** 偏差值(m/s) */
    private BigDecimal deviation;

    private LocalDateTime createdAt;
}
