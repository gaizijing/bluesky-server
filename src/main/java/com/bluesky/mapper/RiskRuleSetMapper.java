package com.bluesky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluesky.entity.RiskRuleSet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RiskRuleSetMapper extends BaseMapper<RiskRuleSet> {

    @Select("SELECT * FROM risk_rule_set WHERE status = 'PUBLISHED' ORDER BY effective_from DESC NULLS LAST LIMIT 1")
    RiskRuleSet selectLatestPublished();
}
