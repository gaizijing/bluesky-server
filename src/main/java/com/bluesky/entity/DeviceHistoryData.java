package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备监测历史数据实体类
 */
@Data
@TableName("device_history_data")
public class DeviceHistoryData implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceId;
    private LocalDateTime dataTime;
    private String dataType;
    private BigDecimal value;
    private String unit;
    private LocalDateTime createdAt;
}
