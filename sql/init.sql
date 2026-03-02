-- ============================================================
-- 低空气象飞行保障服务系统 - 数据库初始化脚本（完整版）
-- 数据库: PostgreSQL 14+
-- 执行方式: psql -U postgres -d bluesky -f sql/init.sql
-- 包含: 建表 + 索引 + 初始化数据
-- ============================================================


-- ====================
--  一、建表结构（13张表）
-- ====================

-- 1. 用户表
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100),
    email VARCHAR(100),
    phone VARCHAR(20),
    status VARCHAR(20) DEFAULT 'active',
    last_login_time TIMESTAMP,
    login_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);
COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.status IS 'active-激活, inactive-停用, locked-锁定';
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- 2. 重点关注区域表
CREATE TABLE IF NOT EXISTS monitoring_points (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50),
    type VARCHAR(20) NOT NULL,
    location VARCHAR(200),
    longitude DECIMAL(10, 6) NOT NULL,
    latitude DECIMAL(10, 6) NOT NULL,
    bbox_min_lng DECIMAL(10, 6),
    bbox_min_lat DECIMAL(10, 6),
    bbox_max_lng DECIMAL(10, 6),
    bbox_max_lat DECIMAL(10, 6),
    altitude DECIMAL(8, 2),
    status VARCHAR(20) DEFAULT 'available',
    warning_reason VARCHAR(500),
    last_update BIGINT,
    is_active BOOLEAN DEFAULT true,
    is_selected BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);
COMMENT ON TABLE monitoring_points IS '重点关注区域表';
COMMENT ON COLUMN monitoring_points.type IS 'takeoff-起降点, operation-作业点';
COMMENT ON COLUMN monitoring_points.status IS 'available/warning/unavailable';
CREATE INDEX IF NOT EXISTS idx_monitoring_points_status ON monitoring_points(status);
CREATE INDEX IF NOT EXISTS idx_monitoring_points_type ON monitoring_points(type);
CREATE INDEX IF NOT EXISTS idx_monitoring_points_active ON monitoring_points(is_active);

-- 3. 风险预警表
CREATE TABLE IF NOT EXISTS risk_warnings (
    id VARCHAR(50) PRIMARY KEY,
    point_id VARCHAR(50),
    level VARCHAR(20) NOT NULL,
    type VARCHAR(50) NOT NULL,
    area VARCHAR(200),
    start_time TIME,
    end_time TIME,
    warning_date DATE,
    detail TEXT,
    suggestion TEXT,
    handle_status VARCHAR(20) DEFAULT 'unhandled',
    handler VARCHAR(50),
    handle_time TIMESTAMP,
    handle_remark TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rw_point FOREIGN KEY (point_id) REFERENCES monitoring_points(id)
);
COMMENT ON TABLE risk_warnings IS '风险预警表';
COMMENT ON COLUMN risk_warnings.level IS 'danger/warning/info';
COMMENT ON COLUMN risk_warnings.handle_status IS 'unhandled/handled';
CREATE INDEX IF NOT EXISTS idx_risk_warnings_point ON risk_warnings(point_id);
CREATE INDEX IF NOT EXISTS idx_risk_warnings_level ON risk_warnings(level);
CREATE INDEX IF NOT EXISTS idx_risk_warnings_status ON risk_warnings(handle_status);
CREATE INDEX IF NOT EXISTS idx_risk_warnings_date ON risk_warnings(warning_date DESC);

-- 4. 实时气象数据表
CREATE TABLE IF NOT EXISTS weather_realtime (
    id BIGSERIAL PRIMARY KEY,
    point_id VARCHAR(50),
    obs_time TIMESTAMP NOT NULL,
    temp DECIMAL(5, 2),
    feels_like DECIMAL(5, 2),
    icon VARCHAR(20),
    text VARCHAR(50),
    wind_360 INTEGER,
    wind_dir VARCHAR(20),
    wind_scale VARCHAR(10),
    wind_speed DECIMAL(5, 2),
    humidity INTEGER,
    precip DECIMAL(5, 2),
    pressure DECIMAL(7, 2),
    vis DECIMAL(5, 2),
    cloud INTEGER,
    dew DECIMAL(5, 2),
    wind_shear_level VARCHAR(20),
    stability_index VARCHAR(10),
    data_source VARCHAR(50),
    data_quality INTEGER DEFAULT 100,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wr_point FOREIGN KEY (point_id) REFERENCES monitoring_points(id)
);
COMMENT ON TABLE weather_realtime IS '实时气象数据表';
CREATE INDEX IF NOT EXISTS idx_wr_point_time ON weather_realtime(point_id, obs_time DESC);
CREATE INDEX IF NOT EXISTS idx_wr_obs_time ON weather_realtime(obs_time DESC);

