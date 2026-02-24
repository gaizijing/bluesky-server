-- ============================================================
-- 低空气象飞行保障服务系统 - 初始化数据脚本
-- 执行方式: psql -U postgres -d bluesky -f sql/init_data.sql
-- ============================================================

-- 管理员用户 (密码: admin123)
INSERT INTO users (id, username, password, name, status, login_count)
VALUES ('user-admin', 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '系统管理员', 'active', 0)
ON CONFLICT (id) DO NOTHING;

-- 重点关注区域示例数据
INSERT INTO monitoring_points (id, name, type, location, longitude, latitude, bbox_min_lng, bbox_min_lat, bbox_max_lng, bbox_max_lat, status, last_update, is_active) VALUES
('point-1', '青岛中心起降坪', 'takeoff', '市南区-五四广场附近', 120.3835, 36.0625, 120.3735, 36.0525, 120.3935, 36.0725, 'warning',  EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, true),
('point-2', '崂山区起降点',   'takeoff', '崂山区-石老人附近',   120.4750, 36.1120, 120.4650, 36.1020, 120.4850, 36.1220, 'available', EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, true),
('point-3', '西海岸作业点',   'operation','西海岸新区-金沙滩附近',120.1680, 35.9950, 120.1580, 35.9850, 120.1780, 36.0050, 'available', EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, true),
('point-4', '胶州湾作业点',   'operation','胶州市-胶州湾北岸',   120.0330, 36.2670, 120.0230, 36.2570, 120.0430, 36.2770, 'available', EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, true)
ON CONFLICT (id) DO NOTHING;

-- 飞行器型号示例数据
INSERT INTO aircraft_models (id, model_name, category, manufacturer, max_altitude, max_speed, cruise_speed, max_range, max_endurance, max_payload, is_active) VALUES
('aircraft-1', 'DJI Matrice 300 RTK', '多旋翼', '大疆创新', 5000, 83, 50, 40, 55, 2.7, true),
('aircraft-2', 'DJI Agras T40',       '多旋翼', '大疆创新', 4500, 54, 36, 7, 22, 40, true),
('aircraft-3', 'Wingcopter 198',       '固定翼', 'Wingcopter', 4000, 150, 120, 110, 90, 6, true),
('aircraft-4', '翼龙-10',              '固定翼', '成飞', 9000, 300, 220, 2000, 480, 200, true)
ON CONFLICT (id) DO NOTHING;

-- 飞行器气象限制
INSERT INTO aircraft_limits (aircraft_id, max_wind_speed, max_wind_shear, min_visibility, max_precipitation, min_cloud_base, temp_min, temp_max, max_humidity) VALUES
('aircraft-1', 12.0, 5.0, 1.5, 5.0, 200, -20.0, 50.0, 95),
('aircraft-2', 10.0, 4.0, 1.0, 2.0, 200, -10.0, 45.0, 90),
('aircraft-3', 18.0, 8.0, 3.0, 10.0, 300, -30.0, 55.0, 95),
('aircraft-4', 22.0, 10.0, 5.0, 20.0, 500, -40.0, 60.0, 98)
ON CONFLICT (aircraft_id) DO NOTHING;

-- 风险预警示例数据
INSERT INTO risk_warnings (id, point_id, level, type, area, start_time, end_time, warning_date, detail, suggestion, handle_status) VALUES
('RW20251103001', 'point-1', 'danger',  '强风',    'A起降点周边10km', '10:05:00', '11:30:00', CURRENT_DATE,
 '当前风速14.8m/s，预计10:30后增至18m/s，超过多数机型限制',
 '暂停该区域所有飞行任务，已起飞立即返航', 'unhandled'),
('RW20251103002', 'point-2', 'warning', '低能见度', 'B货运站至C工业园区航线', '11:00:00', '13:00:00', CURRENT_DATE,
 '预计能见度将从5.2km降至2.5km，接近小型无人机限制阈值',
 '小型无人机暂停飞行，大型飞行器需开启辅助导航', 'unhandled'),
('RW20251103003', 'point-3', 'info',    '温度骤降', '西海岸作业区全域', '18:00:00', '23:59:00', CURRENT_DATE,
 '今日18时后气温将快速下降约8℃至2℃，注意电池性能下降',
 '排查电池保温措施，调整飞行计划至白天高温时段', 'unhandled')
ON CONFLICT (id) DO NOTHING;

-- 飞行任务示例数据
INSERT INTO flight_tasks (task_id, type, type_color, aircraft_id, aircraft_type, takeoff, landing, plan_height, status, meteorology_adapt, adapt_reason, start_time, end_time, task_date) VALUES
('FT20251103001', '救援', '#FF4444', 'aircraft-1', 'DJI Matrice 300 RTK', '青岛中心起降坪', '崂山区起降点', 150, 'ongoing',   '适配',   '当前气象条件良好，适合救援飞行', '09:00:00', '10:30:00', CURRENT_DATE),
('FT20251103002', '物流', '#4488FF', 'aircraft-2', 'DJI Agras T40',       '西海岸作业点',   '胶州湾作业点', 120, 'waiting',   '不适配', '当前风速超过该机型飞行限制',     '11:00:00', '12:00:00', CURRENT_DATE),
('FT20251103003', '巡检', '#44AA44', 'aircraft-3', 'Wingcopter 198',      '崂山区起降点',   '崂山区起降点', 200, 'completed', '适配',   '气象条件满足巡检任务要求',       '07:00:00', '09:00:00', CURRENT_DATE)
ON CONFLICT (task_id) DO NOTHING;

-- 核心气象要素示例数据
INSERT INTO core_indicators (point_id, data_time, indicator_id, indicator_name, value, unit, status, threshold_warning, threshold_danger) VALUES
('point-1', NOW(), 'temp',       '温度',   18.5, '℃',    'normal',  NULL,  NULL),
('point-1', NOW(), 'humidity',   '湿度',   72,   '%',    'normal',  85,    95),
('point-1', NOW(), 'windSpeed',  '风速',   8.3,  'm/s',  'warning', 10.0,  15.0),
('point-1', NOW(), 'windDir',    '风向',   225,  '°',    'normal',  NULL,  NULL),
('point-1', NOW(), 'visibility', '能见度', 5.2,  'km',   'normal',  3.0,   1.0),
('point-1', NOW(), 'pressure',   '气压',   1013, 'hPa',  'normal',  NULL,  NULL),
('point-1', NOW(), 'precip',     '降水',   0.0,  'mm',   'normal',  5.0,   20.0);

-- 垂直剖面示例数据
INSERT INTO vertical_profile (point_id, data_time, height, wind_speed, temperature, humidity, visibility, pressure) VALUES
('point-1', NOW(), 50,  6.5, 18.2, 75, 5.5, 1015),
('point-1', NOW(), 100, 7.8, 17.5, 72, 5.2, 1012),
('point-1', NOW(), 150, 9.2, 16.8, 69, 4.9, 1009),
('point-1', NOW(), 200, 10.5,16.0, 66, 4.6, 1006),
('point-1', NOW(), 300, 12.1,14.5, 61, 4.2, 1001),
('point-1', NOW(), 400, 13.8,12.8, 55, 3.8, 996),
('point-1', NOW(), 500, 15.2,11.0, 50, 3.5, 991);

COMMIT;
