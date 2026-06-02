package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.common.ResultCode;
import com.bluesky.dto.NoFlyZoneRequest;
import com.bluesky.entity.NoFlyZone;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.NoFlyZoneMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NoFlyZoneService {

    private final NoFlyZoneMapper mapper;
    private final RegionService regionService;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> listByRegion(String regionId) {
        regionService.assertRegionAccess(regionId);
        return mapper.selectList(new LambdaQueryWrapper<NoFlyZone>()
                        .eq(NoFlyZone::getRegionId, regionId)
                        .eq(NoFlyZone::getDeleted, 0)
                        .orderByAsc(NoFlyZone::getName))
                .stream().map(this::toMap).toList();
    }

    @Transactional
    public Map<String, Object> create(NoFlyZoneRequest request) {
        regionService.assertRegionAccess(request.getRegionId());
        NoFlyZone zone = new NoFlyZone();
        zone.setZoneId("NFZ" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        applyRequest(zone, request);
        zone.setCreatedAt(LocalDateTime.now());
        zone.setUpdatedAt(LocalDateTime.now());
        mapper.insert(zone);
        return toMap(zone);
    }

    @Transactional
    public Map<String, Object> update(String zoneId, NoFlyZoneRequest request) {
        NoFlyZone zone = require(zoneId);
        regionService.assertRegionAccess(zone.getRegionId());
        applyRequest(zone, request);
        zone.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(zone);
        return toMap(zone);
    }

    @Transactional
    public void delete(String zoneId) {
        NoFlyZone zone = require(zoneId);
        regionService.assertRegionAccess(zone.getRegionId());
        mapper.deleteById(zoneId);
    }

    @Transactional
    public List<Map<String, Object>> importGeoJson(String regionId, Map<String, Object> geoJson) {
        regionService.assertRegionAccess(regionId);
        List<Map<String, Object>> created = new ArrayList<>();
        Object features = geoJson.get("features");
        if (!(features instanceof List<?> list)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "GeoJSON 需为 FeatureCollection");
        }
        int index = 1;
        for (Object raw : list) {
            if (!(raw instanceof Map<?, ?> feature)) {
                continue;
            }
            NoFlyZoneRequest req = new NoFlyZoneRequest();
            req.setRegionId(regionId);
            Object props = feature.get("properties");
            String name = "导入禁飞区-" + index;
            if (props instanceof Map<?, ?> propMap && propMap.get("name") != null) {
                name = String.valueOf(propMap.get("name"));
            }
            req.setName(name);
            req.setGeometry(Map.of(
                    "type", feature.get("geometry") instanceof Map<?, ?> g ? g.get("type") : "Polygon",
                    "coordinates", feature.get("geometry") instanceof Map<?, ?> g ? g.get("coordinates") : List.of()
            ));
            created.add(create(req));
            index++;
        }
        return created;
    }

    private NoFlyZone require(String zoneId) {
        NoFlyZone zone = mapper.selectById(zoneId);
        if (zone == null || Integer.valueOf(1).equals(zone.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "禁飞区不存在: " + zoneId);
        }
        return zone;
    }

    private void applyRequest(NoFlyZone zone, NoFlyZoneRequest request) {
        zone.setRegionId(request.getRegionId());
        zone.setName(request.getName());
        zone.setZoneType(request.getZoneType() != null ? request.getZoneType() : "PERMANENT");
        zone.setEnabled(request.getEnabled() == null || request.getEnabled());
        try {
            zone.setGeometryJson(objectMapper.writeValueAsString(request.getGeometry()));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "geometry 格式错误");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(NoFlyZone zone) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("zoneId", zone.getZoneId());
        map.put("regionId", zone.getRegionId());
        map.put("name", zone.getName());
        map.put("zoneType", zone.getZoneType());
        map.put("enabled", zone.getEnabled());
        try {
            map.put("geometry", objectMapper.readValue(zone.getGeometryJson(), Map.class));
        } catch (JsonProcessingException e) {
            map.put("geometry", zone.getGeometryJson());
        }
        return map;
    }
}
