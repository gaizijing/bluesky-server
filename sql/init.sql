-- ========================================
-- 低空气象飞行保障服务系统 - 数据库初始化脚本
-- ========================================

-- 创建用户表
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
COMMENT ON COLUMN users.status IS '用户状态: active-激活, inactive-停用, locked-锁定';

-- 创建重点关注区域表
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
COMMENT ON COLUMN monitoring_points.type IS '类型: takeoff-起降点, operation-作业点';
COMMENT ON COLUMN monitoring_points.status IS '状态: available/warning/unavailable';

-- 创建风险预警表
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
    FOREIGN KEY (point_id) REFERENCES monitoring_points(id)
);

COMMENT ON TABLE risk_warnings IS '风险预警表';
COMMENT ON COLUMN risk_warnings.level IS '预警等级: danger/warning/info';
COMMENT ON COLUMN risk_warnings.handle_status IS '处理状态: unhandled/handled';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

CREATE INDEX IF NOT EXISTS idx_monitoring_points_status ON monitoring_points(status);
CREATE INDEX IF NOT EXISTS idx_monitoring_points_type ON monitoring_points(type);
CREATE INDEX IF NOT EXISTS idx_monitoring_points_active ON monitoring_points(is_active);

CREATE INDEX IF NOT EXISTS idx_risk_warnings_point_id ON risk_warnings(point_id);
CREATE INDEX IF NOT EXISTS idx_risk_warnings_level ON risk_warnings(level);
CREATE INDEX IF NOT EXISTS idx_risk_warnings_status ON risk_warnings(handle_status);
CREATE INDEX IF NOT EXISTS idx_risk_warnings_date ON risk_warnings(warning_date DESC);

-- 插入初始数据
-- 插入默认管理员用户(密码: admin123, BCrypt加密后的值)
INSERT INTO users (id, username, password, name, status, login_count)
VALUES ('user-admin', 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '系统管理员', 'active', 0)
ON CONFLICT (id) DO NOTHING;

-- 插入示例重点关注区域
INSERT INTO monitoring_points (id, name, type, location, longitude, latitude, bbox_min_lng, bbox_min_lat, bbox_max_lng, bbox_max_lat, status, last_update, is_active)
VALUES 
('point-1', '青岛中心起降坪', 'takeoff', '市南区-五四广场附近', 120.3835, 36.0625, 120.3735, 36.0525, 120.3935, 36.0725, 'available', EXTRACT(EPOCH FROM NOW()) * 1000, true),
('point-2', '崂山区起降点', 'takeoff', '崂山区-石老人附近', 120.4750, 36.1120, 120.4650, 36.1020, 120.4850, 36.1220, 'available', EXTRACT(EPOCH FROM NOW()) * 1000, true),
('point-3', '西海岸作业点', 'operation', '西海岸新区-金沙滩附近', 120.1680, 35.9950, 120.1580, 35.9850, 120.1780, 36.0050, 'warning', EXTRACT(EPOCH FROM NOW()) * 1000, true)
ON CONFLICT (id) DO NOTHING;

-- 插入示例风险预警
INSERT INTO risk_warnings (id, point_id, level, type, area, start_time, end_time, warning_date, detail, suggestion, handle_status)
VALUES 
('RW20251103001', 'point-1', 'danger', '强风', 'A机场周边10km', '10:05:00', '11:30:00', CURRENT_DATE, '当前风速14.8m/s,预计10:30后增至18m/s,超过多数机型限制', '暂停该区域所有飞行任务,已起飞的立即返航', 'unhandled'),
('RW20251103002', 'point-2', 'warning', '低能见度', 'B货运站至C工业园区航线', '11:00:00', '13:00:00', CURRENT_DATE, '预计能见度将从5.2km降至2.5km,接近小型无人机限制阈值', '小型无人机暂停飞行,大型飞行器需开启辅助导航', 'unhandled')
ON CONFLICT (id) DO NOTHING;

COMMIT;
