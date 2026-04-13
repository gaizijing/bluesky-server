package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 地区配置实体类
 *
 * @author BlueSky Team
 */
@Data
@TableName("region_config")
public class RegionConfigEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 地区名称
     */
    private String name;

    /**
     * 西边界经度
     */
    private Double west;

    /**
     * 东边界经度
     */
    private Double east;

    /**
     * 南边界纬度
     */
    private Double south;

    /**
     * 北边界纬度
     */
    private Double north;

    /**
     * 是否为默认配置
     */
    private Boolean isDefault;

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
