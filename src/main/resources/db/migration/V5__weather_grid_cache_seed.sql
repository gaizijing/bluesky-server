-- MetViz 气象填色：weather_grid_cache 种子（/weather/grid-field 数据源）
-- bucket 对齐 15 分钟档（与 TimeBucketUtil 一致）

DELETE FROM weather_grid_cache
WHERE grid_json::jsonb @> '{"source":"v5-seed"}'::jsonb;

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
                'lng', r.west + (r.east - r.west) * c.col / 9.0,
                'lat', r.south + (r.north - r.south) * rrow.row / 9.0,
                'value', ROUND(
                    CASE p.product
                        WHEN 'temperature' THEN
                            18 + 10 * sin(rrow.row * 0.7 + c.col * 0.5 + h.height_m * 0.001)
                        WHEN 'wind' THEN
                            3 + 8 * abs(sin(rrow.row * 0.6 + c.col * 0.4))
                        WHEN 'visibility' THEN
                            8 + 10 * cos(rrow.row * 0.5 - c.col * 0.3)
                        WHEN 'precip' THEN
                            GREATEST(0, 2 + 4 * sin(rrow.row + c.col * 0.8))
                        WHEN 'humidity' THEN
                            55 + 30 * sin(rrow.row * 0.4 + c.col * 0.35)
                        WHEN 'cloud' THEN
                            30 + 50 * abs(cos(rrow.row * 0.55 + c.col * 0.45))
                        WHEN 'pressure' THEN
                            1005 + 8 * sin(rrow.row * 0.3 - c.col * 0.25)
                        ELSE
                            20 + 15 * sin(rrow.row * 0.5 + c.col * 0.5)
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
    CROSS JOIN generate_series(0, 9) AS rrow(row)
    CROSS JOIN generate_series(0, 9) AS c(col)
    CROSS JOIN (VALUES (100), (300), (500), (1000), (2000)) AS h(height_m)
    CROSS JOIN (
        VALUES ('temperature'), ('wind'), ('visibility'), ('precip'), ('humidity'), ('cloud'), ('pressure')
    ) AS p(product)
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
        'source', 'v5-seed',
        'west', west,
        'east', east,
        'south', south,
        'north', north,
        'product', product,
        'cells', cells
    ),
    bucket_time,
    NOW(),
    NOW() + interval '7 days'
FROM cell_grid;
