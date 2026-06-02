package com.bluesky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegionRequest {

    @NotBlank
    private String name;

    @NotNull
    private Double west;
    @NotNull
    private Double east;
    @NotNull
    private Double south;
    @NotNull
    private Double north;

    private Double centerLng;
    private Double centerLat;
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
