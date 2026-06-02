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

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegionService {

    private final RegionMapper regionMapper;
    private final ObjectMapper objectMapper;

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

    @Transactional
    public RegionVO create(RegionRequest request) {
        Region region = new Region();
        region.setRegionId(generateRegionId());
        applyRequest(region, request);
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
        applyRequest(region, request);
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

    private void applyRequest(Region region, RegionRequest request) {
        region.setName(request.getName());
        region.setWest(request.getWest());
        region.setEast(request.getEast());
        region.setSouth(request.getSouth());
        region.setNorth(request.getNorth());
        double west = request.getWest() != null ? request.getWest() : region.getWest();
        double east = request.getEast() != null ? request.getEast() : region.getEast();
        double south = request.getSouth() != null ? request.getSouth() : region.getSouth();
        double north = request.getNorth() != null ? request.getNorth() : region.getNorth();
        region.setCenterLng(request.getCenterLng() != null ? request.getCenterLng() : (west + east) / 2);
        region.setCenterLat(request.getCenterLat() != null ? request.getCenterLat() : (south + north) / 2);
        region.setModelUrl(request.getModelUrl());
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
        }
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
        vo.setWest(region.getWest());
        vo.setEast(region.getEast());
        vo.setSouth(region.getSouth());
        vo.setNorth(region.getNorth());
        vo.setCenterLng(region.getCenterLng());
        vo.setCenterLat(region.getCenterLat());
        vo.setCreatedAt(region.getCreatedAt());
        vo.setUpdatedAt(region.getUpdatedAt());
        if (region.getMapLiftJson() != null) {
            try {
                vo.setMapLift(objectMapper.readValue(region.getMapLiftJson(), RegionMapLiftVO.class));
            } catch (JsonProcessingException ignored) {
                RegionMapLiftVO lift = new RegionMapLiftVO();
                lift.setLongitude(region.getCenterLng());
                lift.setLatitude(region.getCenterLat());
                lift.setHeight(120000d);
                lift.setPitch(-45d);
                vo.setMapLift(lift);
            }
        }
        return vo;
    }
}
