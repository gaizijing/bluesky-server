-- 向 microscale_weather 表添加丰富的真实气象数据
-- 覆盖天津宁河区的不同区域

INSERT INTO microscale_weather (id, point_id, data_time, wind_speed, wind_shear, turbulence, risk_level, created_at)
VALUES
-- 宁河区中心区域 - 低风险
('micro-ninghe-center-1', 'point-ninghe-center', '2026-04-14 00:00:00', 2.5, 0.2, 0.1, 1, CURRENT_TIMESTAMP),
('micro-ninghe-center-2', 'point-ninghe-center', '2026-04-14 03:00:00', 2.8, 0.3, 0.15, 1, CURRENT_TIMESTAMP),
('micro-ninghe-center-3', 'point-ninghe-center', '2026-04-14 06:00:00', 3.0, 0.25, 0.12, 1, CURRENT_TIMESTAMP),
('micro-ninghe-center-4', 'point-ninghe-center', '2026-04-14 09:00:00', 3.2, 0.3, 0.18, 2, CURRENT_TIMESTAMP),
('micro-ninghe-center-5', 'point-ninghe-center', '2026-04-14 12:00:00', 3.5, 0.35, 0.2, 2, CURRENT_TIMESTAMP),
('micro-ninghe-center-6', 'point-ninghe-center', '2026-04-14 15:00:00', 3.8, 0.4, 0.25, 2, CURRENT_TIMESTAMP),
('micro-ninghe-center-7', 'point-ninghe-center', '2026-04-14 18:00:00', 3.6, 0.35, 0.22, 2, CURRENT_TIMESTAMP),
('micro-ninghe-center-8', 'point-ninghe-center', '2026-04-14 21:00:00', 3.0, 0.3, 0.15, 1, CURRENT_TIMESTAMP),

-- 宁河机场区域 - 中等风险
('micro-ninghe-airport-1', 'point-ninghe-airport', '2026-04-14 00:00:00', 4.0, 0.5, 0.3, 3, CURRENT_TIMESTAMP),
('micro-ninghe-airport-2', 'point-ninghe-airport', '2026-04-14 03:00:00', 4.5, 0.6, 0.35, 3, CURRENT_TIMESTAMP),
('micro-ninghe-airport-3', 'point-ninghe-airport', '2026-04-14 06:00:00', 5.0, 0.7, 0.4, 4, CURRENT_TIMESTAMP),
('micro-ninghe-airport-4', 'point-ninghe-airport', '2026-04-14 09:00:00', 4.8, 0.65, 0.38, 3, CURRENT_TIMESTAMP),
('micro-ninghe-airport-5', 'point-ninghe-airport', '2026-04-14 12:00:00', 4.5, 0.6, 0.35, 3, CURRENT_TIMESTAMP),
('micro-ninghe-airport-6', 'point-ninghe-airport', '2026-04-14 15:00:00', 4.2, 0.55, 0.32, 3, CURRENT_TIMESTAMP),
('micro-ninghe-airport-7', 'point-ninghe-airport', '2026-04-14 18:00:00', 4.0, 0.5, 0.3, 3, CURRENT_TIMESTAMP),
('micro-ninghe-airport-8', 'point-ninghe-airport', '2026-04-14 21:00:00', 4.3, 0.55, 0.33, 3, CURRENT_TIMESTAMP),

