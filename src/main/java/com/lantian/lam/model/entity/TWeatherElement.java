package com.lantian.lam.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_weather_element")
public class TWeatherElement {
    @TableId
    private Long elemId;

    private String elemCode;

    private String elemName;

    private String unit;

    private String valueType;

    private String description;

    private Short state;
}
