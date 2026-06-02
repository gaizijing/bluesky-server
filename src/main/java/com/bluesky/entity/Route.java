package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("routes")
public class Route implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String regionId;
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
    private String currentVersionId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
