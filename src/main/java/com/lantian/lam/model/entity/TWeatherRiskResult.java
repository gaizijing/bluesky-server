package com.lantian.lam.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.locationtech.jts.geom.Geometry;

import java.sql.Timestamp;
import java.util.Date;

@Data
@TableName("t_weather_risk_result")
public class TWeatherRiskResult {
    @TableId
    private Long resultId;

    private Long elemId;

    private Long ruleId;

    private Integer riskLevel;

    private Date obsTime;

    private Geometry geom;  // PostGIS Geometry 类型

    private Double height;

    private String dataType;

    private Date createTime;
}
