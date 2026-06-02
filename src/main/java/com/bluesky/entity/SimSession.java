package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sim_session")
public class SimSession implements Serializable {

    @TableId(value = "session_id", type = IdType.INPUT)
    private String sessionId;
    private String regionId;
    private String routeId;
    private String status;
    private Long lastSequence;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
