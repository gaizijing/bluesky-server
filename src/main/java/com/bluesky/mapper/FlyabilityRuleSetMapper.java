package com.bluesky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluesky.entity.FlyabilityRuleSet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FlyabilityRuleSetMapper extends BaseMapper<FlyabilityRuleSet> {

    @Select("SELECT * FROM flyability_rule_set WHERE status = 'PUBLISHED' ORDER BY effective_from DESC NULLS LAST LIMIT 1")
    FlyabilityRuleSet selectLatestPublished();

    @Select("SELECT COALESCE(MAX(version_no), 0) FROM flyability_rule_set WHERE status = 'PUBLISHED'")
    int selectMaxPublishedVersionNo();
}
