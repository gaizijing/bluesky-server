-- R2 青岛：补充 5 个起降点，便于总览面板展示

INSERT INTO landing_point (
    landing_point_id, region_id, name, code, type, address,
    longitude, latitude, altitude,
    bbox_min_lng, bbox_min_lat, bbox_max_lng, bbox_max_lat,
    enabled, created_at, updated_at
) VALUES
(
    'LP004', 'R2', '崂山低空枢纽', 'QD-02', 'takeoff', '青岛市崂山区',
    120.468000, 36.107000, 12.00,
    120.448000, 36.087000, 120.488000, 36.127000,
    TRUE, NOW(), NOW()
),
(
    'LP005', 'R2', '西海岸起降场', 'QD-03', 'takeoff', '青岛市黄岛区',
    120.192000, 35.960000, 8.00,
    120.172000, 35.940000, 120.212000, 35.980000,
    TRUE, NOW(), NOW()
),
(
    'LP006', 'R2', '古城建设发展有限公司', 'QD-04', 'operation', '青岛市城阳区',
    120.396000, 36.307000, 6.00,
    120.376000, 36.287000, 120.416000, 36.327000,
    TRUE, NOW(), NOW()
),
(
    'LP007', 'R2', '流亭物流起降点', 'QD-05', 'takeoff', '青岛市城阳区流亭',
    120.374000, 36.266000, 10.00,
    120.354000, 36.246000, 120.394000, 36.286000,
    TRUE, NOW(), NOW()
),
(
    'LP008', 'R2', '即墨滨海作业点', 'QD-06', 'operation', '青岛市即墨区',
    120.447000, 36.389000, 5.00,
    120.427000, 36.369000, 120.467000, 36.409000,
    TRUE, NOW(), NOW()
)
ON CONFLICT (landing_point_id) DO NOTHING;
