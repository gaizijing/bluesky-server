package com.bluesky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LandingPointRequest {

    @NotBlank
    private String regionId;

    @NotBlank
    private String name;

    private String code;
    private String type;
    private String address;

    @NotNull
    private BigDecimal longitude;

    @NotNull
    private BigDecimal latitude;

    private BigDecimal altitude;
    private BigDecimal bboxMinLng;
    private BigDecimal bboxMinLat;
    private BigDecimal bboxMaxLng;
    private BigDecimal bboxMaxLat;
    private Boolean enabled;
}
