-- P1 预警验收种子（勿改 V2，避免 Flyway checksum 冲突）

INSERT INTO warning_rule_set (rule_set_id, name, version_no, status, effective_from, rules_json, enable_llm, created_at, updated_at)
VALUES (
    'WS001', '默认 L2 预警规则', 1, 'PUBLISHED', NOW(),
    '{"rules":[{"id":"W1","type":"WIND","threshold":12}]}'::jsonb,
    FALSE, NOW(), NOW()
)
ON CONFLICT (rule_set_id) DO NOTHING;

INSERT INTO warning_records (
    warning_id, warning_type, display_region_id, target_type, target_id,
    level, title, content, status, dedupe_key, occurrence_count,
    bucket_time, rule_version, last_triggered_at, created_at, updated_at
) VALUES (
    'WR001', 'L2', 'R1', 'LANDING_POINT', 'LP001',
    'YELLOW', '宁河主起降场风速预警', '当前风速接近黄色阈值，请关注放飞决策。',
    'NEW', 'L2:W1:LANDING_POINT:LP001:R1:seed', 1,
    NOW(), 'WS001-v1', NOW(), NOW(), NOW()
)
ON CONFLICT (warning_id) DO NOTHING;
