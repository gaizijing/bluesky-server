-- 预警测试种子：R1/R2 多条，覆盖未读/已读/已处理与不同等级

INSERT INTO warning_records (
    warning_id, warning_type, display_region_id, target_type, target_id,
    level, title, content, status, dedupe_key, occurrence_count,
    bucket_time, rule_version, last_triggered_at, created_at, updated_at
) VALUES
(
    'WR101', 'L2', 'R2', 'LANDING_POINT', 'LP004',
    'RED', '崂山低空枢纽阵风超限', '当前阵风 14.2 m/s，超过 L2 阈值 12 m/s，建议暂停放飞。',
    'NEW', 'seed:WR101', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '5 minutes', NOW(), NOW()
),
(
    'WR102', 'L2', 'R2', 'ROUTE', 'route-sf-huangdao',
    'YELLOW', '顺丰-黄岛保税航路能见度偏低', '航路沿线能见度约 2.8 km，低于适飞建议值，注意低云影响。',
    'NEW', 'seed:WR102', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '18 minutes', NOW(), NOW()
),
(
    'WR103', 'L1', 'R2', 'LANDING_POINT', 'LP007',
    'YELLOW', '流亭物流起降点侧风预警', '侧风分量持续偏高，建议调整起降方向或延迟出港。',
    'ACKNOWLEDGED', 'seed:WR103', 2, NOW(), 'WS001-v1', NOW() - INTERVAL '42 minutes', NOW(), NOW()
),
(
    'WR104', 'L1', 'R2', 'AIRSPACE', 'R2',
    'GREEN', '城阳区域低云增多', '云底高度下降，对低空目视飞行有一般影响，持续监测。',
    'NEW', 'seed:WR104', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '65 minutes', NOW(), NOW()
),
(
    'WR105', 'L2', 'R2', 'LANDING_POINT', 'LP005',
    'RED', '西海岸起降场强降水', '未来 1 小时有中到大雨，不建议执行物流放飞任务。',
    'NEW', 'seed:WR105', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '90 minutes', NOW(), NOW()
),
(
    'WR201', 'L2', 'R1', 'LANDING_POINT', 'LP001',
    'RED', '宁河主起降场阵风超限', '阵风达 13.5 m/s，超过放飞上限，建议停飞。',
    'NEW', 'seed:WR201', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '10 minutes', NOW(), NOW()
),
(
    'WR202', 'L1', 'R1', 'LANDING_POINT', 'LP002',
    'YELLOW', '宁河备降场能见度下降', '能见度约 3.2 km，低于理想值，注意目视条件。',
    'ACKNOWLEDGED', 'seed:WR202', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '35 minutes', NOW(), NOW()
),
(
    'WR203', 'L1', 'R1', 'ROUTE', 'route-nh-logistics',
    'GREEN', '宁河物流航线低云提示', '航路中段云底略低，对一般任务影响有限。',
    'HANDLED', 'seed:WR203', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '120 minutes', NOW(), NOW()
)
ON CONFLICT (warning_id) DO NOTHING;
