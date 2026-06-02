package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("landing_point")
public class LandingPoint implements Serializable {

    @TableId(value = "landing_point_id", type = IdType.INPUT)
    private String landingPointId;

    private String regionId;
    private String name;
    private String code;
    private String type;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal altitude;
    private BigDecimal bboxMinLng;
    private BigDecimal bboxMinLat;
    private BigDecimal bboxMaxLng;
    private BigDecimal bboxMaxLat;
    private Boolean enabled;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
