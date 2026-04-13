package com.bluesky.dto;

import com.bluesky.entity.MonitoringPoint;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 重点关注区域 DTO（包含前端需要的 coordinates 数组格式）
 */
@Data
public class MonitoringPointDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String code;
    private String type;
    private String location;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private List<List<BigDecimal>> bbox;
    private BigDecimal altitude;
    private String status;
    private Boolean isSelected;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 从实体转换为 DTO
     */
    public static MonitoringPointDTO fromEntity(MonitoringPoint entity) {
        if (entity == null) {
            return null;
        }
        MonitoringPointDTO dto = new MonitoringPointDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCode(entity.getCode());
        dto.setType(entity.getType());
        dto.setLocation(entity.getLocation());
        dto.setLongitude(entity.getLongitude());
        dto.setLatitude(entity.getLatitude());

        // 组装 bbox 格式 [[minLng, minLat], [maxLng, maxLat]]
        if (entity.getBboxMinLng() != null && entity.getBboxMinLat() != null
                && entity.getBboxMaxLng() != null && entity.getBboxMaxLat() != null) {
            dto.setBbox(Arrays.asList(
                    Arrays.asList(entity.getBboxMinLng(), entity.getBboxMinLat()),
                    Arrays.asList(entity.getBboxMaxLng(), entity.getBboxMaxLat())
            ));
        }

        dto.setAltitude(entity.getAltitude());
        // 状态值映射：后端存储英文，前端管理页面需要中文
        dto.setStatus(entity.getStatus());
        dto.setIsSelected(entity.getIsSelected());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        return dto;
    }

}