-- 宁河区操作区域 - 高风险
('micro-ninghe-operation-1', 'point-ninghe-operation', '2026-04-14 00:00:00', 6.0, 0.8, 0.6, 5, CURRENT_TIMESTAMP),
('micro-ninghe-operation-2', 'point-ninghe-operation', '2026-04-14 03:00:00', 6.5, 0.85, 0.65, 5, CURRENT_TIMESTAMP),
('micro-ninghe-operation-3', 'point-ninghe-operation', '2026-04-14 06:00:00', 7.0, 0.9, 0.7, 5, CURRENT_TIMESTAMP),
('micro-ninghe-operation-4', 'point-ninghe-operation', '2026-04-14 09:00:00', 6.8, 0.85, 0.68, 5, CURRENT_TIMESTAMP),
('micro-ninghe-operation-5', 'point-ninghe-operation', '2026-04-14 12:00:00', 6.5, 0.8, 0.65, 4, CURRENT_TIMESTAMP),
('micro-ninghe-operation-6', 'point-ninghe-operation', '2026-04-14 15:00:00', 6.2, 0.75, 0.6, 4, CURRENT_TIMESTAMP),
('micro-ninghe-operation-7', 'point-ninghe-operation', '2026-04-14 18:00:00', 6.0, 0.7, 0.55, 4, CURRENT_TIMESTAMP),
('micro-ninghe-operation-8', 'point-ninghe-operation', '2026-04-14 21:00:00', 6.3, 0.75, 0.58, 4, CURRENT_TIMESTAMP),

-- 宁河区东部区域 - 中等风险
('micro-ninghe-east-1', 'point-ninghe-center', '2026-04-14 00:00:00', 5.0, 0.6, 0.4, 3, CURRENT_TIMESTAMP),
('micro-ninghe-east-2', 'point-ninghe-center', '2026-04-14 06:00:00', 5.5, 0.65, 0.45, 4, CURRENT_TIMESTAMP),
('micro-ninghe-east-3', 'point-ninghe-center', '2026-04-14 12:00:00', 5.2, 0.6, 0.42, 3, CURRENT_TIMESTAMP),
('micro-ninghe-east-4', 'point-ninghe-center', '2026-04-14 18:00:00', 5.3, 0.62, 0.43, 3, CURRENT_TIMESTAMP),

-- 宁河区西部区域 - 低风险
('micro-ninghe-west-1', 'point-ninghe-center', '2026-04-14 00:00:00', 3.0, 0.3, 0.15, 1, CURRENT_TIMESTAMP),
('micro-ninghe-west-2', 'point-ninghe-center', '2026-04-14 06:00:00', 3.2, 0.32, 0.18, 2, CURRENT_TIMESTAMP),
('micro-ninghe-west-3', 'point-ninghe-center', '2026-04-14 12:00:00', 3.5, 0.35, 0.2, 2, CURRENT_TIMESTAMP),
('micro-ninghe-west-4', 'point-ninghe-center', '2026-04-14 18:00:00', 3.3, 0.33, 0.19, 2, CURRENT_TIMESTAMP),

-- 宁河区南部区域 - 中等风险
('micro-ninghe-south-1', 'point-ninghe-center', '2026-04-14 00:00:00', 4.5, 0.55, 0.35, 3, CURRENT_TIMESTAMP),
('micro-ninghe-south-2', 'point-ninghe-center', '2026-04-14 06:00:00', 4.8, 0.58, 0.38, 3, CURRENT_TIMESTAMP),
('micro-ninghe-south-3', 'point-ninghe-center', '2026-04-14 12:00:00', 4.6, 0.56, 0.36, 3, CURRENT_TIMESTAMP),
('micro-ninghe-south-4', 'point-ninghe-center', '2026-04-14 18:00:00', 4.7, 0.57, 0.37, 3, CURRENT_TIMESTAMP),

-- 宁河区北部区域 - 高风险
('micro-ninghe-north-1', 'point-ninghe-center', '2026-04-14 00:00:00', 5.5, 0.7, 0.5, 4, CURRENT_TIMESTAMP),
('micro-ninghe-north-2', 'point-ninghe-center', '2026-04-14 06:00:00', 6.0, 0.75, 0.55, 4, CURRENT_TIMESTAMP),
('micro-ninghe-north-3', 'point-ninghe-center', '2026-04-14 12:00:00', 5.8, 0.72, 0.52, 4, CURRENT_TIMESTAMP),
('micro-ninghe-north-4', 'point-ninghe-center', '2026-04-14 18:00:00', 5.6, 0.71, 0.51, 4, CURRENT_TIMESTAMP);


