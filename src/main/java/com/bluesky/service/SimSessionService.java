package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.common.ResultCode;
import com.bluesky.entity.SimSession;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.SimSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SimSessionService {

    private static final Set<String> VALID_TRANSITIONS = Set.of(
            "INIT", "ROUTE_SELECTED", "CONNECTING", "CONNECTED", "STREAMING", "DISCONNECTED", "CLOSED"
    );

    private final SimSessionMapper mapper;
    private final RegionService regionService;

    public List<Map<String, Object>> listByRegion(String regionId) {
        regionService.assertRegionAccess(regionId);
        return mapper.selectList(new LambdaQueryWrapper<SimSession>()
                        .eq(SimSession::getRegionId, regionId)
                        .orderByDesc(SimSession::getUpdatedAt))
                .stream().map(this::toMap).toList();
    }

    public Map<String, Object> getById(String sessionId) {
        SimSession session = require(sessionId);
        regionService.assertRegionAccess(session.getRegionId());
        return toMap(session);
    }

    @Transactional
    public Map<String, Object> create(String regionId, String routeId) {
        regionService.assertRegionAccess(regionId);
        SimSession session = new SimSession();
        session.setSessionId("SIM" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        session.setRegionId(regionId);
        session.setRouteId(routeId);
        session.setStatus(routeId != null && !routeId.isBlank() ? "ROUTE_SELECTED" : "INIT");
        session.setLastSequence(0L);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        mapper.insert(session);
        return toMap(session);
    }

    @Transactional
    public Map<String, Object> updateStatus(String sessionId, String status) {
        if (!VALID_TRANSITIONS.contains(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "非法状态: " + status);
        }
        SimSession session = require(sessionId);
        regionService.assertRegionAccess(session.getRegionId());
        session.setStatus(status);
        session.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(session);
        return toMap(session);
    }

    @Transactional
    public void close(String sessionId) {
        updateStatus(sessionId, "CLOSED");
    }

    private SimSession require(String sessionId) {
        SimSession session = mapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "模拟会话不存在: " + sessionId);
        }
        return session;
    }

    private Map<String, Object> toMap(SimSession session) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sessionId", session.getSessionId());
        map.put("regionId", session.getRegionId());
        map.put("routeId", session.getRouteId());
        map.put("status", session.getStatus());
        map.put("lastSequence", session.getLastSequence());
        map.put("createdAt", session.getCreatedAt());
        map.put("updatedAt", session.getUpdatedAt());
        return map;
    }
}