-- 5. 风向趋势数据表
CREATE TABLE IF NOT EXISTS wind_trend (
    id BIGSERIAL PRIMARY KEY,
    point_id VARCHAR(50),
    data_time TIMESTAMP NOT NULL,
    time_label VARCHAR(10),
    wind_speed DECIMAL(5, 2),
    wind_dir INTEGER,
    upper_limit DECIMAL(5, 2),
    lower_limit DECIMAL(5, 2),
    deviation DECIMAL(5, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wt_point FOREIGN KEY (point_id) REFERENCES monitoring_points(id)
);
COMMENT ON TABLE wind_trend IS '风向趋势数据表';
CREATE INDEX IF NOT EXISTS idx_wt_point_time ON wind_trend(point_id, data_time DESC);

-- 6. 风场数据表
CREATE TABLE IF NOT EXISTS wind_field (
    id BIGSERIAL PRIMARY KEY,
    data_time TIMESTAMP NOT NULL,
    height INTEGER NOT NULL,
    longitude DECIMAL(10, 6) NOT NULL,
    latitude DECIMAL(10, 6) NOT NULL,
    u_component DECIMAL(6, 3),
    v_component DECIMAL(6, 3),
    speed DECIMAL(6, 3),
    direction INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE wind_field IS '3D风场数据表';
CREATE INDEX IF NOT EXISTS idx_wf_time_height ON wind_field(data_time DESC, height);

-- 7. 垂直剖面数据表
CREATE TABLE IF NOT EXISTS vertical_profile (
    id BIGSERIAL PRIMARY KEY,
    point_id VARCHAR(50),
    data_time TIMESTAMP NOT NULL,
    height INTEGER NOT NULL,
    wind_speed DECIMAL(5, 2),
    temperature DECIMAL(5, 2),
    humidity INTEGER,
    visibility DECIMAL(5, 2),
    pressure DECIMAL(7, 2),
    turbulence_level VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vp_point FOREIGN KEY (point_id) REFERENCES monitoring_points(id)
);
COMMENT ON TABLE vertical_profile IS '垂直剖面数据表';
CREATE INDEX IF NOT EXISTS idx_vp_point_time_height ON vertical_profile(point_id, data_time DESC, height);

-- 8. 核心气象要素表
CREATE TABLE IF NOT EXISTS core_indicators (
    id BIGSERIAL PRIMARY KEY,
    point_id VARCHAR(50),
    data_time TIMESTAMP NOT NULL,
    indicator_id VARCHAR(50) NOT NULL,
    indicator_name VARCHAR(50),
    value DECIMAL(10, 2),
    unit VARCHAR(20),
    precision VARCHAR(50),
    status VARCHAR(20) DEFAULT 'normal',
    threshold_warning DECIMAL(10, 2),
    threshold_danger DECIMAL(10, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE core_indicators IS '核心气象要素监测表';
COMMENT ON COLUMN core_indicators.status IS 'normal/warning/danger';
CREATE INDEX IF NOT EXISTS idx_ci_point_type ON core_indicators(point_id, indicator_id, data_time DESC);

-- 9. 微尺度天气数据表
CREATE TABLE IF NOT EXISTS microscale_weather (
    id BIGSERIAL PRIMARY KEY,
    region VARCHAR(100),
    data_time TIMESTAMP NOT NULL,
    grid_size INTEGER,
    grid_x DECIMAL(10, 6),
    grid_y DECIMAL(10, 6),
    risk_level INTEGER,
    wind_speed DECIMAL(5, 2),
    wind_shear DECIMAL(5, 2),
    turbulence DECIMAL(5, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE microscale_weather IS '微尺度天气网格数据表';
CREATE INDEX IF NOT EXISTS idx_mw_region_time ON microscale_weather(region, data_time DESC);

-- 10. 适飞分析数据表
CREATE TABLE IF NOT EXISTS suitability_analysis (
    id BIGSERIAL PRIMARY KEY,
    point_id VARCHAR(50),
    analysis_time TIMESTAMP NOT NULL,
    time_interval INTEGER,
    total_hours INTEGER,
    factor VARCHAR(50) NOT NULL,
    time_point VARCHAR(50) NOT NULL,
    is_suitable BOOLEAN,
    abnormal_value NUMERIC(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sa_point FOREIGN KEY (point_id) REFERENCES monitoring_points(id)
);
COMMENT ON TABLE suitability_analysis IS '适飞分析数据表';
CREATE INDEX IF NOT EXISTS idx_sa_point_factor ON suitability_analysis(point_id, factor, analysis_time DESC);

-- 11. 飞行器型号表
CREATE TABLE IF NOT EXISTS aircraft_models (
    id VARCHAR(50) PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    manufacturer VARCHAR(100),
    max_altitude INTEGER,
    max_speed DECIMAL(6, 2),
    cruise_speed DECIMAL(6, 2),
    max_range DECIMAL(8, 2),
    max_endurance INTEGER,
    max_payload DECIMAL(8, 2),
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE aircraft_models IS '飞行器型号基础信息表';

-- 12. 飞行器气象限制表
CREATE TABLE IF NOT EXISTS aircraft_limits (
    id BIGSERIAL PRIMARY KEY,
    aircraft_id VARCHAR(50) NOT NULL UNIQUE,
    max_wind_speed DECIMAL(5, 2),
    max_wind_shear DECIMAL(5, 2),
    min_visibility DECIMAL(5, 2),
    max_precipitation DECIMAL(5, 2),
    min_cloud_base INTEGER,
    temp_min DECIMAL(5, 2),
    temp_max DECIMAL(5, 2),
    max_humidity INTEGER,
    max_turbulence_level VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_al_aircraft FOREIGN KEY (aircraft_id) REFERENCES aircraft_models(id) ON DELETE CASCADE
);
COMMENT ON TABLE aircraft_limits IS '飞行器气象限制参数表';

-- 13. 飞行任务表
CREATE TABLE IF NOT EXISTS flight_tasks (
    task_id VARCHAR(50) PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    type_color VARCHAR(20),
    aircraft_id VARCHAR(50),
    aircraft_type VARCHAR(100),
    takeoff VARCHAR(100),
    landing VARCHAR(100),
    takeoff_point_id VARCHAR(50),
    landing_point_id VARCHAR(50),
    plan_height INTEGER,
    actual_height INTEGER,
    status VARCHAR(20) DEFAULT 'waiting',
    current_pos VARCHAR(200),
    current_lng DECIMAL(10, 6),
    current_lat DECIMAL(10, 6),
    meteorology_adapt VARCHAR(20),
    adapt_reason TEXT,
    start_time TIME,
    end_time TIME,
    actual_start_time TIMESTAMP,
    actual_end_time TIMESTAMP,
    task_date DATE NOT NULL,
    route_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT fk_ft_aircraft FOREIGN KEY (aircraft_id) REFERENCES aircraft_models(id)
);
COMMENT ON TABLE flight_tasks IS '飞行任务表';
COMMENT ON COLUMN flight_tasks.status IS 'waiting/ongoing/completed/cancelled';
CREATE INDEX IF NOT EXISTS idx_ft_date_status ON flight_tasks(task_date DESC, status);
CREATE INDEX IF NOT EXISTS idx_ft_type ON flight_tasks(type);
CREATE INDEX IF NOT EXISTS idx_ft_aircraft ON flight_tasks(aircraft_id);


-- ====================
--  二、初始化数据
-- ====================

-- 管理员用户（密码: admin123，BCrypt加密）
INSERT INTO users (id, username, password, name, status, login_count)
VALUES ('user-admin', 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '系统管理员', 'active', 0)
ON CONFLICT (id) DO NOTHING;

-- 重点关注区域示例数据
INSERT INTO monitoring_points (id, name, type, location, longitude, latitude, bbox_min_lng, bbox_min_lat, bbox_max_lng, bbox_max_lat, status, last_update, is_active) VALUES
('point-1', '青岛中心起降坪', 'takeoff',   '市南区-五四广场附近',     120.3835, 36.0625, 120.3735, 36.0525, 120.3935, 36.0725, 'warning',   EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, true),
('point-2', '崂山区起降点',   'takeoff',   '崂山区-石老人附近',       120.4750, 36.1120, 120.4650, 36.1020, 120.4850, 36.1220, 'available', EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, true),
('point-3', '西海岸作业点',   'operation', '西海岸新区-金沙滩附近',   120.1680, 35.9950, 120.1580, 35.9850, 120.1780, 36.0050, 'available', EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, true),
('point-4', '胶州湾作业点',   'operation', '胶州市-胶州湾北岸',       120.0330, 36.2670, 120.0230, 36.2570, 120.0430, 36.2770, 'available', EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, true)
ON CONFLICT (id) DO NOTHING;

-- 飞行器型号示例数据
INSERT INTO aircraft_models (id, model_name, category, manufacturer, max_altitude, max_speed, cruise_speed, max_range, max_endurance, max_payload, is_active) VALUES
('aircraft-1', 'DJI Matrice 300 RTK', '多旋翼', '大疆创新',   5000,  83,  50,  40,   55,  2.7, true),
('aircraft-2', 'DJI Agras T40',       '多旋翼', '大疆创新',   4500,  54,  36,   7,   22, 40.0, true),
('aircraft-3', 'Wingcopter 198',       '固定翼', 'Wingcopter', 4000, 150, 120, 110,   90,  6.0, true),
('aircraft-4', '翼龙-10',              '固定翼', '成飞',       9000, 300, 220, 2000, 480, 200.0, true)
ON CONFLICT (id) DO NOTHING;

-- 飞行器气象限制
INSERT INTO aircraft_limits (aircraft_id, max_wind_speed, max_wind_shear, min_visibility, max_precipitation, min_cloud_base, temp_min, temp_max, max_humidity) VALUES
('aircraft-1', 12.0, 5.0,  1.5,  5.0, 200, -20.0, 50.0, 95),
('aircraft-2', 10.0, 4.0,  1.0,  2.0, 200, -10.0, 45.0, 90),
('aircraft-3', 18.0, 8.0,  3.0, 10.0, 300, -30.0, 55.0, 95),
('aircraft-4', 22.0, 10.0, 5.0, 20.0, 500, -40.0, 60.0, 98)
ON CONFLICT (aircraft_id) DO NOTHING;

-- 风险预警示例数据
INSERT INTO risk_warnings (id, point_id, level, type, area, start_time, end_time, warning_date, detail, suggestion, handle_status) VALUES
('RW20251103001', 'point-1', 'danger',  '强风',     'A起降点周边10km',       '10:05:00', '11:30:00', CURRENT_DATE,
 '当前风速14.8m/s，预计10:30后增至18m/s，超过多数机型限制',
 '暂停该区域所有飞行任务，已起飞立即返航', 'unhandled'),
('RW20251103002', 'point-2', 'warning', '低能见度', 'B货运站至C工业园区航线', '11:00:00', '13:00:00', CURRENT_DATE,
 '预计能见度将从5.2km降至2.5km，接近小型无人机限制阈值',
 '小型无人机暂停飞行，大型飞行器需开启辅助导航', 'unhandled'),
('RW20251103003', 'point-3', 'info',    '温度骤降', '西海岸作业区全域',       '18:00:00', '23:59:00', CURRENT_DATE,
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
('point-1', NOW(), 'temp',       '温度',   18.5, '℃',   'normal',  NULL, NULL),
('point-1', NOW(), 'humidity',   '湿度',   72,   '%',    'normal',  85,   95),
('point-1', NOW(), 'windSpeed',  '风速',   8.3,  'm/s',  'warning', 10.0, 15.0),
('point-1', NOW(), 'windDir',    '风向',   225,  '°',    'normal',  NULL, NULL),
('point-1', NOW(), 'visibility', '能见度', 5.2,  'km',   'normal',  3.0,  1.0),
('point-1', NOW(), 'pressure',   '气压',   1013, 'hPa',  'normal',  NULL, NULL),
('point-1', NOW(), 'precip',     '降水',   0.0,  'mm',   'normal',  5.0,  20.0);

-- 垂直剖面示例数据
INSERT INTO vertical_profile (point_id, data_time, height, wind_speed, temperature, humidity, visibility, pressure) VALUES
('point-1', NOW(), 50,   6.5, 18.2, 75, 5.5, 1015),
('point-1', NOW(), 100,  7.8, 17.5, 72, 5.2, 1012),
('point-1', NOW(), 150,  9.2, 16.8, 69, 4.9, 1009),
('point-1', NOW(), 200, 10.5, 16.0, 66, 4.6, 1006),
('point-1', NOW(), 300, 12.1, 14.5, 61, 4.2, 1001),
('point-1', NOW(), 400, 13.8, 12.8, 55, 3.8,  996),
('point-1', NOW(), 500, 15.2, 11.0, 50, 3.5,  991);

COMMIT;
