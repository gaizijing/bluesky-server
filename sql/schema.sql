-- ============================================================
-- 低空气象飞行保障服务系统 - 完整数据库建表脚本
-- 数据库: PostgreSQL 14+
-- 执行方式: psql -U postgres -d bluesky -f sql/schema.sql
-- ============================================================

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
    abnormal_value VARCHAR(50),
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
