-- PostgreSQL: 风险区表 + 种子（坐标落在 application.yml wind.field.bounds 附近，可按 RegionConfig 调整）

CREATE TABLE IF NOT EXISTS risk_zones (
    id              VARCHAR(64) PRIMARY KEY,
    zone_type       VARCHAR(32)  NOT NULL,
    label           VARCHAR(256),
    center_lng      DOUBLE PRECISION NOT NULL,
    center_lat      DOUBLE PRECISION NOT NULL,
    radius_m        DOUBLE PRECISION NOT NULL,
    height_m        DOUBLE PRECISION NOT NULL,
    sort_order      INT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT DEFAULT 0
);

-- 与默认 bounds west:119 east:122 south:35 north:38 一致的内点示例
INSERT INTO risk_zones (id, zone_type, label, center_lng, center_lat, radius_m, height_m, sort_order, deleted)
VALUES
    ('seed-nofly-1', 'NO_FLY', '禁飞区-模拟', 120.2, 36.1, 600, 500, 1, 0),
    ('seed-caution-1', 'CAUTION', '', 120.55, 36.35, 1000, 400, 2, 0)
ON CONFLICT (id) DO NOTHING;
