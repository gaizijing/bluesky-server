-- 先添加默认飞行器模型（aircraft-1）
INSERT INTO public.aircraft_models (id, model_name, category, manufacturer, max_altitude, max_speed, cruise_speed, max_range, max_endurance, max_payload, description, is_active, created_at, updated_at)
VALUES (
    'aircraft-1', 
    '默认无人机', 
    '多旋翼', 
    '通用厂商', 
    6000, 
    25.00, 
    15.00, 
    12000.00, 
    60, 
    3.00, 
    '默认通用无人机', 
    true, 
    NOW(), 
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- 添加宁河区专用飞行器模型
INSERT INTO public.aircraft_models (id, model_name, category, manufacturer, max_altitude, max_speed, cruise_speed, max_range, max_endurance, max_payload, description, is_active, created_at, updated_at)
VALUES (
    'aircraft-ninghe', 
    '宁河巡检无人机', 
    '多旋翼', 
    '本地厂商', 
    5000, 
    20.00, 
    12.00, 
    10000.00, 
    45, 
    2.00, 
    '宁河区专用巡检机型', 
    true, 
    NOW(), 
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- 添加默认阈值配置（aircraft-1）
INSERT INTO public.aircraft_limits (id, aircraft_id, max_wind_speed, max_wind_shear, min_visibility, max_precipitation, min_cloud_base, temp_min, temp_max, max_humidity, max_turbulence_level, created_at, updated_at)
VALUES (
    7, 
    'aircraft-1', 
    12.00, 
    5.00, 
    1.50, 
    5.00, 
    300, 
    -20.00, 
    40.00, 
    90, 
    'medium', 
    NOW(), 
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- 添加宁河区专用阈值配置
INSERT INTO public.aircraft_limits (id, aircraft_id, max_wind_speed, max_wind_shear, min_visibility, max_precipitation, min_cloud_base, temp_min, temp_max, max_humidity, max_turbulence_level, created_at, updated_at)
VALUES (
    8, 
    'aircraft-ninghe', 
    10.00, 
    4.00, 
    2.00, 
    3.00, 
    250, 
    -15.00, 
    35.00, 
    85, 
    'low', 
    NOW(), 
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- 更新序列值
SELECT pg_catalog.setval('public.aircraft_limits_id_seq', 8, true);
