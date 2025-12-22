package com.lantian.lam.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.locationtech.jts.geom.Geometry;

import java.sql.Timestamp;
import java.util.Date;

@Data
@TableName("t_weather_warning")
public class TWeatherWarning {
    @TableId
    private Long warningId;

    private String warningType;

    private String targetType;

    private Long targetId;

    private Geometry geom;  // PostGIS Geometry 类型

    private Integer riskLevel;

    private Date startTime;

    private Date endTime;

    private String status;

    private String message;

    private Date createTime;
}
