-- MC Demo：R2 青岛 demo 区 risk_field_cache 种子
-- 对齐 cesium-mc-demo 假数据 bbox（120.12–120.48°E，35.96–36.22°N）
-- 12×12 × 5 高度层 = 720 格点；两座风暴核 + 随高度增强

DELETE FROM risk_field_cache
WHERE rule_version = 'RS001-v1-seed'
  AND region_id = 'R2';

INSERT INTO risk_field_cache (
    region_id, bucket_time, height_m, lng, lat,
    value, level, reason, factors_json, rule_version, computed_at
)
SELECT
    'R2',
    date_trunc('hour', NOW()),
    h.height_m,
    120.12 + (120.48 - 120.12) * c.col / 11.0,
    35.96 + (36.22 - 35.96) * r.row / 11.0,
    ROUND(v.raw::numeric, 2),
    CASE
        WHEN v.raw >= 70 THEN 'HIGH'
        WHEN v.raw >= 40 THEN 'MEDIUM'
        ELSE 'LOW'
    END,
    CASE
        WHEN v.raw >= 70 THEN '强对流核心，建议暂缓放飞'
        WHEN v.raw >= 40 THEN '回波中等，关注风切变'
        ELSE '背景回波较弱'
    END,
    '{"wind":0.45,"windShear":0.3,"visibility":0.25}'::jsonb,
    'RS001-v1-seed',
    NOW()
FROM generate_series(0, 11) AS r(row)
CROSS JOIN generate_series(0, 11) AS c(col)
CROSS JOIN (VALUES (100), (300), (500), (1000), (2000)) AS h(height_m)
CROSS JOIN LATERAL (
    SELECT
        120.12 + (120.48 - 120.12) * c.col / 11.0 AS lng,
        35.96 + (36.22 - 35.96) * r.row / 11.0 AS lat
) AS g
CROSS JOIN LATERAL (
    SELECT LEAST(
        100,
        GREATEST(
            6,
            8
            + 6 * sin(r.row * 0.82 + c.col * 0.58)
            + 52 * exp(-(
                power((g.lng - 120.252) / 0.032, 2)
                + power((g.lat - 36.065) / 0.028, 2)
              ))
              * (0.32 + h.height_m / 4200.0)
            + 44 * exp(-(
                power((g.lng - 120.368) / 0.038, 2)
                + power((g.lat - 36.105) / 0.034, 2)
              ))
              * (0.28 + h.height_m / 3600.0)
            + h.height_m * 0.014
        )
    ) AS raw
) AS v;
