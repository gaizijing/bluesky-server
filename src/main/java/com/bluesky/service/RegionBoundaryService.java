package com.bluesky.service;

import com.bluesky.config.RegionBoundaryConfig;
import com.bluesky.entity.Region;
import com.bluesky.exception.BusinessException;
import com.bluesky.common.ResultCode;
import com.bluesky.util.GeoJsonEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class RegionBoundaryService {

    private final RegionBoundaryConfig config;
    private final ObjectMapper objectMapper;

    public record BoundaryImportResult(String boundaryUrl, String adcode) {}

    public BoundaryImportResult importBoundary(String regionId, String adcode, String boundarySourceUrl) {
        String normalizedAdcode = normalizeAdcode(adcode);
        String downloadUrl = resolveDownloadUrl(normalizedAdcode, boundarySourceUrl);
        String geoJson = downloadGeoJson(downloadUrl);
        validateGeoJson(geoJson);

        Path storageDir = resolveStorageDir();
        try {
            Files.createDirectories(storageDir);
            Path target = storageDir.resolve(regionId + ".geojson");
            Files.writeString(target, geoJson);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "保存 GeoJSON 失败: " + e.getMessage());
        }

        String publicUrl = normalizePublicPrefix() + "/" + regionId + ".geojson";
        return new BoundaryImportResult(publicUrl, normalizedAdcode);
    }

    /** 从 Region 已存储的 GeoJSON 文件解析包络（内部算法用，非独立边界配置） */
    public GeoJsonEnvelope.Envelope resolveEnvelope(Region region) {
        if (region == null || !StringUtils.hasText(region.getBoundaryUrl())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Region 未配置边界 GeoJSON");
        }
        return readEnvelopeFromFile(resolveBoundaryFile(region.getBoundaryUrl()));
    }

    public void deleteBoundaryFile(String regionId) {
        if (!StringUtils.hasText(regionId)) {
            return;
        }
        try {
            Path target = resolveStorageDir().resolve(regionId + ".geojson");
            Files.deleteIfExists(target);
        } catch (Exception ignored) {
            // 非关键路径
        }
    }

    private GeoJsonEnvelope.Envelope readEnvelopeFromFile(Path file) {
        try {
            if (!Files.exists(file)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "边界 GeoJSON 文件不存在: " + file);
            }
            String json = Files.readString(file);
            return GeoJsonEnvelope.parse(json, objectMapper);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "读取边界 GeoJSON 失败: " + e.getMessage());
        }
    }

    private Path resolveBoundaryFile(String boundaryUrl) {
        String filename = boundaryUrl.substring(boundaryUrl.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的 boundaryUrl: " + boundaryUrl);
        }
        return resolveStorageDir().resolve(filename);
    }

    private String resolveDownloadUrl(String adcode, String boundarySourceUrl) {
        if (StringUtils.hasText(boundarySourceUrl)) {
            return boundarySourceUrl.trim();
        }
        if (StringUtils.hasText(adcode)) {
            return config.getDatavTemplate().replace("{adcode}", adcode);
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "请提供行政区划 adcode 或 GeoJSON 下载地址");
    }

    private String downloadGeoJson(String url) {
        try {
            RestTemplate restTemplate = createRestTemplate();
            String body = restTemplate.getForObject(url, String.class);
            if (!StringUtils.hasText(body)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "GeoJSON 下载内容为空: " + url);
            }
            return body;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "GeoJSON 下载失败: " + e.getMessage());
        }
    }

    private void validateGeoJson(String geoJson) {
        try {
            JsonNode root = objectMapper.readTree(geoJson);
            String type = root.path("type").asText("");
            if (!StringUtils.hasText(type)) {
                throw new IllegalArgumentException("缺少 type 字段");
            }
            GeoJsonEnvelope.parse(root);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "GeoJSON 格式无效: " + e.getMessage());
        }
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    private Path resolveStorageDir() {
        Path path = Paths.get(config.getStorageDir()).normalize();
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
        }
        return path;
    }

    private String normalizePublicPrefix() {
        String prefix = config.getPublicUrlPrefix();
        if (!StringUtils.hasText(prefix)) {
            return "/cesium/shp";
        }
        return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }

    private String normalizeAdcode(String adcode) {
        if (!StringUtils.hasText(adcode)) {
            return null;
        }
        return adcode.trim();
    }
}
