-- MetViz 全要素格点种子：temperature / wind / visibility / precip / humidity / cloud / pressure
-- 64×64 · 5 高度层 · R1 + R2

DELETE FROM weather_grid_cache
WHERE grid_json::jsonb @> '{"source":"v21-seed"}'::jsonb;

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
                              - h.height_m * 0.002
                        WHEN 'wind' THEN
                            3 + 9 * abs(sin(rrow.row * 0.09 + c.col * 0.07))
                              + 3 * cos(rrow.row * 0.04 - c.col * 0.05)
                              + h.height_m * 0.002
                        WHEN 'visibility' THEN
                            8000 + 6000 * sin(rrow.row * 0.08 + c.col * 0.06)
                              + 2000 * cos(c.col * 0.09 - rrow.row * 0.05)
                              - h.height_m * 0.8
                        WHEN 'precip' THEN
                            GREATEST(
                                0,
                                2 + 8 * abs(sin(rrow.row * 0.13 + c.col * 0.1))
                                  - h.height_m * 0.001
                            )
                        WHEN 'humidity' THEN
                            55 + 30 * sin(rrow.row * 0.1 + c.col * 0.07 + h.height_m * 0.0003)
                              + 10 * cos(c.col * 0.08 - rrow.row * 0.04)
                              - h.height_m * 0.004
                        WHEN 'cloud' THEN
                            30 + 50 * abs(sin(rrow.row * 0.09 + c.col * 0.11))
                              + 15 * cos(rrow.row * 0.05 - c.col * 0.06)
                        WHEN 'pressure' THEN
                            1013 - h.height_m * 0.012
                              + 8 * sin(rrow.row * 0.07 + c.col * 0.05)
                        ELSE 0
                    END::numeric,
                    2
                )
            )
            ORDER BY rrow.row, c.col
        ) AS cells
    FROM bucket b
    CROSS JOIN (VALUES
        ('R1', 117.4::float8, 118.1::float8, 39.1::float8, 39.5::float8),
        ('R2', 120.0::float8, 121.0::float8, 36.0::float8, 37.0::float8)
    ) AS r(region_id, west, east, south, north)
    CROSS JOIN generate_series(0, 63) AS rrow(row)
    CROSS JOIN generate_series(0, 63) AS c(col)
    CROSS JOIN (VALUES (100), (300), (500), (1000), (2000)) AS h(height_m)
    CROSS JOIN (VALUES
        ('temperature'),
        ('wind'),
        ('visibility'),
        ('precip'),
        ('humidity'),
        ('cloud'),
        ('pressure')
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
        'source', 'v21-seed',
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
