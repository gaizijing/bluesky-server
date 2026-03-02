package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.dto.MonitoringPointDTO;
import com.bluesky.dto.MonitoringPointRequestDTO;
import com.bluesky.entity.MonitoringPoint;
import com.bluesky.service.MonitoringPointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 重点关注区域控制器
 *
 * @author BlueSky Team
 */
@Tag(name = "重点关注区域管理", description = "重点关注区域的增删改查等接口")
@RestController
@RequestMapping("/monitoring-points")
@RequiredArgsConstructor
public class MonitoringPointController {

    private final MonitoringPointService monitoringPointService;

    /**
     * 获取重点关注区域列表
     */
    @Operation(summary = "获取重点关注区域列表", description = "获取所有重点关注区域列表")
    @GetMapping
    public Result<List<MonitoringPointDTO>> getAll() {
        List<MonitoringPoint> points = monitoringPointService.getAll();
        List<MonitoringPointDTO> dtoList = points.stream()
                .map(MonitoringPointDTO::fromEntity)
                .collect(Collectors.toList());
        return Result.success(dtoList);
    }

    /**
     * 获取当前选中的重点关注区域
     */
    @Operation(summary = "获取当前选中的重点关注区域", description = "获取当前用户选中的重点关注区域")
    @GetMapping("/selected")
    public Result<MonitoringPointDTO> getSelected() {
        MonitoringPoint selected = monitoringPointService.getSelected();
        
        if (selected == null) {
            // 如果没有监测点数据，创建一个默认区域
            MonitoringPoint defaultPoint = createDefaultPoint();
            // 保存到数据库
            defaultPoint = monitoringPointService.add(defaultPoint);
            // 设置为选中状态
            monitoringPointService.updateSelected(defaultPoint.getId());
            selected = defaultPoint;
        }
        
        return Result.success(MonitoringPointDTO.fromEntity(selected));
    }

    private MonitoringPoint createDefaultPoint() {
        MonitoringPoint point = new MonitoringPoint();
        point.setId("point-default");
        point.setName("青岛中心起降坪");
        point.setType("takeoff");
        point.setLocation("市南区-五四广场附近");
        point.setLongitude(new java.math.BigDecimal("120.3835"));
        point.setLatitude(new java.math.BigDecimal("36.0625"));
        point.setBboxMinLng(new java.math.BigDecimal("120.3735"));
        point.setBboxMinLat(new java.math.BigDecimal("36.0525"));
        point.setBboxMaxLng(new java.math.BigDecimal("120.3935"));
        point.setBboxMaxLat(new java.math.BigDecimal("36.0725"));
        point.setStatus("available");
        point.setIsActive(true);
        point.setIsSelected(true); // 默认选中
        point.setLastUpdate(System.currentTimeMillis());
        return point;
    }

    /**
     * 更新选中的重点关注区域
     */
    @Operation(summary = "更新选中的重点关注区域", description = "更新当前用户选中的重点关注区域")
    @PostMapping("/selected")
    public Result<Map<String, Object>> updateSelected(@RequestBody Map<String, String> request) {
        String pointId = request.get("pointId");
        
        if (pointId == null || pointId.trim().isEmpty()) {
            throw new IllegalArgumentException("pointId参数不能为空");
        }
        
        // 更新选中状态
        monitoringPointService.updateSelected(pointId);
        
        // 获取更新后的监测点信息
        MonitoringPoint updatedPoint = monitoringPointService.getById(pointId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("pointId", pointId);
        result.put("pointName", updatedPoint.getName());
        result.put("selectedTime", java.time.LocalDateTime.now().toString());
        result.put("status", "updated");
        result.put("message", "监测点选中状态已更新");

        return Result.success(result);
    }

    /**
     * 添加新的重点关注区域
     */
    @Operation(summary = "添加新的重点关注区域", description = "添加新的重点关注区域")
    @PostMapping
    public Result<MonitoringPointDTO> add(@RequestBody MonitoringPointRequestDTO requestDTO) {
        MonitoringPoint point = convertToEntity(requestDTO);
        MonitoringPoint created = monitoringPointService.add(point);
        return Result.success(MonitoringPointDTO.fromEntity(created));
    }

    /**
     * 将前端请求DTO转换为实体
     */
    private MonitoringPoint convertToEntity(MonitoringPointRequestDTO dto) {
        MonitoringPoint point = new MonitoringPoint();
        point.setId(dto.getId());
        point.setName(dto.getName());
        point.setCode(dto.getCode());
        point.setType(dto.getType());
        point.setLocation(dto.getLocation());

        // 优先使用直接的 longitude/latitude 字段，如果没有则解析 coordinates 数组
        if (dto.getLongitude() != null && dto.getLatitude() != null) {
            point.setLongitude(dto.getLongitude());
            point.setLatitude(dto.getLatitude());
        } else if (dto.getCoordinates() != null && dto.getCoordinates().size() >= 2) {
            point.setLongitude(dto.getCoordinates().get(0));
            point.setLatitude(dto.getCoordinates().get(1));
        }

        // 解析 bbox（支持数组格式和对象格式）
        if (dto.hasValidBbox()) {
            point.setBboxMinLng(dto.getBboxMinLng());
            point.setBboxMinLat(dto.getBboxMinLat());
            point.setBboxMaxLng(dto.getBboxMaxLng());
            point.setBboxMaxLat(dto.getBboxMaxLat());
        }

        point.setAltitude(dto.getAltitude());
        // 状态值映射：前端可能是中文，需要转换为英文存储
        point.setStatus(convertStatusToEnglish(dto.getStatus()));
        point.setWarningReason(dto.getWarningReason());
        point.setLastUpdate(dto.getLastUpdate());
        point.setIsActive(dto.getIsActive());
        point.setCreatedBy(dto.getCreatedBy());
        point.setUpdatedBy(dto.getUpdatedBy());

        return point;
    }

    /**
     * 将中文状态值转换为英文（适配数据库存储）
     */
    private String convertStatusToEnglish(String status) {
        if (status == null) {
            return "available";
        }
        return switch (status) {
            case "正常" -> "available";
            case "维护中" -> "warning";
            case "故障" -> "unavailable";
            default -> status;
        };
    }

    /**
     * 更新重点关注区域
     */
    @Operation(summary = "更新重点关注区域", description = "更新重点关注区域信息")
    @PutMapping("/{id}")
    public Result<MonitoringPointDTO> update(@PathVariable String id, @RequestBody MonitoringPointRequestDTO requestDTO) {
        MonitoringPoint point = convertToEntity(requestDTO);
        point.setId(id);
        MonitoringPoint updated = monitoringPointService.update(point);
        return Result.success(MonitoringPointDTO.fromEntity(updated));
    }

    /**
     * 删除重点关注区域
     */
    @Operation(summary = "删除重点关注区域", description = "删除重点关注区域(逻辑删除)")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        monitoringPointService.delete(id);
        return Result.success();
    }
}
