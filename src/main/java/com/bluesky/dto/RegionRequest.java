package com.bluesky.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegionRequest {

    @NotBlank
    private String name;

    /** 相机中心点经度 */
    private Double centerLng;
    /** 相机中心点纬度 */
    private Double centerLat;

    /** 行政区划代码，用于从 DataV 自动下载边界 GeoJSON */
    private String adcode;
    /** 自定义 GeoJSON 下载地址（优先级高于 adcode） */
    private String boundarySourceUrl;

    private String modelUrl;
    private Boolean enabled;
    private Boolean isDefault;
    private RegionMapLiftRequest mapLift;

    @Data
    public static class RegionMapLiftRequest {
        private Double longitude;
        private Double latitude;
        private Double height;
        private Double pitch;
        private Double heading;
        private Double terrainExaggeration;
    }
}
