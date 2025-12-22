package com.lantian.lam.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.locationtech.jts.geom.Polygon;
import java.util.Date;

@Data
@TableName("t_focus_region")
public class TFocusRegion {
    @TableId
    private Long regionId;
    private String regionName;

    private String regionType;

    private Polygon geom;  // PostGIS Geometry 类型

    private Double minHeight;

    private Double maxHeight;

    private String remark;

    private Short state;

    private Date createTime;
}
