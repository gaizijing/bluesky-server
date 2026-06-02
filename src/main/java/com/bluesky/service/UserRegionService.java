package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.Region;
import com.bluesky.enums.UserRole;
import com.bluesky.mapper.RegionMapper;
import com.bluesky.mapper.UserRegionRelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserRegionService {

    private final UserRegionRelMapper userRegionRelMapper;
    private final RegionMapper regionMapper;

    public List<String> listRegionIdsByUserId(String userId, UserRole role) {
        if (role == UserRole.SUPER_ADMIN) {
            return regionMapper.selectList(new LambdaQueryWrapper<Region>()
                            .eq(Region::getEnabled, true)
                            .eq(Region::getDeleted, 0))
                    .stream()
                    .map(Region::getRegionId)
                    .collect(Collectors.toList());
        }
        return userRegionRelMapper.selectList(new LambdaQueryWrapper<com.bluesky.entity.UserRegionRel>()
                        .eq(com.bluesky.entity.UserRegionRel::getUserId, userId))
                .stream()
                .map(com.bluesky.entity.UserRegionRel::getRegionId)
                .collect(Collectors.toList());
    }
}
