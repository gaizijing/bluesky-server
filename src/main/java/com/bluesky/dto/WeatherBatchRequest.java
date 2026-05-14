package com.bluesky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量按经纬度查询天气（航迹剖面等）。
 */
@Data
@Schema(description = "批量坐标天气请求")
public class WeatherBatchRequest {

    @NotEmpty(message = "coordinates 不能为空")
    @Size(max = 400, message = "单次最多 400 个坐标点")
    @Valid
    @Schema(description = "坐标列表，顺序与航迹采样一致")
    private List<Coordinate> coordinates;

    @Data
    @Schema(description = "单个经纬度点")
    public static class Coordinate {
        @Schema(description = "经度", example = "120.3835", requiredMode = Schema.RequiredMode.REQUIRED)
        private Double lng;
        @Schema(description = "纬度", example = "36.0625", requiredMode = Schema.RequiredMode.REQUIRED)
        private Double lat;
    }
}
