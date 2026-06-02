package com.bluesky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class NoFlyZoneRequest {

    @NotBlank
    private String regionId;
    @NotBlank
    private String name;
    private String zoneType = "PERMANENT";
    @NotNull
    private Map<String, Object> geometry;
    private Boolean enabled = true;
}
