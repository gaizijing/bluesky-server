-- 摄像头与天气预报缓存表（代码中已使用，V1 未包含）

-- ========== 摄像头 ==========
CREATE TABLE cameras (
    id              VARCHAR(50) PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    location        VARCHAR(255),
    point_id        VARCHAR(50) NOT NULL REFERENCES landing_point (landing_point_id),
    longitude       NUMERIC(10, 6),
    latitude        NUMERIC(10, 6),
    status          VARCHAR(20) NOT NULL DEFAULT 'online',
    resolution      VARCHAR(32) DEFAULT '1920x1080',
    preview_url     VARCHAR(500),
    stream_url      VARCHAR(500),
    last_heartbeat  BIGINT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cameras_point_id ON cameras (point_id);
CREATE INDEX idx_cameras_status ON cameras (status);

-- ========== 天气预报（Open-Meteo 15 分钟粒度缓存） ==========
CREATE TABLE weather_forecast (
    id              BIGSERIAL PRIMARY KEY,
    point_id        VARCHAR(50) NOT NULL REFERENCES landing_point (landing_point_id),
    forecast_time   TIMESTAMP NOT NULL,
    temperature     NUMERIC(6, 2),
    wind_speed      NUMERIC(6, 2),
    visibility      NUMERIC(8, 2),
    precipitation   NUMERIC(6, 2),
    weather_code    INT,
    weather_text    VARCHAR(100),
    data_source     VARCHAR(50),
    data_quality    INT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_weather_forecast_point_time ON weather_forecast (point_id, forecast_time);
