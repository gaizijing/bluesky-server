package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 航路实体类
 */
@Data
@TableName("routes")
public class Route implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String name;
    private String startName;
    private String endName;
    private Double distance;
    private Integer estimatedTime;
    private String weatherCondition;
    private String status;
    private Double averageRisk;
    private Boolean isActive;
    private String aircraftModel;
    private Double flightHeight;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
