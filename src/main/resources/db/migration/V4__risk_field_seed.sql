-- P4 MetViz / 航路分析：risk_field_cache 种子（替代已删除的 microscale_weather）
-- bucket_time = 当前整点；R1 含 MetViz 工具栏高度 100/300/500/1000/2000m

DELETE FROM risk_field_cache
WHERE rule_version = 'RS001-v1-seed'
  AND region_id IN ('R1', 'R2');

-- R1 天津宁河区：10×10 × 5 高度层 = 500 格点
INSERT INTO risk_field_cache (
    region_id, bucket_time, height_m, lng, lat,
    value, level, reason, factors_json, rule_version, computed_at
)
SELECT
    'R1',
    date_trunc('hour', NOW()),
    h.height_m,
    117.4 + (118.1 - 117.4) * c.col / 9.0,
    39.1 + (39.5 - 39.1) * r.row / 9.0,
    ROUND(v.raw::numeric, 2),
    CASE
        WHEN v.raw >= 70 THEN 'HIGH'
        WHEN v.raw >= 40 THEN 'MEDIUM'
        ELSE 'LOW'
    END,
    CASE
        WHEN v.raw >= 70 THEN '风速偏大，建议暂缓放飞'
        WHEN v.raw >= 40 THEN '综合风险中等，请关注风切变'
        ELSE '综合风险一般'
    END,
    '{"wind":0.4,"windShear":0.3,"visibility":0.3}'::jsonb,
    'RS001-v1-seed',
    NOW()
FROM generate_series(0, 9) AS r(row)
CROSS JOIN generate_series(0, 9) AS c(col)
CROSS JOIN (VALUES (100), (300), (500), (1000), (2000)) AS h(height_m)
CROSS JOIN LATERAL (
    SELECT LEAST(
        100,
        GREATEST(
            5,
            28
            + 22 * sin(r.row * 0.85 + c.col * 0.55 + h.height_m * 0.002)
            + 18 * cos(c.col * 0.72 - r.row * 0.4)
            + (r.row + c.col) * 2.5
        )
    ) AS raw
) AS v;

-- R2 青岛：8×8 × 100m、500m = 128 格点
INSERT INTO risk_field_cache (
    region_id, bucket_time, height_m, lng, lat,
    value, level, reason, factors_json, rule_version, computed_at
)
SELECT
    'R2',
    date_trunc('hour', NOW()),
    h.height_m,
    120.0 + (121.0 - 120.0) * c.col / 7.0,
    36.0 + (37.0 - 36.0) * r.row / 7.0,
    ROUND(v.raw::numeric, 2),
    CASE
        WHEN v.raw >= 70 THEN 'HIGH'
        WHEN v.raw >= 40 THEN 'MEDIUM'
        ELSE 'LOW'
    END,
    CASE
        WHEN v.raw >= 70 THEN '沿海风速增强'
        WHEN v.raw >= 40 THEN '能见度一般'
        ELSE '适飞条件较好'
    END,
    '{"wind":0.45,"visibility":0.35,"precip":0.2}'::jsonb,
    'RS001-v1-seed',
    NOW()
FROM generate_series(0, 7) AS r(row)
CROSS JOIN generate_series(0, 7) AS c(col)
CROSS JOIN (VALUES (100), (500)) AS h(height_m)
CROSS JOIN LATERAL (
    SELECT LEAST(
        100,
        GREATEST(
            8,
            35
            + 20 * sin(r.row * 0.9 + c.col * 0.6)
            + 12 * cos(c.col - r.row * 0.5)
        )
    ) AS raw
) AS v;
