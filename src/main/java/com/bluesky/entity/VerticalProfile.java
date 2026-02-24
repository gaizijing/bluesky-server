package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 垂直剖面数据实体类
 */
@Data
@TableName("vertical_profile")
public class VerticalProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 重点关注区域ID */
    private String pointId;

    /** 数据时间 */
    private LocalDateTime dataTime;

    /** 高度(米) */
    private Integer height;

    /** 风速(m/s) */
    private BigDecimal windSpeed;

    /** 温度(℃) */
    private BigDecimal temperature;

    /** 湿度(%) */
    private Integer humidity;

    /** 能见度(km) */
    private BigDecimal visibility;

    /** 气压(hPa) */
    private BigDecimal pressure;

    /** 湍流等级 */
    private String turbulenceLevel;

    private LocalDateTime createdAt;
}
