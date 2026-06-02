package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("route_version")
public class RouteVersion implements Serializable {

    @TableId(value = "route_version_id", type = IdType.INPUT)
    private String routeVersionId;

    private String routeId;
    private Integer versionNo;
    private Double cruiseHeightM;
    private String geometryJson;
    private Integer waypointCount;
    private Double distanceM;
    private String status;
    private LocalDateTime createdAt;
    private String createdBy;
}
