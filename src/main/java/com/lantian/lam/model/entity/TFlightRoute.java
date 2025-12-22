package com.lantian.lam.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.locationtech.jts.geom.LineString;


import java.util.Date;

@Data
@TableName("t_flight_route")
public class TFlightRoute {
    @TableId
    private Long routeId;

    private String routeName;

    private LineString geom;  // PostGIS Geometry 类型

    private Double minHeight;

    private Double maxHeight;

    private String remark;

    private Date createTime;

    private String startName;

    private String endName;
}
