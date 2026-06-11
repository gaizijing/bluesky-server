-- 补充预警测试数据：R2 联飞场景，覆盖风切变/湍流/禁飞区等

INSERT INTO warning_records (
    warning_id, warning_type, display_region_id, target_type, target_id,
    level, title, content, status, dedupe_key, occurrence_count,
    bucket_time, rule_version, last_triggered_at, created_at, updated_at
) VALUES
(
    'WR301', 'L2', 'R2', 'ROUTE', 'route-sf-huangdao',
    'RED', '顺丰航路风切变告警', '航路中段监测到中度风切变（水平风速变化 6 m/s），建议暂停联飞任务。',
    'NEW', 'seed:WR301', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '2 minutes', NOW(), NOW()
),
(
    'WR302', 'L2', 'R2', 'LANDING_POINT', 'LP006',
    'RED', '古城建设起降场湍流预警', '垂直气流速度达 4.2 m/s，对低空起降有显著影响，建议延迟放飞。',
    'NEW', 'seed:WR302', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '8 minutes', NOW(), NOW()
),
(
    'WR303', 'L1', 'R2', 'ROUTE', 'route-sf-huangdao',
    'YELLOW', '顺丰航路侧风偏大', '航路沿线侧风分量 8.5 m/s，接近机型限制，注意姿态修正。',
    'NEW', 'seed:WR303', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '15 minutes', NOW(), NOW()
),
(
    'WR304', 'L1', 'R2', 'LANDING_POINT', 'LP008',
    'YELLOW', '即墨滨海作业点湿度超标', '当前湿度 88%，超过农业无人机安全阈值，建议缩短单次作业时间。',
    'ACKNOWLEDGED', 'seed:WR304', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '28 minutes', NOW(), NOW()
),
(
    'WR305', 'L2', 'R2', 'AIRSPACE', 'R2',
    'RED', '城阳空域雷暴临近', '雷达显示 30 分钟后有雷暴云团移入，建议所有低空任务立即返航。',
    'NEW', 'seed:WR305', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '3 minutes', NOW(), NOW()
),
(
    'WR306', 'L1', 'R2', 'LANDING_POINT', 'LP004',
    'GREEN', '崂山枢纽电池低温提示', '气温降至 2℃，电池放电效率可能下降 15%，建议缩短续航预期。',
    'NEW', 'seed:WR306', 1, NOW(), 'WS001-v1', NOW() - INTERVAL '50 minutes', NOW(), NOW()
),
(
    'WR307', 'L2', 'R2', 'ROUTE', 'route-sf-huangdao',
    'RED', '顺丰航路进入禁飞缓冲区', '当前位置距禁飞区边界不足 500 m，请立即调整航向。',
    'NEW', 'seed:WR307', 2, NOW(), 'WS001-v1', NOW() - INTERVAL '1 minute', NOW(), NOW()
)
ON CONFLICT (warning_id) DO NOTHING;
