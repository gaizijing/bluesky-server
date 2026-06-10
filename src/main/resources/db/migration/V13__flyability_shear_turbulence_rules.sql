-- 适飞规则补充：风切变、颠簸指数、湍流阈值（与下钻 1H 适飞热力图因子对齐）

UPDATE flyability_rule_set
SET rules_json = rules_json || '{
  "windShearMs": {"yellow": 3, "red": 5},
  "turbulenceIndex": {"yellow": 0.35, "red": 0.6},
  "turbulence": {"yellow": 0.35, "red": 0.6}
}'::jsonb,
    updated_at = NOW()
WHERE rule_set_id = 'FS001'
  AND status = 'PUBLISHED';
