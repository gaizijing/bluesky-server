package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.common.ResultCode;
import com.bluesky.entity.WarningHandleRecord;
import com.bluesky.entity.WarningRecord;
import com.bluesky.enums.WarningStatus;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.WarningHandleRecordMapper;
import com.bluesky.mapper.WarningRecordMapper;
import com.bluesky.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WarningService {

    private final WarningRecordMapper warningRecordMapper;
    private final WarningHandleRecordMapper handleRecordMapper;
    private final RegionService regionService;

    public List<Map<String, Object>> list(String regionId, String types, String statuses) {
        regionService.assertRegionAccess(regionId);
        LambdaQueryWrapper<WarningRecord> wrapper = new LambdaQueryWrapper<WarningRecord>()
                .eq(WarningRecord::getDisplayRegionId, regionId)
                .orderByDesc(WarningRecord::getLastTriggeredAt);
        if (types != null && !types.isBlank()) {
            wrapper.in(WarningRecord::getWarningType, Arrays.asList(types.split(",")));
        }
        if (statuses != null && !statuses.isBlank()) {
            wrapper.in(WarningRecord::getStatus, Arrays.asList(statuses.split(",")));
        }
        return warningRecordMapper.selectList(wrapper).stream().map(this::toMap).toList();
    }

    public Map<String, Object> getById(String warningId) {
        WarningRecord record = require(warningId);
        regionService.assertRegionAccess(record.getDisplayRegionId());
        return toMap(record);
    }

    @Transactional
    public Map<String, Object> ack(String warningId, String remark) {
        return transition(warningId, WarningStatus.NEW, WarningStatus.ACKNOWLEDGED, "ACK", remark);
    }

    @Transactional
    public Map<String, Object> handle(String warningId, String remark) {
        WarningRecord record = require(warningId);
        regionService.assertRegionAccess(record.getDisplayRegionId());
        WarningStatus current = WarningStatus.parse(record.getStatus());
        if (current != WarningStatus.NEW && current != WarningStatus.ACKNOWLEDGED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前状态不可处理: " + record.getStatus());
        }
        record.setStatus(WarningStatus.HANDLED.name());
        record.setUpdatedAt(LocalDateTime.now());
        warningRecordMapper.updateById(record);
        writeHandleRecord(warningId, "HANDLE", remark);
        return toMap(record);
    }

    @Transactional
    public Map<String, Object> close(String warningId, String remark) {
        WarningRecord record = require(warningId);
        regionService.assertRegionAccess(record.getDisplayRegionId());
        if (WarningStatus.CLOSED.name().equals(record.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "预警已关闭");
        }
        record.setStatus(WarningStatus.CLOSED.name());
        record.setUpdatedAt(LocalDateTime.now());
        warningRecordMapper.updateById(record);
        writeHandleRecord(warningId, "CLOSE", remark);
        return toMap(record);
    }

    private Map<String, Object> transition(String warningId, WarningStatus from, WarningStatus to,
                                           String action, String remark) {
        WarningRecord record = require(warningId);
        regionService.assertRegionAccess(record.getDisplayRegionId());
        if (!from.name().equals(record.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "仅 " + from.name() + " 状态可执行 " + action + "，当前: " + record.getStatus());
        }
        record.setStatus(to.name());
        record.setUpdatedAt(LocalDateTime.now());
        warningRecordMapper.updateById(record);
        writeHandleRecord(warningId, action, remark);
        return toMap(record);
    }

    private void writeHandleRecord(String warningId, String action, String remark) {
        WarningHandleRecord handle = new WarningHandleRecord();
        handle.setWarningId(warningId);
        handle.setAction(action);
        handle.setRemark(remark);
        handle.setOperatorId(SecurityUtils.currentUser() != null ? SecurityUtils.currentUser().getUserId() : null);
        handle.setCreatedAt(LocalDateTime.now());
        handleRecordMapper.insert(handle);
    }

    private WarningRecord require(String warningId) {
        WarningRecord record = warningRecordMapper.selectById(warningId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预警不存在: " + warningId);
        }
        return record;
    }

    private Map<String, Object> toMap(WarningRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("warningId", record.getWarningId());
        map.put("warningType", record.getWarningType());
        map.put("displayRegionId", record.getDisplayRegionId());
        map.put("targetType", record.getTargetType());
        map.put("targetId", record.getTargetId());
        map.put("level", record.getLevel());
        map.put("title", record.getTitle());
        map.put("content", record.getContent());
        map.put("status", record.getStatus());
        map.put("bucketTime", record.getBucketTime());
        map.put("ruleVersion", record.getRuleVersion());
        map.put("occurrenceCount", record.getOccurrenceCount());
        map.put("lastTriggeredAt", record.getLastTriggeredAt());
        return map;
    }
}
