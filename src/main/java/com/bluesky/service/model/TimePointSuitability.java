package com.bluesky.service.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 时间点适飞数据
 */
@Data
public class TimePointSuitability {
    /** 时间点 */
    private LocalDateTime timePoint;
    
    /** 监测点ID */
    private String pointId;
    
    /** 各因素适飞性 */
    private List<SuitabilityFactor> factors;
    
    /** 该时间点综合适飞指数 */
    private double overallSuitability;
}