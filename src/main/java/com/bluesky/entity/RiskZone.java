package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 低空风险区（禁飞 / 谨慎），圆柱范围由中心、半径、高度描述。
 */
@Data
@TableName("risk_zones")
public class RiskZone implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** NO_FLY | CAUTION */
    private String zoneType;

    private String label;

    private Double centerLng;

    private Double centerLat;

    private Double radiusM;

    private Double heightM;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
