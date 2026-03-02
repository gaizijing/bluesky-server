package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 适飞分析数据实体类
 */
@Data
@TableName("suitability_analysis")
public class SuitabilityAnalysis implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 重点关注区域ID */
    private String pointId;

    /** 分析时间 */
    private LocalDateTime analysisTime;

    /** 时间间隔(分钟) */
    private Integer timeInterval;

    /** 预测总时长(小时) */
    private Integer totalHours;

    /** 气象因素: 综合/风/风切变/颠簸指数/湍流/降水/能见度 */
    private String factor;

    /** 时间点 */
    private String timePoint;

    /** 是否适飞 */
    private Boolean isSuitable;

    /** 异常值（数值类型） */
    private BigDecimal abnormalValue;

    private LocalDateTime createdAt;
}
