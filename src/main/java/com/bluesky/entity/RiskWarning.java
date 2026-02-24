package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 风险预警实体类
 *
 * @author BlueSky Team
 */
@Data
@TableName("risk_warnings")
public class RiskWarning implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 预警ID,格式: RW+日期+序号
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 重点关注区域ID
     */
    private String pointId;

    /**
     * 预警等级: danger/warning/info
     */
    private String level;

    /**
     * 预警类型
     */
    private String type;

    /**
     * 影响区域
     */
    private String area;

    /**
     * 预警开始时间
     */
    private LocalTime startTime;

    /**
     * 预警结束时间
     */
    private LocalTime endTime;

    /**
     * 预警日期
     */
    private LocalDate warningDate;

    /**
     * 详细描述
     */
    private String detail;

    /**
     * 建议措施
     */
    private String suggestion;

    /**
     * 处理状态: unhandled/handled
     */
    private String handleStatus;

    /**
     * 处理人
     */
    private String handler;

    /**
     * 处理时间
     */
    private LocalDateTime handleTime;

    /**
     * 处理备注
     */
    private String handleRemark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
