package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("warning_handle_records")
public class WarningHandleRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String warningId;
    private String action;
    private String operatorId;
    private String remark;
    private LocalDateTime createdAt;
}
