package com.bluesky.vo;

import lombok.Data;

@Data
public class RegionMapLiftVO {
    private Double longitude;
    private Double latitude;
    private Double height;
    private Double pitch;
    private Double heading;
    private Double terrainExaggeration;
}
