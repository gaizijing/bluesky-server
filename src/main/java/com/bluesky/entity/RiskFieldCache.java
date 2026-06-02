package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluesky.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "risk_field_cache", autoResultMap = true)
public class RiskFieldCache implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long cacheId;
    private String regionId;
    private LocalDateTime bucketTime;
    private Integer heightM;
    private Double lng;
    private Double lat;
    private BigDecimal value;
    private String level;
    private String reason;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String factorsJson;
    private String ruleVersion;
    private LocalDateTime computedAt;
}
