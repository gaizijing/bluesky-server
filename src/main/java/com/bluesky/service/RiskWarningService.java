package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.RiskWarning;
import com.bluesky.mapper.RiskWarningMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 风险预警服务
 *
 * @author BlueSky Team
 */
@Service
@RequiredArgsConstructor
public class RiskWarningService {

    private final RiskWarningMapper riskWarningMapper;

    /**
     * 获取风险预警数据
     */
    public Map<String, Object> getWarnings(String pointId, String timeRange) {
        // 解析时间范围
        String[] times = timeRange.split(",");
        LocalDate startDate = LocalDate.parse(times[0].substring(0, 10));
        LocalDate endDate = LocalDate.parse(times[1].substring(0, 10));

        // 查询预警数据
        List<RiskWarning> warnings = riskWarningMapper.selectList(
                new LambdaQueryWrapper<RiskWarning>()
                        .eq(pointId != null, RiskWarning::getPointId, pointId)
                        .between(RiskWarning::getWarningDate, startDate, endDate)
                        .orderByDesc(RiskWarning::getCreatedAt));

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("warnings", warnings);

        return result;
    }

    /**
     * 添加风险预警
     */
    public RiskWarning add(RiskWarning warning) {
        warning.setHandleStatus("unhandled");
        warning.setCreatedAt(LocalDateTime.now());
        warning.setUpdatedAt(LocalDateTime.now());

        riskWarningMapper.insert(warning);
        return warning;
    }

    /**
     * 处理预警
     */
    public void handle(String id, String handler, String remark) {
        RiskWarning warning = riskWarningMapper.selectById(id);
        if (warning != null) {
            warning.setHandleStatus("handled");
            warning.setHandler(handler);
            warning.setHandleTime(LocalDateTime.now());
            warning.setHandleRemark(remark);
            warning.setUpdatedAt(LocalDateTime.now());

            riskWarningMapper.updateById(warning);
        }
    }
}
