package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备告警实体类
 */
@Data
@TableName("device_alarms")
public class DeviceAlarm implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String deviceId;
    private String deviceType;
    private String deviceName;
    private String alarmContent;
    private LocalDateTime alarmTime;
    private LocalDate alarmDate;
    private String level;
    private String status;
    private String handler;
    private LocalDateTime handleTime;
    private String handleRemark;
    private LocalDateTime createdAt;
}
