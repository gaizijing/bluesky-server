package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 航路途经点实体类
 */
@Data
@TableName("route_waypoints")
public class RouteWaypoint implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String routeId;
    private Integer sequence;
    private String name;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal altitude;
    private LocalDateTime createdAt;
}
