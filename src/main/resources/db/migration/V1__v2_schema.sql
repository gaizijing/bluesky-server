-- V2.0 方案 B：空库全量建表（P0 核心 + P1/P2 预留）

-- ========== 用户与权限 ==========
CREATE TABLE users (
    id              VARCHAR(50) PRIMARY KEY,
    username        VARCHAR(50) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    name            VARCHAR(100),
    email           VARCHAR(100),
    phone           VARCHAR(20),
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    role            VARCHAR(32) NOT NULL DEFAULT 'READ_ONLY',
    last_login_time TIMESTAMP,
    login_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(50),
    updated_by      VARCHAR(50)
);

CREATE TABLE region (
    region_id       VARCHAR(32) PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    center_lng      DOUBLE PRECISION,
    center_lat      DOUBLE PRECISION,
    west            DOUBLE PRECISION NOT NULL,
    east            DOUBLE PRECISION NOT NULL,
    south           DOUBLE PRECISION NOT NULL,
    north           DOUBLE PRECISION NOT NULL,
    map_lift_json   JSONB,
    model_url       VARCHAR(500),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    deleted         SMALLINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(50),
    updated_by      VARCHAR(50)
);

CREATE INDEX idx_region_enabled ON region (enabled) WHERE deleted = 0;

CREATE TABLE user_region_rel (
    id          BIGSERIAL PRIMARY KEY,
    user_id     VARCHAR(50) NOT NULL REFERENCES users (id),
    region_id   VARCHAR(32) NOT NULL REFERENCES region (region_id),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, region_id)
);

CREATE INDEX idx_user_region_user ON user_region_rel (user_id);

