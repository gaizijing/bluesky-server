package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 风场数据实体类
 */
@Data
@TableName("wind_field")
public class WindField implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据时间 */
    private LocalDateTime dataTime;

    /** 高度(米) */
    private Integer height;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 东西方向风速分量(m/s) */
    private BigDecimal uComponent;

    /** 南北方向风速分量(m/s) */
    private BigDecimal vComponent;

    /** 风速(m/s) */
    private BigDecimal speed;

    /** 风向(度) */
    private Integer direction;

    private LocalDateTime createdAt;
}
