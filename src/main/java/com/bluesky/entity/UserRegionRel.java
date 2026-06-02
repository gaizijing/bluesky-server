package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("user_region_rel")
public class UserRegionRel implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;
    private String regionId;
    private LocalDateTime createdAt;
}
