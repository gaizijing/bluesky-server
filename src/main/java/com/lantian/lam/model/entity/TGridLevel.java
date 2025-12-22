package com.lantian.lam.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_grid_level")
public class TGridLevel {
    @TableId
    private Long levelId;
    private Integer gridLevel;
    private Double minHeight;
    private Double maxHeight;
    private String description;
}
