package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluesky.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName(value = "no_fly_zone", autoResultMap = true)
public class NoFlyZone implements Serializable {

    @TableId(value = "zone_id", type = IdType.INPUT)
    private String zoneId;
    private String regionId;
    private String name;
    private String zoneType;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String geometryJson;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Boolean enabled;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
