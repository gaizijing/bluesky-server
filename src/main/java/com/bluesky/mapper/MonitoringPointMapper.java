package com.bluesky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluesky.entity.MonitoringPoint;
import org.apache.ibatis.annotations.Mapper;

/**
 * 重点关注区域Mapper
 *
 * @author BlueSky Team
 */
@Mapper
public interface MonitoringPointMapper extends BaseMapper<MonitoringPoint> {
}
