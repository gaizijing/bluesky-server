package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.RiskZone;
import com.bluesky.mapper.RiskZoneMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RiskZoneService {

    private final RiskZoneMapper riskZoneMapper;

    public Map<String, Object> listAll() {
        LambdaQueryWrapper<RiskZone> q = new LambdaQueryWrapper<>();
        q.orderByAsc(RiskZone::getSortOrder).orderByAsc(RiskZone::getId);
        List<RiskZone> list = riskZoneMapper.selectList(q);
        List<Map<String, Object>> rows = list.stream().map(this::toDto).collect(Collectors.toList());
        Map<String, Object> out = new HashMap<>();
        out.put("zones", rows);
        out.put("total", rows.size());
        return out;
    }

    private Map<String, Object> toDto(RiskZone z) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", z.getId());
        m.put("zoneType", z.getZoneType());
        m.put("label", z.getLabel());
        m.put("centerLng", z.getCenterLng());
        m.put("centerLat", z.getCenterLat());
        m.put("radiusM", z.getRadiusM());
        m.put("heightM", z.getHeightM());
        m.put("sortOrder", z.getSortOrder());
        return m;
    }
}
