package com.lantian.lam.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.locationtech.jts.geom.Point;

import java.sql.Timestamp;
import java.util.Date;

@Data
@TableName("t_weather_observation")
public class TWeatherObservation {
    @TableId
    private Long obsId;

    private Long elemId;

    private Date obsTime;

    private Point geom;  // PostGIS Geometry 类型

    private Double height;

    private Double valueNum;

    private String valueEnum;

    private String dataType;

    private Date createTime;
}
