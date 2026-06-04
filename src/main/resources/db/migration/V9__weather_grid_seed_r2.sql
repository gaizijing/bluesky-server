-- R2 青岛：MetViz 格点种子（与 V8/R1 同策略，64×64 temperature + wind）
-- 解决 /weather/grid-field regionId=R2 返回 cacheMiss、grid=[]

DELETE FROM weather_grid_cache
WHERE grid_json::jsonb @> '{"source":"v9-seed"}'::jsonb;

DELETE FROM weather_grid_cache w
WHERE w.region_id = 'R2'
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
                            18 + 10 * sin(rrow.row * 0.11 + c.col * 0.08 + h.height_m * 0.0007)
                              + 5 * cos(c.col * 0.1 - rrow.row * 0.06)
                        WHEN 'wind' THEN
                            3 + 9 * abs(sin(rrow.row * 0.09 + c.col * 0.07))
                              + 3 * cos(rrow.row * 0.04 - c.col * 0.05)
                        ELSE 0
                    END::numeric,
                    2
                )
            )
            ORDER BY rrow.row, c.col
        ) AS cells
    FROM bucket b
    CROSS JOIN (VALUES
        ('R2', 120.0::float8, 121.0::float8, 36.0::float8, 37.0::float8)
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
        'source', 'v9-seed',
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
