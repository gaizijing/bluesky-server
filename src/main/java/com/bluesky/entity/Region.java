package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("region")
public class Region implements Serializable {

    @TableId(value = "region_id", type = IdType.INPUT)
    private String regionId;

    private String name;
    private Double centerLng;
    private Double centerLat;
    private Double west;
    private Double east;
    private Double south;
    private Double north;
    private String mapLiftJson;
    private String modelUrl;
    private Boolean enabled;
    private Boolean isDefault;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
