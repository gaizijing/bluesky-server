package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 航路航段实体类
 */
@Data
@TableName("route_segments")
public class RouteSegment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String routeId;
    private Integer sequence;
    private String startWaypointId;
    private String endWaypointId;
    private Double distance;
    private String windDirection;
    private Double windSpeed;
    private Double visibility;
    private String precipitation;
    private String riskLevel;
    private LocalDateTime createdAt;
}
