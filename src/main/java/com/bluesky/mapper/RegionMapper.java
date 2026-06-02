package com.bluesky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluesky.entity.Region;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RegionMapper extends BaseMapper<Region> {

    @Select("SELECT * FROM region WHERE is_default = TRUE AND deleted = 0 LIMIT 1")
    Region selectDefault();

    @Update("UPDATE region SET is_default = FALSE, updated_at = NOW() WHERE deleted = 0 AND region_id <> #{regionId}")
    void clearOtherDefaults(@Param("regionId") String regionId);

    /** 将指定 Region 设为唯一默认（单条 SQL，避免多次 update 异常） */
    @Update("UPDATE region SET is_default = (region_id = #{regionId}), updated_at = NOW() WHERE deleted = 0")
    void setDefaultRegion(@Param("regionId") String regionId);
}
