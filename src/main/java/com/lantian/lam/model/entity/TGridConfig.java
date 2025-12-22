package com.lantian.lam.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.locationtech.jts.geom.Polygon;

@Data
@TableName("t_grid_config")
public class TGridConfig {
    @TableId
    private Long gridId;

    private String gridName;

    private Double gridSize;

    private Polygon geom;  // PostGIS Geometry 类型

    private String remark;

    private Short state;
}
