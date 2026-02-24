package com.bluesky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 飞行任务实体类
 */
@Data
@TableName("flight_tasks")
public class FlightTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private String taskId;

    /** 任务类型: 救援/物流/巡检/训练 */
    private String type;

    /** 类型颜色代码 */
    private String typeColor;

    /** 飞行器ID */
    private String aircraftId;

    /** 飞行器型号 */
    private String aircraftType;

    /** 起飞点 */
    private String takeoff;

    /** 降落点 */
    private String landing;

    /** 起飞重点关注区域ID */
    private String takeoffPointId;

    /** 降落重点关注区域ID */
    private String landingPointId;

    /** 计划飞行高度(米) */
    private Integer planHeight;

    /** 实际飞行高度(米) */
    private Integer actualHeight;

    /** 状态: waiting/ongoing/completed/cancelled */
    private String status;

    /** 当前位置 */
    private String currentPos;

    /** 当前经度 */
    private BigDecimal currentLng;

    /** 当前纬度 */
    private BigDecimal currentLat;

    /** 气象适配状态: 适配/不适配 */
    private String meteorologyAdapt;

    /** 适配原因说明 */
    private String adaptReason;

    /** 计划开始时间 */
    private LocalTime startTime;

    /** 计划结束时间 */
    private LocalTime endTime;

    /** 实际开始时间 */
    private LocalDateTime actualStartTime;

    /** 实际结束时间 */
    private LocalDateTime actualEndTime;

    /** 任务日期 */
    private LocalDate taskDate;

    /** 航线数据(JSON格式) */
    private String routeData;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
