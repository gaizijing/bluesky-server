-- 默认视角改为倾斜俯视（pitch -35），能看到地平线；区域级高度 25000m

UPDATE region
SET map_lift_json = '{"longitude":117.75,"latitude":39.30,"height":25000,"pitch":-35,"heading":0,"terrainExaggeration":1.2}'::jsonb,
    updated_at = NOW()
WHERE region_id = 'R1';

UPDATE region
SET map_lift_json = '{"longitude":120.5,"latitude":36.5,"height":25000,"pitch":-35,"heading":0,"terrainExaggeration":1.2}'::jsonb,
    updated_at = NOW()
WHERE region_id = 'R2';
