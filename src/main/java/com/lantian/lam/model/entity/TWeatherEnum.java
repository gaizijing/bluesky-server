package com.lantian.lam.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_weather_enum")
public class TWeatherEnum {
    @TableId
    private Long enumId;  // 主键

    private Long elemId;  // 外键，引用 t_weather_element 表

    private String enumCode;

    private String enumName;

    private Integer orderNo;
}
