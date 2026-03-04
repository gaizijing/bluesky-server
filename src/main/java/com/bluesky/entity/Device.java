package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备实体类
 */
@Data
@TableName("devices")
public class Device implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String name;
    private String code;
    private String type;
    private String pointId;
    private String location;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal altitude;
    private String status;
    private Integer onlineCount;
    private Integer totalCount;
    private Long lastHeartbeat;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
