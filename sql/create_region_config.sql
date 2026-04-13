-- 创建地区配置表
CREATE TABLE IF NOT EXISTS public.region_config (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    west DOUBLE PRECISION NOT NULL,
    east DOUBLE PRECISION NOT NULL,
    south DOUBLE PRECISION NOT NULL,
    north DOUBLE PRECISION NOT NULL,
    is_default BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

-- 添加默认地区配置（天津宁河区）
INSERT INTO public.region_config (name, west, east, south, north, is_default, created_at, updated_at)
VALUES (
    '天津宁河区',
    117.4,
    118.1,
    39.1,
    39.5,
    true,
    NOW(),
    NOW()
) ON CONFLICT DO NOTHING;

-- 添加青岛地区配置
INSERT INTO public.region_config (name, west, east, south, north, is_default, created_at, updated_at)
VALUES (
    '青岛',
    120.0,
    121.0,
    36.0,
    37.0,
    false,
    NOW(),
    NOW()
) ON CONFLICT DO NOTHING;

-- 更新序列值
SELECT pg_catalog.setval('public.region_config_id_seq', 2, true);
