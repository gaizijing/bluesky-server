package com.lantian.lam.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("t_weather_risk_rule")
public class TWeatherRiskRule {
    @TableId
    private Long ruleId;  // 主键

    private Long elemId;  // 外键，引用 t_weather_element 表

    private Integer riskLevel;  // 外键，引用 t_risk_level 表

    private Double minValue;  // 对应 numeric(10, 3)

    private Double maxValue;  // 对应 numeric(10, 3)

    private Double minHeight;  // 对应 numeric(6, 1)

    private Double maxHeight;  // 对应 numeric(6, 1)

    private String description;  // 对应 text 类型
}
