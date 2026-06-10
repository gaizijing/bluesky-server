package com.bluesky.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RegionVO {
    private String regionId;
    private String name;
    private Boolean enabled;
    private Boolean isDefault;
    private String modelUrl;
    private RegionMapLiftVO mapLift;
    private Double centerLng;
    private Double centerLat;
    private String boundaryUrl;
    private String adcode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
