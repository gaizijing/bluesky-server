-- 演示禁飞区：R1 宁河、R2 青岛各 1 个多边形（可在管理端继续导入/编辑）

INSERT INTO no_fly_zone (zone_id, region_id, name, zone_type, geometry_json, enabled, created_at, updated_at)
SELECT
    'NFZ_R1_001',
    'R1',
    '宁河东部禁飞区',
    'PERMANENT',
    '{"type":"Polygon","coordinates":[[[117.860,39.310],[117.900,39.310],[117.900,39.350],[117.860,39.350],[117.860,39.310]]]}'::jsonb,
    TRUE,
    NOW(),
    NOW()
WHERE EXISTS (SELECT 1 FROM region WHERE region_id = 'R1')
  AND NOT EXISTS (SELECT 1 FROM no_fly_zone WHERE zone_id = 'NFZ_R1_001');

INSERT INTO no_fly_zone (zone_id, region_id, name, zone_type, geometry_json, enabled, created_at, updated_at)
SELECT
    'NFZ_R2_001',
    'R2',
    '崂山禁飞演示区',
    'PERMANENT',
    '{"type":"Polygon","coordinates":[[[120.580,36.120],[120.650,36.120],[120.650,36.180],[120.580,36.180],[120.580,36.120]]]}'::jsonb,
    TRUE,
    NOW(),
    NOW()
WHERE EXISTS (SELECT 1 FROM region WHERE region_id = 'R2')
  AND NOT EXISTS (SELECT 1 FROM no_fly_zone WHERE zone_id = 'NFZ_R2_001');
