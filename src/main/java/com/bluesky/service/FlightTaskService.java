package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.entity.AircraftLimit;
import com.bluesky.entity.AircraftModel;
import com.bluesky.entity.FlightTask;
import com.bluesky.entity.WeatherRealtime;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.AircraftLimitMapper;
import com.bluesky.mapper.AircraftModelMapper;
import com.bluesky.mapper.FlightTaskMapper;
import com.bluesky.mapper.WeatherRealtimeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 飞行任务服务
 * 负责飞行任务管理及飞行器适配分析
 */
@Service
@RequiredArgsConstructor
public class FlightTaskService {

    private final FlightTaskMapper flightTaskMapper;
    private final AircraftModelMapper aircraftModelMapper;
    private final AircraftLimitMapper aircraftLimitMapper;
    private final WeatherRealtimeMapper weatherRealtimeMapper;

    // ==================== 飞行任务 ====================

    /**
     * 获取飞行任务列表
     */
    public Map<String, Object> getFlightTasks(String taskDate, String status, String type) {
        LocalDate date = (taskDate != null && !taskDate.isEmpty())
                ? LocalDate.parse(taskDate)
                : LocalDate.now();

        LambdaQueryWrapper<FlightTask> wrapper = new LambdaQueryWrapper<FlightTask>()
                .eq(FlightTask::getTaskDate, date)
                .eq(status != null && !status.isEmpty(), FlightTask::getStatus, status)
                .eq(type != null && !type.isEmpty(), FlightTask::getType, type)
                .orderByDesc(FlightTask::getCreatedAt);

        List<FlightTask> tasks = flightTaskMapper.selectList(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("taskDate", date.toString());
        result.put("total", tasks.size());
        result.put("tasks", tasks);
        return result;
    }

    /**
     * 获取单个飞行任务
     */
    public FlightTask getTaskById(String taskId) {
        FlightTask task = flightTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "飞行任务不存在: " + taskId);
        }
        return task;
    }

    /**
     * 创建飞行任务
     */
    public FlightTask createTask(FlightTask task) {
        task.setStatus("waiting");
        task.setTaskDate(task.getTaskDate() != null ? task.getTaskDate() : LocalDate.now());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        flightTaskMapper.insert(task);
        return task;
    }

    /**
     * 更新飞行任务
     */
    public FlightTask updateTask(String taskId, FlightTask task) {
        getTaskById(taskId); // 校验存在
        task.setTaskId(taskId);
        task.setUpdatedAt(LocalDateTime.now());
        flightTaskMapper.updateById(task);
        return task;
    }

    /**
     * 更新任务状态
     */
    public void updateTaskStatus(String taskId, String status) {
        FlightTask task = getTaskById(taskId);
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        if ("ongoing".equals(status)) {
            task.setActualStartTime(LocalDateTime.now());
        } else if ("completed".equals(status) || "cancelled".equals(status)) {
            task.setActualEndTime(LocalDateTime.now());
        }
        flightTaskMapper.updateById(task);
    }

    // ==================== 飞行器适配分析 ====================

    /**
     * 飞行器气象适配分析
     * 根据当前气象条件判断各飞行器是否适合飞行
     */
    public Map<String, Object> getAircraftAdapt(String pointId) {
        // 获取当前实时气象
        WeatherRealtime weather = weatherRealtimeMapper.selectOne(
                new LambdaQueryWrapper<WeatherRealtime>()
                        .eq(WeatherRealtime::getPointId, pointId)
                        .orderByDesc(WeatherRealtime::getObsTime)
                        .last("LIMIT 1"));

        // 获取所有激活的飞行器型号
        List<AircraftModel> models = aircraftModelMapper.selectList(
                new LambdaQueryWrapper<AircraftModel>()
                        .eq(AircraftModel::getIsActive, true));

        // 当前气象条件（若无实时数据则用默认值）
        double currentWindSpeed = weather != null && weather.getWindSpeed() != null
                ? weather.getWindSpeed().doubleValue() / 3.6 // km/h → m/s
                : 0.0;
        double currentVis = weather != null && weather.getVis() != null
                ? weather.getVis().doubleValue()
                : 10.0;
        int currentCloudBase = 500; // 默认值，实际应从数据中取

        Map<String, Object> currentConditions = new HashMap<>();
        currentConditions.put("windSpeed", currentWindSpeed);
        currentConditions.put("visibility", currentVis);
        currentConditions.put("cloudBase", currentCloudBase);
        if (weather != null) {
            currentConditions.put("temperature", weather.getTemp());
            currentConditions.put("humidity", weather.getHumidity());
        }

        // 逐一评估
        List<Map<String, Object>> adaptList = new ArrayList<>();
        for (AircraftModel model : models) {
            AircraftLimit limit = aircraftLimitMapper.selectOne(
                    new LambdaQueryWrapper<AircraftLimit>()
                            .eq(AircraftLimit::getAircraftId, model.getId())
                            .last("LIMIT 1"));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", model.getId());
            item.put("type", model.getModelName());
            item.put("category", model.getCategory());

            if (limit != null) {
                item.put("limits", Map.of(
                        "maxWindSpeed", limit.getMaxWindSpeed(),
                        "minVisibility", limit.getMinVisibility(),
                        "minCloudBase", limit.getMinCloudBase()));
                // 适配判断
                boolean windOk = limit.getMaxWindSpeed() == null
                        || currentWindSpeed <= limit.getMaxWindSpeed().doubleValue();
                boolean visOk = limit.getMinVisibility() == null
                        || currentVis >= limit.getMinVisibility().doubleValue();
                boolean cloudOk = limit.getMinCloudBase() == null
                        || currentCloudBase >= limit.getMinCloudBase();

                boolean suitable = windOk && visOk && cloudOk;
                item.put("adapt", suitable ? "适配" : "不适配");

                List<String> reasons = new ArrayList<>();
                if (!windOk)
                    reasons.add("风速" + String.format("%.1f", currentWindSpeed) + "m/s 超过限制");
                if (!visOk)
                    reasons.add("能见度" + String.format("%.1f", currentVis) + "km 低于限制");
                if (!cloudOk)
                    reasons.add("云底高" + currentCloudBase + "m 低于限制");
                item.put("reason", suitable ? "当前气象条件良好，适合飞行" : String.join("；", reasons));
            } else {
                item.put("adapt", "适配");
                item.put("reason", "暂无限制数据，默认适配");
            }
            adaptList.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("updateTime", LocalDateTime.now().toString());
        result.put("pointId", pointId);
        result.put("currentConditions", currentConditions);
        result.put("aircraftList", adaptList);
        return result;
    }

    /**
     * 获取飞行器型号列表
     */
    public List<AircraftModel> getAircraftModels() {
        return aircraftModelMapper.selectList(
                new LambdaQueryWrapper<AircraftModel>().eq(AircraftModel::getIsActive, true));
    }
}
