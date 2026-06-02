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
@TableName(value = "warning_rule_set", autoResultMap = true)
public class WarningRuleSet implements Serializable {

    @TableId(value = "rule_set_id", type = IdType.INPUT)
    private String ruleSetId;
    private String name;
    private Integer versionNo;
    private String status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String rulesJson;
    private Boolean enableLlm;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}
