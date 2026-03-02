package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 重点关注区域实体类
 *
 * @author BlueSky Team
 */
@Data
@TableName("monitoring_points")
public class MonitoringPoint implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 重点关注区域ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 重点关注区域名称
     */
    private String name;

    /**
     * 监测点编号
     */
    private String code;

    /**
     * 类型: takeoff/operation
     */
    private String type;

    /**
     * 位置描述
     */
    private String location;

    /**
     * 中心经度
     */
    private BigDecimal longitude;

    /**
     * 中心纬度
     */
    private BigDecimal latitude;

    /**
     * 边界框最小经度
     */
    private BigDecimal bboxMinLng;

    /**
     * 边界框最小纬度
     */
    private BigDecimal bboxMinLat;

    /**
     * 边界框最大经度
     */
    private BigDecimal bboxMaxLng;

    /**
     * 边界框最大纬度
     */
    private BigDecimal bboxMaxLat;

    /**
     * 海拔高度(米)
     */
    private BigDecimal altitude;

    /**
     * 状态: available/warning/unavailable
     */
    private String status;

    /**
     * 警告原因
     */
    private String warningReason;

    /**
     * 最后更新时间戳(毫秒)
     */
    private Long lastUpdate;

    /**
     * 是否激活
     */
    private Boolean isActive;

    /**
     * 是否被选中
     */
    private Boolean isSelected;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 更新人
     */
    private String updatedBy;
}
