-- 边界统一使用 boundary_url GeoJSON，移除矩形 bounds 字段

ALTER TABLE region
    DROP COLUMN IF EXISTS west,
    DROP COLUMN IF EXISTS east,
    DROP COLUMN IF EXISTS south,
    DROP COLUMN IF EXISTS north;
