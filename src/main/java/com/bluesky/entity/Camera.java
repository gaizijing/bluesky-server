package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 摄像头实体类
 */
@Data
@TableName("cameras")
public class Camera implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String name;
    private String location;
    private String pointId;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String status;
    private String resolution;
    private String previewUrl;
    private String streamUrl;
    private Long lastHeartbeat;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
