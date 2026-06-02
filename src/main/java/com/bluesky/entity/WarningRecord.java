package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("warning_records")
public class WarningRecord implements Serializable {

    @TableId(value = "warning_id", type = IdType.INPUT)
    private String warningId;
    private String warningType;
    private String displayRegionId;
    private String targetType;
    private String targetId;
    private String level;
    private String title;
    private String content;
    private String status;
    private String dedupeKey;
    private Integer occurrenceCount;
    private LocalDateTime bucketTime;
    private String ruleVersion;
    private LocalDateTime lastTriggeredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
