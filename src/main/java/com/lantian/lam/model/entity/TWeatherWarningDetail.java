package com.lantian.lam.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_weather_warning_detail")
public class TWeatherWarningDetail {
    @TableId
    private Long detailId;

    private Long warningId;

    private Long elemId;

    private Long ruleId;

    private Double actualValue;

    private Double threshold;

    private String description;
}
