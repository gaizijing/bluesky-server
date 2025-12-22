package com.lantian.lam.service.impl;

import com.lantian.lam.mapper.TFocusRegionMapper;
import com.lantian.lam.model.entity.TFocusRegion;
import com.lantian.lam.service.ITFocusRegionService;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TFocusRegionServiceImpl implements ITFocusRegionService {
    @Mapper
    private TFocusRegionMapper tFocusRegionMapper;
    @Override
    public List<TFocusRegion> getAll() {
        return tFocusRegionMapper.selectList(null);

    }
}
