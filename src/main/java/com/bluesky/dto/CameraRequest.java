package com.bluesky.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 摄像头管理请求 DTO
 */
@Data
public class CameraRequest {

    @Size(max = 50, message = "摄像头ID长度不能超过50个字符")
    private String id;

    @Size(max = 100, message = "摄像头名称长度不能超过100个字符")
    private String name;

    @Size(max = 200, message = "部署位置长度不能超过200个字符")
    private String location;

    @NotBlank(message = "关联监测点不能为空")
    @Size(max = 50, message = "点位ID长度不能超过50个字符")
    private String pointId;

    @DecimalMin(value = "-180.0", message = "经度不能小于-180")
    @DecimalMax(value = "180.0", message = "经度不能大于180")
    private BigDecimal longitude;

    @DecimalMin(value = "-90.0", message = "纬度不能小于-90")
    @DecimalMax(value = "90.0", message = "纬度不能大于90")
    private BigDecimal latitude;

    @Pattern(regexp = "(?i)^(online|offline)?$", message = "摄像头状态只能是 online 或 offline")
    private String status;

    @Size(max = 20, message = "分辨率长度不能超过20个字符")
    private String resolution;

    @Size(max = 500, message = "预览地址长度不能超过500个字符")
    private String previewUrl;

    @Size(max = 500, message = "流地址长度不能超过500个字符")
    private String streamUrl;

    @PositiveOrZero(message = "最后心跳时间不能小于0")
    private Long lastHeartbeat;

    private Boolean isActive;
}
