package com.bluesky.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "region.boundary")
public class RegionBoundaryConfig {

    /** GeoJSON 文件落盘目录（相对 server 工作目录或绝对路径） */
    private String storageDir = "../web/public/cesium/shp";

    /** 前端静态访问前缀 */
    private String publicUrlPrefix = "/cesium/shp";

    /** DataV 行政区划边界模板，{adcode} 占位 */
    private String datavTemplate = "https://geo.datav.aliyun.com/areas_v3/bound/{adcode}.json";
}
