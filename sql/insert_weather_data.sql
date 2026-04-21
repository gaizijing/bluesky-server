-- 基于监测点生成微尺度天气演示数据（LS001~LS004, NH001~NH004）
-- 说明：
-- 1) 每个监测点生成 100x100 网格，共 80,000 条
-- 2) 使用 point_type 调整风险形态（operation/takeoff/apron）
-- 3) 风场和风险归一化按“每个点独立”处理，避免所有点看起来一样

TRUNCATE TABLE public.microscale_weather RESTART IDENTITY;

WITH target AS (
  SELECT
    mp.id::text AS point_id,
    COALESCE(mp.type, 'operation')::text AS point_type
  FROM public.monitoring_points mp
  WHERE mp.id IN ('LS001', 'LS002', 'LS003', 'LS004', 'NH001', 'NH002', 'NH003', 'NH004')
),

params AS (
  SELECT
    point_id,
    point_type,
    radians(20 + random() * 140) AS phi,
    0.22 + random() * 0.16 AS c1x,
    0.24 + random() * 0.18 AS c1y,
    0.62 + random() * 0.18 AS c2x,
    0.48 + random() * 0.22 AS c2y,

    CASE point_type
      WHEN 'operation' THEN 1.15
      WHEN 'takeoff'   THEN 1.08
      WHEN 'apron'     THEN 0.95
      ELSE 1.00
    END AS risk_gain,

    CASE point_type
      WHEN 'operation' THEN 1.10
      WHEN 'takeoff'   THEN 1.04
      WHEN 'apron'     THEN 0.92
      ELSE 1.00
    END AS gust_gain
  FROM target
),

grid AS (
  SELECT
    t.point_id,
    t.point_type,
    gx AS grid_x,
    gy AS grid_y,
    gx / 99.0 AS u,
    gy / 99.0 AS v
  FROM target t
  CROSS JOIN generate_series(0, 99) gx
  CROSS JOIN generate_series(0, 99) gy
),

field AS (
  SELECT
    g.*,
    p.phi, p.c1x, p.c1y, p.c2x, p.c2y,
    p.risk_gain, p.gust_gain,

    -- 双核
    exp(-(power(g.u - p.c1x, 2) + power(g.v - p.c1y, 2)) / 0.010) AS cell1,
    exp(-(power(g.u - p.c2x, 2) + power(g.v - p.c2y, 2)) / 0.018) AS cell2,

    -- 风带
    exp(
      -power((g.u - 0.5) * sin(p.phi) - (g.v - 0.5) * cos(p.phi), 2)
      / 0.0028
    ) AS line_band,

    ((g.u - 0.5) * cos(p.phi) + (g.v - 0.5) * sin(p.phi)) AS line_axis

  FROM grid g
  JOIN params p USING (point_id, point_type)
),

met AS (
  SELECT
    point_id,
    grid_x,
    grid_y,

    GREATEST(0.8, LEAST(14.0,
      (
        3.8
        + 3.8 * line_band
        + 2.2 * cell1
        + 1.5 * cell2
        + 1.1 * sin(2 * pi() * (u * 0.9 + v * 0.35))
        + (random() - 0.5) * 0.6
      ) * gust_gain
    )) AS wind_speed,

    GREATEST(0.03, LEAST(0.70,
      (
        0.06
        + 0.26 * line_band * (0.4 + abs(sin(8 * line_axis)))
        + 0.10 * cell2
        + (random() - 0.5) * 0.03
      ) * (0.92 + 0.18 * risk_gain)
    )) AS wind_shear,

    GREATEST(0.05, LEAST(1.30,
      (
        0.12
        + 0.34 * line_band
        + 0.22 * cell1
        + 0.12 * abs(sin(9 * u) * cos(7 * v))
        + (random() - 0.5) * 0.05
      ) * (0.90 + 0.20 * risk_gain)
    )) AS turbulence,

    risk_gain

  FROM field
),

risk_raw AS (
  SELECT
    *,
    risk_gain * (
      0.52 * (wind_speed / 14.0)
      + 0.28 * (wind_shear / 0.70)
      + 0.20 * (turbulence / 1.30)
    ) AS r
  FROM met
),

stats AS (
  SELECT point_id, min(r) AS rmin, max(r) AS rmax
  FROM risk_raw
  GROUP BY point_id
)

INSERT INTO public.microscale_weather
(point_id, data_time, grid_size, grid_x, grid_y,
 risk_level, wind_speed, wind_shear, turbulence, created_at)

SELECT
  rr.point_id,
  date_trunc('minute', now()),
  100,
  rr.grid_x,
  rr.grid_y,

  ROUND(
    GREATEST(
      8.0,
      LEAST(92.0,
        8.0 + 84.0 * (rr.r - s.rmin) / NULLIF(s.rmax - s.rmin, 0)
      )
    )
  )::int,

  ROUND(rr.wind_speed::numeric, 2),
  ROUND(rr.wind_shear::numeric, 2),
  ROUND(rr.turbulence::numeric, 2),
  now()

FROM risk_raw rr
JOIN stats s USING (point_id);
