package com.bluesky.vo;

import com.bluesky.common.TemporalMeta;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class LandingPointVO extends TemporalMeta {
    private String landingPointId;
    private String regionId;
    private String name;
    private String code;
    private String type;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal altitude;
    private Boolean enabled;
}
