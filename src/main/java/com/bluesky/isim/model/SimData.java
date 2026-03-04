package com.bluesky.isim.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * ISIM模拟机数据模型
 * 对应C++ UE5VisualUnit.cpp发送的数据格式
 */
@Data
public class SimData {
    // 数据头标识
    private String header = "UE5_SIM_DATA";
    
    // 飞机核心姿态+位置（最常用）
    private double aircraftRoll;     // 飞机滚转角（度）
    private double aircraftPitch;    // 飞机俯仰角（度）
    private double aircraftHeading;  // 飞机真航向（度）
    private double aircraftLon;      // 飞机重心经度
    private double aircraftLat;      // 飞机重心纬度
    private double aircraftAlt;      // 飞机重心高度（海拔，米）
    
    // 眼点位置（飞行员视角）
    private double eyeLon;           // 眼点经度
    private double eyeLat;           // 眼点纬度
    private double eyeAlt;           // 眼点高度
    
    // 基础开关
    private int trailHide;           // 隐藏尾迹 0/1
    private int airwayHide;          // 隐藏航路 0/1
    
    // 第三视角位置+姿态
    private double observeLon;       // 第三视角经度
    private double observeLat;       // 第三视角纬度
    private double observeAlt;       // 第三视角高度
    private double observePitch;     // 第三视角俯仰角
    private double observeHeading;   // 第三视角航向
    
    // 本机灯光
    private int ownshipLight;        // 本机灯光 0/1
    
    // 时间戳（后端添加）
    private LocalDateTime timestamp = LocalDateTime.now();
    
    // 数据来源标识
    private String source = "ISIM";
}