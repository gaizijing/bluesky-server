package com.bluesky.service.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 适飞分析结果
 */
@Data
public class SuitabilityResult {
    /** 计算时间 */
    private LocalDateTime calculationTime;
    
    /** 各因素适飞性 */
    private List<SuitabilityFactor> factors;
    
    /** 综合适飞指数 (0-100) */
    private double overallSuitability;
    
    /** 飞行建议 */
    private String recommendation;
}