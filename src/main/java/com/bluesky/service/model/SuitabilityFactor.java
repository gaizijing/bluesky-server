package com.bluesky.service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 适飞分析因素
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuitabilityFactor {
    /** 因素名称 */
    private String name;
    
    /** 是否适飞 */
    private boolean suitable;
    
    /** 当前值 */
    private double value;
    
    /** 阈值（可选） */
    private Double threshold;
    
    public SuitabilityFactor(String name, boolean suitable, double value) {
        this.name = name;
        this.suitable = suitable;
        this.value = value;
    }
}