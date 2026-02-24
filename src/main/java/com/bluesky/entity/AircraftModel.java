package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 飞行器型号实体类
 */
@Data
@TableName("aircraft_models")
public class AircraftModel implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 型号名称 */
    private String modelName;

    /** 类别: 直升机/固定翼/多旋翼/无人机 */
    private String category;

    /** 制造商 */
    private String manufacturer;

    /** 最大飞行高度(米) */
    private Integer maxAltitude;

    /** 最大速度(km/h) */
    private BigDecimal maxSpeed;

    /** 巡航速度(km/h) */
    private BigDecimal cruiseSpeed;

    /** 最大航程(km) */
    private BigDecimal maxRange;

    /** 最大续航时间(分钟) */
    private Integer maxEndurance;

    /** 最大载重(kg) */
    private BigDecimal maxPayload;

    /** 描述 */
    private String description;

    /** 是否启用 */
    private Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
