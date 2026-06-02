package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluesky.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName(value = "weather_grid_cache", autoResultMap = true)
public class WeatherGridCache implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long cacheId;
    private String regionId;
    private LocalDateTime bucketTime;
    private Integer heightM;
    private String product;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String gridJson;
    private LocalDateTime dataSourceTime;
    private LocalDateTime computedAt;
    private LocalDateTime expiresAt;
}
