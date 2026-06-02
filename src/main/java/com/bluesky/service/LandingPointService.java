package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.common.TemporalMeta;
import com.bluesky.dto.LandingPointRequest;
import com.bluesky.entity.LandingPoint;
import com.bluesky.exception.BusinessException;
import com.bluesky.common.ResultCode;
import com.bluesky.mapper.LandingPointMapper;
import com.bluesky.util.TimeBucketUtil;
import com.bluesky.vo.LandingPointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LandingPointService {

    private final LandingPointMapper landingPointMapper;
    private final RegionService regionService;

    public List<LandingPointVO> listByRegion(String regionId) {
        regionService.assertRegionAccess(regionId);
        List<LandingPoint> points = landingPointMapper.selectList(new LambdaQueryWrapper<LandingPoint>()
                .eq(LandingPoint::getRegionId, regionId)
                .eq(LandingPoint::getDeleted, 0)
                .orderByAsc(LandingPoint::getName));
        return points.stream().map(this::toSimpleVO).collect(Collectors.toList());
    }

    public LandingPointVO getById(String id, String time) {
        LandingPoint point = requirePoint(id);
        regionService.assertRegionAccess(point.getRegionId());
        OffsetDateTime requested = TimeBucketUtil.parseOrNow(time);
        TemporalMeta meta = TimeBucketUtil.buildMeta(requested, TimeBucketUtil.now(), false);
        LandingPointVO vo = toSimpleVO(point);
        copyMeta(vo, meta);
        return vo;
    }

    public List<LandingPoint> listAllEntities() {
        return landingPointMapper.selectList(new LambdaQueryWrapper<LandingPoint>()
                .eq(LandingPoint::getDeleted, 0));
    }

    public LandingPoint getEntity(String id) {
        return requirePoint(id);
    }

    @Transactional
    public LandingPointVO create(LandingPointRequest request) {
        regionService.assertRegionAccess(request.getRegionId());
        assertCodeUnique(request.getRegionId(), request.getCode(), null);
        LandingPoint point = new LandingPoint();
        point.setLandingPointId("LP" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        applyRequest(point, request);
        point.setCreatedAt(LocalDateTime.now());
        point.setUpdatedAt(LocalDateTime.now());
        landingPointMapper.insert(point);
        return toSimpleVO(point);
    }

    @Transactional
    public LandingPointVO update(String id, LandingPointRequest request) {
        LandingPoint point = requirePoint(id);
        regionService.assertRegionAccess(point.getRegionId());
        assertCodeUnique(point.getRegionId(), request.getCode(), id);
        applyRequest(point, request);
        point.setRegionId(point.getRegionId());
        point.setUpdatedAt(LocalDateTime.now());
        landingPointMapper.updateById(point);
        return toSimpleVO(point);
    }

    @Transactional
    public void delete(String id) {
        LandingPoint point = requirePoint(id);
        regionService.assertRegionAccess(point.getRegionId());
        landingPointMapper.deleteById(id);
    }

    private LandingPoint requirePoint(String id) {
        LandingPoint point = landingPointMapper.selectById(id);
        if (point == null || Integer.valueOf(1).equals(point.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "起降点不存在: " + id);
        }
        return point;
    }

    private void applyRequest(LandingPoint point, LandingPointRequest request) {
        point.setRegionId(request.getRegionId());
        point.setName(request.getName());
        point.setCode(request.getCode());
        point.setType(request.getType());
        point.setAddress(request.getAddress());
        point.setLongitude(request.getLongitude());
        point.setLatitude(request.getLatitude());
        point.setAltitude(request.getAltitude());
        point.setBboxMinLng(request.getBboxMinLng());
        point.setBboxMinLat(request.getBboxMinLat());
        point.setBboxMaxLng(request.getBboxMaxLng());
        point.setBboxMaxLat(request.getBboxMaxLat());
        point.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
    }

    private LandingPointVO toSimpleVO(LandingPoint point) {
        LandingPointVO vo = new LandingPointVO();
        vo.setLandingPointId(point.getLandingPointId());
        vo.setRegionId(point.getRegionId());
        vo.setName(point.getName());
        vo.setCode(point.getCode());
        vo.setType(point.getType());
        vo.setAddress(point.getAddress());
        vo.setLongitude(point.getLongitude());
        vo.setLatitude(point.getLatitude());
        vo.setAltitude(point.getAltitude());
        vo.setEnabled(point.getEnabled());
        return vo;
    }

    private void copyMeta(LandingPointVO vo, TemporalMeta meta) {
        vo.setRequestedTime(meta.getRequestedTime());
        vo.setBucketTime(meta.getBucketTime());
        vo.setComputedAt(meta.getComputedAt());
        vo.setIsStale(meta.getIsStale());
    }

    private void assertCodeUnique(String regionId, String code, String excludeId) {
        if (code == null || code.isBlank()) {
            return;
        }
        LambdaQueryWrapper<LandingPoint> wrapper = new LambdaQueryWrapper<LandingPoint>()
                .eq(LandingPoint::getRegionId, regionId)
                .eq(LandingPoint::getCode, code.trim())
                .eq(LandingPoint::getDeleted, 0);
        if (excludeId != null) {
            wrapper.ne(LandingPoint::getLandingPointId, excludeId);
        }
        if (landingPointMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "同一 Region 内编码已存在: " + code.trim());
        }
    }
}
