-- 向aircraft_models表添加默认飞行器记录
INSERT INTO public.aircraft_models (id, model_name, category, manufacturer, max_altitude, max_speed, cruise_speed, max_range, max_endurance, max_payload, description, is_active, created_at, updated_at)
VALUES (
    'default', 
    '默认飞行器', 
    '多旋翼', 
    '通用厂商', 
    6000, 
    25.00, 
    15.00, 
    12000.00, 
    60, 
    3.00, 
    '默认通用飞行器', 
    true, 
    NOW(), 
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- 向aircraft_limits表添加默认配置记录
INSERT INTO public.aircraft_limits (id, aircraft_id, max_wind_speed, max_wind_shear, min_visibility, max_precipitation, min_cloud_base, temp_min, temp_max, max_humidity, max_turbulence_level, created_at, updated_at)
VALUES (
    9, 
    'default', 
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

-- 更新序列值
SELECT pg_catalog.setval('public.aircraft_limits_id_seq', 9, true);
