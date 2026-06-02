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
@TableName(value = "osi_landing_cache", autoResultMap = true)
public class OsiLandingCache implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long cacheId;
    private String landingPointId;
    private LocalDateTime bucketTime;
    private String level;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String factorResultsJson;
    private String ruleVersion;
    private LocalDateTime computedAt;
}
