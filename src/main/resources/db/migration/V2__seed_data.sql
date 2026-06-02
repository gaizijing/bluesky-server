-- 种子数据：Region / 用户 / 起降点

INSERT INTO users (id, username, password, name, email, status, role, created_at, updated_at)
VALUES (
    'U001',
    'admin',
    '$2a$10$2enCWfyf4FjHFpecHPgl1eLWoqko//fuR9IGRkWpmcwAsy5ng.RaK',
    '系统管理员',
    'admin@bluesky.local',
    'active',
    'SUPER_ADMIN',
    NOW(),
    NOW()
);

INSERT INTO region (region_id, name, center_lng, center_lat, west, east, south, north, map_lift_json, enabled, is_default, created_at, updated_at)
VALUES
(
    'R1', '天津宁河区', 117.75, 39.30, 117.4, 118.1, 39.1, 39.5,
    '{"longitude":117.75,"latitude":39.30,"height":120000,"pitch":-45,"heading":0,"terrainExaggeration":1.2}'::jsonb,
    TRUE, TRUE, NOW(), NOW()
),
(
    'R2', '青岛', 120.5, 36.5, 120.0, 121.0, 36.0, 37.0,
    '{"longitude":120.5,"latitude":36.5,"height":120000,"pitch":-45,"heading":0,"terrainExaggeration":1.2}'::jsonb,
    TRUE, FALSE, NOW(), NOW()
);

INSERT INTO user_region_rel (user_id, region_id, created_at)
SELECT 'U001', region_id, NOW() FROM region;

INSERT INTO landing_point (
    landing_point_id, region_id, name, code, type, address,
    longitude, latitude, altitude,
    bbox_min_lng, bbox_min_lat, bbox_max_lng, bbox_max_lat,
    enabled, created_at, updated_at
) VALUES
(
    'LP001', 'R1', '宁河主起降场', 'NH-01', 'takeoff', '天津市宁河区',
    117.820000, 39.330000, 10.00,
    117.800000, 39.310000, 117.840000, 39.350000,
    TRUE, NOW(), NOW()
),
(
    'LP002', 'R1', '宁河备降场', 'NH-02', 'operation', '天津市宁河区东部',
    117.900000, 39.280000, 8.00,
    117.880000, 39.260000, 117.920000, 39.300000,
    TRUE, NOW(), NOW()
),
(
    'LP003', 'R2', '青岛起降场', 'QD-01', 'takeoff', '青岛市',
    120.450000, 36.250000, 5.00,
    120.430000, 36.230000, 120.470000, 36.270000,
    TRUE, NOW(), NOW()
);

INSERT INTO flyability_rule_set (rule_set_id, name, version_no, status, effective_from, rules_json, created_at, updated_at)
VALUES (
    'FS001', '默认适飞规则', 1, 'PUBLISHED', NOW(),
    '{
      "windSpeedMs": {"yellow": 8, "red": 12},
      "visibilityKm": {"yellow": 3, "red": 1},
      "precipMmH": {"yellow": 2, "red": 5},
      "temperatureC": {"min": -10, "max": 40},
      "cloudBaseM": {"yellow": 300, "red": 150}
    }'::jsonb,
    NOW(), NOW()
);

INSERT INTO risk_rule_set (rule_set_id, name, version_no, status, effective_from, rules_json, created_at, updated_at)
VALUES (
    'RS001', '默认R_met规则', 1, 'PUBLISHED', NOW(),
    '{
      "factors": [
        {"name": "wind", "weight": 0.4, "thresholds": {"medium": 8, "high": 12}},
        {"name": "windShear", "weight": 0.3, "thresholds": {"medium": 3, "high": 5}},
        {"name": "visibility", "weight": 0.3, "thresholds": {"medium": 3, "high": 1}}
      ],
      "outputCap": 100
    }'::jsonb,
    NOW(), NOW()
);
