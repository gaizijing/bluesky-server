package com.bluesky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluesky.entity.RegionConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 地区配置Mapper
 *
 * @author BlueSky Team
 */
@Mapper
public interface RegionConfigMapper extends BaseMapper<RegionConfigEntity> {

    /**
     * 获取默认地区配置
     */
    @Select("SELECT * FROM region_config WHERE is_default = true LIMIT 1")
    RegionConfigEntity getDefaultConfig();
}
