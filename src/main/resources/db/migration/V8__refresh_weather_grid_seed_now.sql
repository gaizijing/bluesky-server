-- 再次刷新 MetViz 格点种子到当前 15 分钟桶（重启后端时 Flyway 执行）
-- 修复：调度写入的和风空值行会挡住旧种子；本迁移保证当前桶有 64×64 有效数据

DELETE FROM weather_grid_cache
WHERE grid_json::jsonb @> '{"source":"v8-seed"}'::jsonb;

-- 清理调度写入的全 null 和风失败行（会挡住有效种子）
DELETE FROM weather_grid_cache w
WHERE w.region_id = 'R1'
  AND w.product IN ('temperature', 'wind')
  AND NOT EXISTS (
    SELECT 1
    FROM jsonb_array_elements(w.grid_json::jsonb -> 'cells') AS c(elem)
    WHERE (c.elem ->> 'value') IS NOT NULL
  );

WITH bucket AS (
    SELECT (
        date_trunc('minute', timezone('Asia/Shanghai', CURRENT_TIMESTAMP))
        - make_interval(
            mins => (EXTRACT(MINUTE FROM timezone('Asia/Shanghai', CURRENT_TIMESTAMP))::int % 15)
        )
    )::timestamp AS t
),
cell_grid AS (
    SELECT
        r.region_id,
        b.t AS bucket_time,
        h.height_m,
        p.product,
        r.west,
        r.east,
        r.south,
        r.north,
        jsonb_agg(
            jsonb_build_object(
                'lng', r.west + (r.east - r.west) * c.col / 63.0,
                'lat', r.south + (r.north - r.south) * rrow.row / 63.0,
                'value', ROUND(
                    CASE p.product
                        WHEN 'temperature' THEN
                            16 + 12 * sin(rrow.row * 0.12 + c.col * 0.09 + h.height_m * 0.0008)
                              + 6 * cos(c.col * 0.11 - rrow.row * 0.07)
                        WHEN 'wind' THEN
                            2 + 10 * abs(sin(rrow.row * 0.1 + c.col * 0.08))
                              + 4 * cos(rrow.row * 0.05 - c.col * 0.06)
                        ELSE 0
                    END::numeric,
                    2
                )
            )
            ORDER BY rrow.row, c.col
        ) AS cells
    FROM bucket b
    CROSS JOIN (VALUES
        ('R1', 117.4::float8, 118.1::float8, 39.1::float8, 39.5::float8)
    ) AS r(region_id, west, east, south, north)
    CROSS JOIN generate_series(0, 63) AS rrow(row)
    CROSS JOIN generate_series(0, 63) AS c(col)
    CROSS JOIN (VALUES (100), (300), (500), (1000), (2000)) AS h(height_m)
    CROSS JOIN (VALUES ('temperature'), ('wind')) AS p(product)
    GROUP BY r.region_id, b.t, h.height_m, p.product, r.west, r.east, r.south, r.north
)
INSERT INTO weather_grid_cache (
    region_id, bucket_time, height_m, product, grid_json, data_source_time, computed_at, expires_at
)
SELECT
    region_id,
    bucket_time,
    height_m,
    product,
    jsonb_build_object(
        'source', 'v8-seed',
        'west', west,
        'east', east,
        'south', south,
        'north', north,
        'width', 64,
        'height', 64,
        'product', product,
        'cells', cells
    ),
    bucket_time,
    NOW(),
    NOW() + interval '7 days'
FROM cell_grid;