-- ========== 起降点 ==========
CREATE TABLE landing_point (
    landing_point_id VARCHAR(50) PRIMARY KEY,
    region_id        VARCHAR(32) NOT NULL REFERENCES region (region_id),
    name             VARCHAR(100) NOT NULL,
    code             VARCHAR(50),
    type             VARCHAR(20),
    address          VARCHAR(255),
    longitude        NUMERIC(10, 6) NOT NULL,
    latitude         NUMERIC(10, 6) NOT NULL,
    altitude         NUMERIC(10, 2),
    bbox_min_lng     NUMERIC(10, 6),
    bbox_min_lat     NUMERIC(10, 6),
    bbox_max_lng     NUMERIC(10, 6),
    bbox_max_lat     NUMERIC(10, 6),
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    deleted          SMALLINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_landing_region ON landing_point (region_id) WHERE deleted = 0;

-- ========== 航路 ==========
CREATE TABLE routes (
    id                  VARCHAR(255) PRIMARY KEY,
    region_id           VARCHAR(32) NOT NULL REFERENCES region (region_id),
    name                VARCHAR(255) NOT NULL,
    start_name          VARCHAR(255) NOT NULL,
    end_name            VARCHAR(255) NOT NULL,
    distance            NUMERIC(10, 2) NOT NULL DEFAULT 0,
    estimated_time      INT,
    weather_condition   VARCHAR(255),
    status              VARCHAR(50) NOT NULL DEFAULT 'available',
    average_risk        NUMERIC(5, 2) DEFAULT 0.5,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    aircraft_model      VARCHAR(255),
    flight_height       NUMERIC(10, 2),
    current_version_id  VARCHAR(255),
    start_time          TIMESTAMP,
    end_time            TIMESTAMP,
    deleted             SMALLINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_routes_region ON routes (region_id) WHERE deleted = 0;

CREATE TABLE route_version (
    route_version_id VARCHAR(255) PRIMARY KEY,
    route_id         VARCHAR(255) NOT NULL REFERENCES routes (id) ON DELETE CASCADE,
    version_no       INT NOT NULL DEFAULT 1,
    cruise_height_m  DOUBLE PRECISION,
    geometry_json    JSONB,
    waypoint_count   INT,
    distance_m       DOUBLE PRECISION,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(50),
    UNIQUE (route_id, version_no)
);

CREATE TABLE route_waypoints (
    id               VARCHAR(255) PRIMARY KEY,
    route_id         VARCHAR(255) NOT NULL REFERENCES routes (id) ON DELETE CASCADE,
    route_version_id VARCHAR(255) NOT NULL REFERENCES route_version (route_version_id) ON DELETE CASCADE,
    sequence         INT NOT NULL,
    name             VARCHAR(255),
    longitude        NUMERIC(12, 9) NOT NULL,
    latitude         NUMERIC(12, 9) NOT NULL,
    altitude         NUMERIC(10, 2) DEFAULT 0,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (route_version_id, sequence)
);

CREATE INDEX idx_route_waypoints_version ON route_waypoints (route_version_id);

CREATE TABLE route_segments (
    id               VARCHAR(255) PRIMARY KEY,
    route_id         VARCHAR(255) NOT NULL REFERENCES routes (id) ON DELETE CASCADE,
    route_version_id VARCHAR(255) NOT NULL REFERENCES route_version (route_version_id) ON DELETE CASCADE,
    sequence         INT NOT NULL,
    start_waypoint_id VARCHAR(255),
    end_waypoint_id   VARCHAR(255),
    distance         DOUBLE PRECISION,
    wind_direction   VARCHAR(50),
    wind_speed       DOUBLE PRECISION,
    visibility       DOUBLE PRECISION,
    precipitation    VARCHAR(50),
    risk_level       VARCHAR(50),
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ========== 禁飞区 ==========
CREATE TABLE no_fly_zone (
    zone_id         VARCHAR(50) PRIMARY KEY,
    region_id       VARCHAR(32) NOT NULL REFERENCES region (region_id),
    name            VARCHAR(100) NOT NULL,
    zone_type       VARCHAR(20) NOT NULL DEFAULT 'PERMANENT',
    geometry_json   JSONB NOT NULL,
    effective_from  TIMESTAMP,
    effective_to    TIMESTAMP,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    deleted         SMALLINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_nfz_region ON no_fly_zone (region_id) WHERE deleted = 0;

-- ========== 规则集（P1） ==========
CREATE TABLE flyability_rule_set (
    rule_set_id     VARCHAR(50) PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    version_no      INT NOT NULL DEFAULT 1,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    effective_from  TIMESTAMP,
    effective_to    TIMESTAMP,
    rules_json      JSONB NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(50)
);

CREATE TABLE risk_rule_set (
    rule_set_id     VARCHAR(50) PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    version_no      INT NOT NULL DEFAULT 1,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    effective_from  TIMESTAMP,
    effective_to    TIMESTAMP,
    rules_json      JSONB NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(50)
);

CREATE TABLE warning_rule_set (
    rule_set_id     VARCHAR(50) PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    version_no      INT NOT NULL DEFAULT 1,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    effective_from  TIMESTAMP,
    effective_to    TIMESTAMP,
    rules_json      JSONB NOT NULL,
    enable_llm      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(50)
);

-- ========== 缓存（P2） ==========
CREATE TABLE weather_grid_cache (
    cache_id          BIGSERIAL PRIMARY KEY,
    region_id         VARCHAR(32) NOT NULL,
    bucket_time       TIMESTAMP NOT NULL,
    height_m          INT NOT NULL,
    product           VARCHAR(32) NOT NULL,
    grid_json         JSONB NOT NULL,
    data_source_time  TIMESTAMP,
    computed_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMP,
    UNIQUE (region_id, bucket_time, height_m, product)
);

CREATE TABLE osi_landing_cache (
    cache_id           BIGSERIAL PRIMARY KEY,
    landing_point_id   VARCHAR(50) NOT NULL,
    bucket_time        TIMESTAMP NOT NULL,
    level              VARCHAR(20) NOT NULL,
    factor_results_json JSONB,
    rule_version       VARCHAR(50),
    computed_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (landing_point_id, bucket_time)
);

CREATE TABLE osi_route_cache (
    cache_id           BIGSERIAL PRIMARY KEY,
    route_id           VARCHAR(255) NOT NULL,
    route_version_id   VARCHAR(255) NOT NULL,
    bucket_time        TIMESTAMP NOT NULL,
    level              VARCHAR(20) NOT NULL,
    factor_results_json JSONB,
    rule_version       VARCHAR(50),
    computed_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (route_id, route_version_id, bucket_time)
);

CREATE TABLE risk_field_cache (
    cache_id         BIGSERIAL PRIMARY KEY,
    region_id        VARCHAR(32) NOT NULL,
    bucket_time      TIMESTAMP NOT NULL,
    height_m         INT NOT NULL,
    lng              DOUBLE PRECISION NOT NULL,
    lat              DOUBLE PRECISION NOT NULL,
    value            NUMERIC(6, 2) NOT NULL,
    level            VARCHAR(20),
    reason           VARCHAR(255),
    factors_json     JSONB,
    rule_version     VARCHAR(50),
    computed_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_risk_region_bucket ON risk_field_cache (region_id, bucket_time, height_m);

-- ========== 预警（P1/P2） ==========
CREATE TABLE warning_records (
    warning_id          VARCHAR(50) PRIMARY KEY,
    warning_type        VARCHAR(10) NOT NULL,
    display_region_id   VARCHAR(32) NOT NULL,
    target_type         VARCHAR(32),
    target_id           VARCHAR(255),
    level               VARCHAR(20),
    title               VARCHAR(255),
    content             TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'NEW',
    dedupe_key          VARCHAR(255) NOT NULL UNIQUE,
    occurrence_count    INT NOT NULL DEFAULT 1,
    bucket_time         TIMESTAMP,
    rule_version        VARCHAR(50),
    last_triggered_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_warning_region_status ON warning_records (display_region_id, status, last_triggered_at DESC);

CREATE TABLE warning_handle_records (
    id              BIGSERIAL PRIMARY KEY,
    warning_id      VARCHAR(50) NOT NULL REFERENCES warning_records (warning_id),
    action          VARCHAR(32) NOT NULL,
    operator_id     VARCHAR(50),
    remark          TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ========== LLM / 模拟飞行（P1） ==========
CREATE TABLE ai_conclusion_cache (
    conclusion_id   VARCHAR(50) PRIMARY KEY,
    cache_key       VARCHAR(512) NOT NULL UNIQUE,
    region_id       VARCHAR(32),
    target_type     VARCHAR(32),
    target_id       VARCHAR(255),
    bucket_time     TIMESTAMP,
    scene           VARCHAR(50),
    fact_pack_json  JSONB,
    conclusion_json JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE sim_session (
    session_id      VARCHAR(50) PRIMARY KEY,
    region_id       VARCHAR(32) NOT NULL,
    route_id        VARCHAR(255),
    status          VARCHAR(20) NOT NULL DEFAULT 'INIT',
    last_sequence   BIGINT DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
