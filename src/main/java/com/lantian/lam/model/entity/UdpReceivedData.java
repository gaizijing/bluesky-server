package com.lantian.lam.model.entity;
/**
 * 飞行数据接收结构体
 */
public class UdpReceivedData {
    public double Longitude;           // 经度
    public double Latitude;            // 纬度
    public double Altitude;            // 高度
    public double Heading;             // 航向
    public double Pitch;               // 俯仰角
    public double Bank;                // 滚转角

    public int Month;                  // 月份
    public int Hour;                   // 小时
    public int Minute;                 // 分钟

    public int WindDirection;          // 风向
    public int WindSpeed;              // 风速
    public int WindHeight;             // 风高
    public int VisibilityDistance;     // 能见度距离
    public int VisibilityBottom;       // 能见度下限
    public int VisibilityTop;          // 能见度上限
    public int RainLevel;              // 降雨等级
    public int SnowLevel;              // 降雪等级
    public int PositiveTemperature;    // 正温度
    public int CloudType;              // 云类型
    public int CloudHeight;            // 云高
    public int CloudBottom;            // 云底高度

    public int Throttle;               // 油门
    public int Throttle1;              // 油门1
    public int Throttle2;              // 油门2
    public int Flaps;                  // 襟翼
    public int Spoilers;               // 扰流板
    public int ParkingBrake;           // 驻车刹车
    public int Gear;                   // 起落架

    public double IAS;                 // 指示空速
    public double Rudder;              // 方向舵
    public double Elevator;            // 升降舵
    public double Aileron;             // 副翼
    public double BrakeL;              // 左刹车
    public double BrakeR;              // 右刹车
}
