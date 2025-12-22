package com.lantian.lam.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_risk_level")
public class TRiskLevel {
    @TableId
    private Integer riskLevel;  // 小整数类型（smallint）
    private String riskName;
    private String color;
    private String description;
}
