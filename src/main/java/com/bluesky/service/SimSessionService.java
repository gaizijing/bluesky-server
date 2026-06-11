package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.common.ResultCode;
import com.bluesky.entity.SimSession;
import com.bluesky.exception.BusinessException;
import com.bluesky.isim.config.IsimConfig;
import com.bluesky.isim.service.IsimUdpService;
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
    private final IsimUdpService isimUdpService;
    private final IsimConfig isimConfig;

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
    public Map<String, Object> bindRoute(String sessionId, String routeId) {
        SimSession session = require(sessionId);
        regionService.assertRegionAccess(session.getRegionId());
        session.setRouteId(routeId);
        session.setStatus(routeId != null && !routeId.isBlank() ? "ROUTE_SELECTED" : session.getStatus());
        session.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(session);
        return toMap(session);
    }

    @Transactional
    public Map<String, Object> connect(String sessionId, Map<String, Object> targetRequest) {
        SimSession session = require(sessionId);
        regionService.assertRegionAccess(session.getRegionId());

        String host = targetRequest.get("host") != null ? String.valueOf(targetRequest.get("host")).trim() : null;
        Integer sendPort = toInteger(targetRequest.get("sendPort"));
        Integer receivePort = toInteger(targetRequest.get("receivePort"));
        Double longitude = toDouble(targetRequest.get("longitude"));
        Double latitude = toDouble(targetRequest.get("latitude"));
        Double altitude = toDouble(targetRequest.get("altitude"));

        if (host == null || host.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "主机地址不能为空");
        }
        if (!host.matches("^([0-9]{1,3}\\.){3}[0-9]{1,3}$") && !host.equals("localhost")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "主机地址格式无效");
        }
        if (sendPort != null && (sendPort < 1 || sendPort > 65535)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "发送端口必须在1-65535范围内");
        }
        if (receivePort != null && (receivePort < 1 || receivePort > 65535)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "接收端口必须在1-65535范围内");
        }
        if (longitude != null && (longitude < -180 || longitude > 180)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "经度必须在-180~180范围内");
        }
        if (latitude != null && (latitude < -90 || latitude > 90)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "纬度必须在-90~90范围内");
        }
        if (altitude != null && altitude < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "高度不能为负数");
        }

        session.setStatus("CONNECTING");
        session.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(session);

        isimConfig.setHost(host);
        if (sendPort != null) {
            isimConfig.setSendPort(sendPort);
        }
        if (receivePort != null) {
            isimConfig.setReceivePort(receivePort);
        }

        isimUdpService.start();

        boolean relocated = false;
        if (longitude != null && latitude != null) {
            double alt = altitude != null ? altitude : 1000.0;
            isimUdpService.sendRelocateCommand(longitude, latitude, alt);
            isimUdpService.pushInitialPositionToFrontend(longitude, latitude, alt);
            relocated = true;
        }

        session.setStatus("CONNECTED");
        session.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(session);

        Map<String, Object> result = new LinkedHashMap<>(toMap(session));
        result.put("success", true);
        result.put("relocated", relocated);
        result.put("message", relocated
                ? "模拟会话已连接，并已发送重定位指令"
                : "模拟会话已连接");
        result.put("runtime", Map.of(
                "connected", isimUdpService.isConnected(),
                "sendingWindData", isimUdpService.isSendingWindData()
        ));
        return result;
    }

    @Transactional
    public Map<String, Object> disconnect(String sessionId) {
        SimSession session = require(sessionId);
        regionService.assertRegionAccess(session.getRegionId());
        isimUdpService.stopUDP();
        session.setStatus("DISCONNECTED");
        session.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(session);
        Map<String, Object> result = new LinkedHashMap<>(toMap(session));
        result.put("success", true);
        result.put("message", "模拟会话已断开");
        return result;
    }

    @Transactional
    public Map<String, Object> control(String sessionId, String command) {
        SimSession session = require(sessionId);
        regionService.assertRegionAccess(session.getRegionId());
        if (command == null || command.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "缺少控制命令");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("command", command);
        result.put("sessionId", sessionId);

        switch (command.toUpperCase()) {
            case "START_SENDING" -> {
                isimUdpService.activate();
                session.setStatus("STREAMING");
                result.put("status", "started");
            }
            case "STOP_SENDING" -> {
                isimUdpService.deactivate();
                session.setStatus("CONNECTED");
                result.put("status", "stopped");
            }
            default -> throw new BusinessException(ResultCode.BAD_REQUEST, "未知命令: " + command);
        }

        session.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(session);
        result.putAll(toMap(session));
        return result;
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

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.doubleValue();
        return null;
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
