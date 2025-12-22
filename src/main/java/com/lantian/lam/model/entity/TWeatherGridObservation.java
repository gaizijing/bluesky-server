package com.lantian.lam.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;

@Data
@TableName("t_weather_grid_observation")
public class TWeatherGridObservation {
    @TableId
    private Long id;  // 主键

    private Long elemId;

    private Date obsTime;

    private Integer gridX;

    private Integer gridY;

    private Integer gridLevel;

    private BigDecimal value;  // 对应 numeric(10, 3) 类型

    private String dataType;

    private Date  createTime;

    private Polygon geom;    // 使用 PostGIS 的 Geometry 类型
}
