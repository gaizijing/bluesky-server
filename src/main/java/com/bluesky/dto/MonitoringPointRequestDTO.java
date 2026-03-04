package com.bluesky.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 前端传入的监测点数据（包含 coordinates 和 bbox 数组格式）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonitoringPointRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String code;
    private String type;
    private String location;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private List<BigDecimal> coordinates;

    // bbox 支持多种格式：
    // 1. 数组格式: [[minLng, minLat], [maxLng, maxLat]]
    // 2. 对象格式: {west, south, east, north} 或 {minLng, minLat, maxLng, maxLat}
    // 3. Map格式
    @JsonProperty("bbox")
    private Object bboxRaw;
    
    // 内部使用的对象格式
    private BboxObject bboxObject;

    @Data
    public static class BboxObject implements Serializable {
        private static final long serialVersionUID = 1L;
        private BigDecimal west;
        private BigDecimal south;
        private BigDecimal east;
        private BigDecimal north;
    }

    private BigDecimal altitude;
    private String status;
    private String warningReason;
    private Long lastUpdate;
    private Boolean isActive;
    private String createdBy;
    private String updatedBy;

    /**
     * 判断是否包含有效的 bbox 数据
     */
    public boolean hasValidBbox() {
        return bboxRaw != null || (bboxObject != null && bboxObject.getWest() != null);
    }

    /**
     * 获取 bbox 的最小经度
     */
    public BigDecimal getBboxMinLng() {
        // 首先检查bboxObject
        if (bboxObject != null && bboxObject.getWest() != null) {
            return bboxObject.getWest();
        }
        
        // 处理bboxRaw
        if (bboxRaw != null) {
            // 如果是二维数组格式
            if (bboxRaw instanceof List) {
                List<?> bboxList = (List<?>) bboxRaw;
                if (!bboxList.isEmpty() && bboxList.get(0) instanceof List) {
                    List<?> minPoint = (List<?>) bboxList.get(0);
                    if (!minPoint.isEmpty() && minPoint.get(0) instanceof Number) {
                        return new BigDecimal(minPoint.get(0).toString());
                    }
                }
            }
            // 如果是Map格式（对象格式）
            else if (bboxRaw instanceof Map) {
                Map<?, ?> bboxMap = (Map<?, ?>) bboxRaw;
                Object value = bboxMap.get("west") != null ? bboxMap.get("west") : 
                              bboxMap.get("minLng") != null ? bboxMap.get("minLng") : 
                              bboxMap.get(0);
                if (value instanceof Number) {
                    return new BigDecimal(value.toString());
                }
            }
        }
        return null;
    }

    /**
     * 获取 bbox 的最小纬度
     */
    public BigDecimal getBboxMinLat() {
        // 首先检查bboxObject
        if (bboxObject != null && bboxObject.getSouth() != null) {
            return bboxObject.getSouth();
        }
        
        // 处理bboxRaw
        if (bboxRaw != null) {
            // 如果是二维数组格式
            if (bboxRaw instanceof List) {
                List<?> bboxList = (List<?>) bboxRaw;
                if (!bboxList.isEmpty() && bboxList.get(0) instanceof List) {
                    List<?> minPoint = (List<?>) bboxList.get(0);
                    if (minPoint.size() > 1 && minPoint.get(1) instanceof Number) {
                        return new BigDecimal(minPoint.get(1).toString());
                    }
                }
            }
            // 如果是Map格式（对象格式）
            else if (bboxRaw instanceof Map) {
                Map<?, ?> bboxMap = (Map<?, ?>) bboxRaw;
                Object value = bboxMap.get("south") != null ? bboxMap.get("south") : 
                              bboxMap.get("minLat") != null ? bboxMap.get("minLat") : 
                              bboxMap.get(1);
                if (value instanceof Number) {
                    return new BigDecimal(value.toString());
                }
            }
        }
        return null;
    }

    /**
     * 获取 bbox 的最大经度
     */
    public BigDecimal getBboxMaxLng() {
        // 首先检查bboxObject
        if (bboxObject != null && bboxObject.getEast() != null) {
            return bboxObject.getEast();
        }
        
        // 处理bboxRaw
        if (bboxRaw != null) {
            // 如果是二维数组格式
            if (bboxRaw instanceof List) {
                List<?> bboxList = (List<?>) bboxRaw;
                if (bboxList.size() > 1 && bboxList.get(1) instanceof List) {
                    List<?> maxPoint = (List<?>) bboxList.get(1);
                    if (!maxPoint.isEmpty() && maxPoint.get(0) instanceof Number) {
                        return new BigDecimal(maxPoint.get(0).toString());
                    }
                }
            }
            // 如果是Map格式（对象格式）
            else if (bboxRaw instanceof Map) {
                Map<?, ?> bboxMap = (Map<?, ?>) bboxRaw;
                Object value = bboxMap.get("east") != null ? bboxMap.get("east") : 
                              bboxMap.get("maxLng") != null ? bboxMap.get("maxLng") : 
                              bboxMap.get(2);
                if (value instanceof Number) {
                    return new BigDecimal(value.toString());
                }
            }
        }
        return null;
    }

    /**
     * 获取 bbox 的最大纬度
     */
    public BigDecimal getBboxMaxLat() {
        // 首先检查bboxObject
        if (bboxObject != null && bboxObject.getNorth() != null) {
            return bboxObject.getNorth();
        }
        
        // 处理bboxRaw
        if (bboxRaw != null) {
            // 如果是二维数组格式
            if (bboxRaw instanceof List) {
                List<?> bboxList = (List<?>) bboxRaw;
                if (bboxList.size() > 1 && bboxList.get(1) instanceof List) {
                    List<?> maxPoint = (List<?>) bboxList.get(1);
                    if (maxPoint.size() > 1 && maxPoint.get(1) instanceof Number) {
                        return new BigDecimal(maxPoint.get(1).toString());
                    }
                }
            }
            // 如果是Map格式（对象格式）
            else if (bboxRaw instanceof Map) {
                Map<?, ?> bboxMap = (Map<?, ?>) bboxRaw;
                Object value = bboxMap.get("north") != null ? bboxMap.get("north") : 
                              bboxMap.get("maxLat") != null ? bboxMap.get("maxLat") : 
                              bboxMap.get(3);
                if (value instanceof Number) {
                    return new BigDecimal(value.toString());
                }
            }
        }
        return null;
    }
}
