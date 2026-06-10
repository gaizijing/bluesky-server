package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluesky.dto.RegionRequest;
import com.bluesky.entity.Region;
import com.bluesky.exception.BusinessException;
import com.bluesky.common.ResultCode;
import com.bluesky.mapper.RegionMapper;
import com.bluesky.security.LoginUser;
import com.bluesky.security.SecurityUtils;
import com.bluesky.vo.RegionMapLiftVO;
import com.bluesky.vo.RegionVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegionService {

    private final RegionMapper regionMapper;
    private final ObjectMapper objectMapper;
    private final RegionBoundaryService regionBoundaryService;

    public List<RegionVO> listForCurrentUser() {
        LoginUser user = SecurityUtils.requireUser();
        LambdaQueryWrapper<Region> wrapper = new LambdaQueryWrapper<Region>()
                .eq(Region::getDeleted, 0)
                .eq(Region::getEnabled, true)
                .orderByDesc(Region::getIsDefault);
        if (!user.getRole().isSuperAdmin()) {
            if (user.getRegionIds() == null || user.getRegionIds().isEmpty()) {
                return List.of();
            }
            wrapper.in(Region::getRegionId, user.getRegionIds());
        }
        return regionMapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    public RegionVO getById(String regionId) {
        Region region = requireRegion(regionId);
        assertRegionAccess(regionId);
        return toVO(region);
    }

    public RegionVO getDefault() {
        Region region = regionMapper.selectDefault();
        if (region == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "默认 Region 不存在");
        }
        assertRegionAccess(region.getRegionId());
        return toVO(region);
    }

    public Region getEntity(String regionId) {
        return requireRegion(regionId);
    }

    /** P2 调度：仅处理已启用且未删除的 Region */
    public List<Region> listEnabled() {
        return regionMapper.selectList(new LambdaQueryWrapper<Region>()
                .eq(Region::getDeleted, 0)
                .eq(Region::getEnabled, true));
    }

    @Transactional
    public RegionVO create(RegionRequest request) {
        if (!hasBoundarySource(request)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请提供行政区划 adcode 或 GeoJSON 下载地址");
        }

        Region region = new Region();
        region.setRegionId(generateRegionId());
        applyBasicFields(region, request, true);

        RegionBoundaryService.BoundaryImportResult boundary =
                regionBoundaryService.importBoundary(
                        region.getRegionId(),
                        request.getAdcode(),
                        request.getBoundarySourceUrl());
        applyBoundaryResult(region, boundary);

        region.setCreatedAt(LocalDateTime.now());
        region.setUpdatedAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            regionMapper.clearOtherDefaults(region.getRegionId());
        }
        regionMapper.insert(region);
        return toVO(region);
    }

    @Transactional
    public RegionVO update(String regionId, RegionRequest request) {
        Region region = requireRegion(regionId);
        applyBasicFields(region, request, false);

        if (hasBoundarySource(request)) {
            RegionBoundaryService.BoundaryImportResult boundary =
                    regionBoundaryService.importBoundary(
                            regionId,
                            request.getAdcode(),
                            request.getBoundarySourceUrl());
            applyBoundaryResult(region, boundary);
        }

        region.setUpdatedAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            regionMapper.setDefaultRegion(regionId);
            region.setIsDefault(true);
            regionMapper.updateById(region);
            return toVO(requireRegion(regionId));
        }
        if (request.getIsDefault() != null) {
            region.setIsDefault(request.getIsDefault());
        }
        regionMapper.updateById(region);
        return toVO(region);
    }

    @Transactional
    public RegionVO setAsDefault(String regionId) {
        requireRegion(regionId);
        assertRegionAccess(regionId);
        regionMapper.setDefaultRegion(regionId);
        return toVO(requireRegion(regionId));
    }

    @Transactional
    public void delete(String regionId) {
        Region region = requireRegion(regionId);
        if (Boolean.TRUE.equals(region.getIsDefault())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能删除默认 Region");
        }
        regionMapper.deleteById(regionId);
        regionBoundaryService.deleteBoundaryFile(regionId);
    }

    public void assertRegionAccess(String regionId) {
        LoginUser user = SecurityUtils.currentUser();
        if (user == null || user.getRole().isSuperAdmin()) {
            return;
        }
        if (user.getRegionIds() == null || !user.getRegionIds().contains(regionId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问 Region: " + regionId);
        }
    }

    public Page<Region> pageAll(int page, int size) {
        return regionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Region>().eq(Region::getDeleted, 0));
    }

    private Region requireRegion(String regionId) {
        Region region = regionMapper.selectById(regionId);
        if (region == null || Integer.valueOf(1).equals(region.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Region 不存在: " + regionId);
        }
        return region;
    }

    private boolean hasBoundarySource(RegionRequest request) {
        return StringUtils.hasText(request.getAdcode())
                || StringUtils.hasText(request.getBoundarySourceUrl());
    }

    private void applyBasicFields(Region region, RegionRequest request, boolean isCreate) {
        region.setName(request.getName());
        if (request.getCenterLng() != null) {
            region.setCenterLng(request.getCenterLng());
        } else if (isCreate) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请提供中心点经度 centerLng");
        }
        if (request.getCenterLat() != null) {
            region.setCenterLat(request.getCenterLat());
        } else if (isCreate) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请提供中心点纬度 centerLat");
        }
        region.setModelUrl(StringUtils.hasText(request.getModelUrl()) ? request.getModelUrl().trim() : null);
        if (request.getEnabled() != null) {
            region.setEnabled(request.getEnabled());
        } else if (region.getEnabled() == null) {
            region.setEnabled(true);
        }
        if (request.getIsDefault() != null) {
            region.setIsDefault(request.getIsDefault());
        }
        if (request.getMapLift() != null) {
            try {
                region.setMapLiftJson(objectMapper.writeValueAsString(request.getMapLift()));
            } catch (JsonProcessingException e) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "mapLift 格式错误");
            }
        } else if (isCreate) {
            syncMapLiftFromCenter(region);
        } else if (request.getCenterLng() != null || request.getCenterLat() != null) {
            syncMapLiftCenterFromRegion(region);
        }
    }

    private void syncMapLiftFromCenter(Region region) {
        try {
            RegionMapLiftVO lift = new RegionMapLiftVO();
            lift.setLongitude(region.getCenterLng());
            lift.setLatitude(region.getCenterLat());
            lift.setHeight(52000d);
            lift.setPitch(-40d);
            lift.setHeading(0d);
            lift.setTerrainExaggeration(1.2d);
            region.setMapLiftJson(objectMapper.writeValueAsString(lift));
        } catch (JsonProcessingException ignored) {
            // ignore
        }
    }

    /** 更新中心点时同步 mapLift 经纬度，保留原有高度/俯仰等视角参数 */
    private void syncMapLiftCenterFromRegion(Region region) {
        if (region.getCenterLng() == null || region.getCenterLat() == null) {
            return;
        }
        try {
            RegionMapLiftVO lift = null;
            if (region.getMapLiftJson() != null) {
                lift = objectMapper.readValue(region.getMapLiftJson(), RegionMapLiftVO.class);
            }
            if (lift == null) {
                syncMapLiftFromCenter(region);
                return;
            }
            lift.setLongitude(region.getCenterLng());
            lift.setLatitude(region.getCenterLat());
            region.setMapLiftJson(objectMapper.writeValueAsString(lift));
        } catch (JsonProcessingException ignored) {
            syncMapLiftFromCenter(region);
        }
    }

    private void applyBoundaryResult(Region region, RegionBoundaryService.BoundaryImportResult boundary) {
        region.setBoundaryUrl(boundary.boundaryUrl());
        region.setAdcode(boundary.adcode());
    }

    private String generateRegionId() {
        long count = regionMapper.selectCount(null) + 1;
        return "R" + count;
    }

    private RegionVO toVO(Region region) {
        RegionVO vo = new RegionVO();
        vo.setRegionId(region.getRegionId());
        vo.setName(region.getName());
        vo.setEnabled(region.getEnabled());
        vo.setIsDefault(region.getIsDefault());
        vo.setModelUrl(region.getModelUrl());
        vo.setCenterLng(region.getCenterLng());
        vo.setCenterLat(region.getCenterLat());
        vo.setBoundaryUrl(region.getBoundaryUrl());
        vo.setAdcode(region.getAdcode());
        vo.setCreatedAt(region.getCreatedAt());
        vo.setUpdatedAt(region.getUpdatedAt());
        if (region.getMapLiftJson() != null) {
            try {
                vo.setMapLift(objectMapper.readValue(region.getMapLiftJson(), RegionMapLiftVO.class));
            } catch (JsonProcessingException ignored) {
                RegionMapLiftVO lift = new RegionMapLiftVO();
                lift.setLongitude(region.getCenterLng());
                lift.setLatitude(region.getCenterLat());
                lift.setHeight(52000d);
                lift.setPitch(-40d);
                vo.setMapLift(lift);
            }
        }
        return vo;
    }
}
